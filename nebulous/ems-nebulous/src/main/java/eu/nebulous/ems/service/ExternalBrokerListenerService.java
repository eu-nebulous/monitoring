/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.controller.ControlServiceRequestInfo;
import gr.iccs.imu.ems.control.controller.NodeRegistrationCoordinator;
import gr.iccs.imu.ems.control.plugin.PostTranslationPlugin;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

@Slf4j
@Service
public class ExternalBrokerListenerService extends AbstractExternalBrokerService
		implements PostTranslationPlugin, InitializingBean
{
	private final NebulousEmsTranslatorProperties translatorProperties;
	private final ApplicationContext applicationContext;
	private final ArrayBlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(100);
	private final ObjectMapper objectMapper;
	private List<Consumer> consumers;
	private Publisher commandsResponsePublisher;
	private Publisher modelsResponsePublisher;
	private String applicationId = System.getenv("APPLICATION_ID");

	record Command(String key, String address, Map body, Message message, Context context) {
	}

	public ExternalBrokerListenerService(ApplicationContext applicationContext,
										 ExternalBrokerServiceProperties properties,
                                         TaskScheduler taskScheduler,
										 ObjectMapper objectMapper,
										 NebulousEmsTranslatorProperties translatorProperties)
	{
		super(properties, taskScheduler);
		this.applicationContext = applicationContext;
		this.objectMapper = objectMapper;
        this.translatorProperties = translatorProperties;
    }

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!properties.isEnabled()) {
			log.info("ExternalBrokerListenerService: Disabled due to configuration");
			return;
		}
		log.info("ExternalBrokerListenerService: Application Id (from Env.): {}", applicationId);
		if (checkProperties()) {
			initializeConsumers();
			initializePublishers();
			startCommandProcessor();
			connectToBroker(List.of(commandsResponsePublisher, modelsResponsePublisher), consumers);
			log.info("ExternalBrokerListenerService: Initialized listeners and publishers");
		} else {
			log.warn("ExternalBrokerListenerService: Not configured or misconfigured. Will not initialize");
		}
	}

	@Override
	public void processTranslationResults(TranslationContext translationContext, TopicBeacon topicBeacon) {
		if (!properties.isEnabled()) {
			log.info("ExternalBrokerListenerService: Disabled due to configuration");
			return;
		}

		this.applicationId = translationContext.getAppId();
		log.info("ExternalBrokerListenerService: Set applicationId to: {}", applicationId);

		// Call control-service to deploy EMS clients
		if (properties.isDeployEmsClientsOnKubernetesEnabled()) {
			try {
				log.info("ExternalBrokerListenerService: Start deploying EMS clients...");
				String id = "dummy-" + System.currentTimeMillis();
				Map<String, Object> nodeInfo = new HashMap<>(Map.of(
						"id", id,
						"name", id,
						"type", "K8S",
						"provider", "Kubernetes",
						"zone-id", ""
				));
				applicationContext.getBean(NodeRegistrationCoordinator.class)
						.registerNode("", nodeInfo, translationContext);
				log.debug("ExternalBrokerListenerService: EMS clients deployment started");
			} catch (Exception e) {
				log.warn("ExternalBrokerListenerService: EXCEPTION while starting EMS client deployment: ", e);
			}
		} else
			log.info("ExternalBrokerListenerService: EMS clients deployment is disabled");
    }

	private void initializeConsumers() {
		/*if (StringUtils.isBlank(applicationId)) {
			log.warn("ExternalBrokerListenerService: Call to initializeConsumers with blank applicationId. Will not change anything.");
			return;
		}*/

		// Create message handler
		Handler messageHandler = new Handler() {
			@Override
			public void onMessage(String key, String address, Map body, Message message, Context context) {
				try {
					log.info("ExternalBrokerListenerService: messageHandler: Got new message: key={}, address={}, body={}, message={}",
							key, address, body, message);
					super.onMessage(key, address, body, message, context);
					commandQueue.add(new Command(key, address, body, message, context));
				} catch (IllegalStateException e) {
					log.warn("ExternalBrokerListenerService: Commands queue is full. Dropping command: queue-size={}", commandQueue.size());
				} catch (Exception e) {
					log.warn("ExternalBrokerListenerService: Error while processing message: ", e);
				}
			}
		};

		// Create consumers for each topic of interest
		consumers = List.of(
				new Consumer(properties.getCommandsTopic(), properties.getCommandsTopic(), messageHandler, null, true, true),
				new Consumer(properties.getModelsTopic(), properties.getModelsTopic(), messageHandler, null, true, true)
		);
		log.info("ExternalBrokerListenerService: created subscribers");
	}

	private void initializePublishers() {
		commandsResponsePublisher = new Publisher(properties.getCommandsResponseTopic(), properties.getCommandsResponseTopic(), true, true);
		modelsResponsePublisher = new Publisher(properties.getModelsResponseTopic(), properties.getModelsResponseTopic(), true, true);
		log.info("ExternalBrokerListenerService: created publishers");
	}

	private void startCommandProcessor() {
		taskScheduler.schedule(()->{
			while (true) {
				try {
					Command command = commandQueue.take();
					processMessage(command);
				} catch (InterruptedException e) {
                    log.warn("ExternalBrokerListenerService: Command processor interrupted. Exiting process loop");
					break;
                } catch (Exception e) {
					log.warn("ExternalBrokerListenerService: Exception while processing command: {}\n", commandQueue, e);
				}
            }
		}, Instant.now());
	}

	private void processMessage(Command command) throws ClientException, IOException {
		log.debug("ExternalBrokerListenerService: Command: {}", command);
		log.debug("ExternalBrokerListenerService: Command: message: {}", command.message);
		log.debug("ExternalBrokerListenerService: Command: body: {}", command.message.body());
		command.message.forEachProperty((s, o) ->
				log.debug("ExternalBrokerListenerService: Command: --- property: {} = {}", s, o));
		if (properties.getCommandsTopic().equals(command.address)) {
			// Process command
			log.info("ExternalBrokerListenerService: Received a command from external broker: {}", command.body);
			processCommandMessage(command);
		} else
		if (properties.getModelsTopic().equals(command.address)) {
			// Process metric model message
			log.info("ExternalBrokerListenerService: Received a new Metric Model message from external broker: {}", command.body);
			processMetricModelMessage(command);
		}
	}

	private void processCommandMessage(Command command) throws ClientException {
		// Get application id
		String appId = getAppId(command, commandsResponsePublisher);
		if (appId == null) return;

		// Get command string
		String commandStr = command.body.getOrDefault("command", "").toString();
		log.debug("ExternalBrokerListenerService: Command: {}", commandStr);

		sendResponse(commandsResponsePublisher, appId, "ERROR: ---NOT YET IMPLEMENTED---: "+ command.body);
	}

	private void processMetricModelMessage(Command command) throws ClientException, IOException {
		// Get application id
		String appId = getAppId(command, modelsResponsePublisher);
		if (appId == null) return;

		// Get model string and/or model file
		Object modelObj = command.body.getOrDefault("model", null);
		if (modelObj==null) {
			modelObj = command.body.getOrDefault("yaml", null);
		}
		if (modelObj==null) {
			modelObj = command.body.getOrDefault("body", null);
		}
		String modelFile = command.body.getOrDefault("model-path", "").toString();

		// Check if 'model' or 'model-path' is provided
		if (modelObj==null && StringUtils.isBlank(modelFile)) {
			log.warn("ExternalBrokerListenerService: No model found in Metric Model message: {}", command.body);
			sendResponse(modelsResponsePublisher, appId, "ERROR: No model found in Metric Model message: "+ command.body);
			return;
		}

		String modelStr = null;
		if (modelObj!=null) {
			log.debug("ExternalBrokerListenerService: modelObj: class={}, object={}", modelObj.getClass(), modelObj);
			modelStr = (modelObj instanceof String) ? (String) modelObj : modelObj.toString();
			if (modelObj instanceof String) {
				modelStr = (String) modelObj;
			}
			if (modelObj instanceof Map) {
				modelStr = objectMapper.writeValueAsString(modelObj);
			}
		}

		processMetricModel(appId, modelStr, modelFile);
	}

	void processMetricModel(String appId, String modelStr, String modelFile) throws IOException {
		// If 'model' string is provided, store it in a file
		if (StringUtils.isNotBlank(modelStr)) {
			modelFile = StringUtils.isBlank(modelFile) ? getModelFile(appId) : modelFile;
			storeModel(modelFile, modelStr);
		} else if (StringUtils.isNotBlank(modelStr)) {
			log.warn("ExternalBrokerListenerService: Parameter 'modelStr' is blank. Trying modelFile: {}", modelFile);
		} else {
			log.warn("ExternalBrokerListenerService: Parameters 'modelStr' and 'modelFile' are both blank");
			throw new IllegalArgumentException("Parameters 'modelStr' and 'modelFile' are both blank");
		}

		// Call control-service to process model, also pass a callback to get the result
		applicationContext.getBean(ControlServiceCoordinator.class).processAppModel(modelFile, null,
				ControlServiceRequestInfo.create(appId, null, null, null,
						(result) -> {
							// Send message with the processing result
							log.info("ExternalBrokerListenerService: Metric model processing result: {}", result);
							sendResponse(modelsResponsePublisher, appId, result);
						}));
	}

	private String getAppId(Command command, Publisher publisher) throws ClientException {
		// Check if 'applicationId' is provided in message properties
		Object propApp = command.message.property(properties.getApplicationIdPropertyName());
		String appId = propApp != null ? propApp.toString() : null;
		if (StringUtils.isNotBlank(appId)) {
			log.debug("ExternalBrokerListenerService: Application Id found in message properties: {}", appId);
			return appId;
		}
		log.debug("ExternalBrokerListenerService: No Application Id found in message properties: {}", command.body);

		// Check if 'applicationId' is provided in message body
		appId = command.body.getOrDefault("application", "").toString();
		if (StringUtils.isNotBlank(appId)) {
			log.debug("ExternalBrokerListenerService: Application Id found in message body: {}", appId);
			return appId;
		}
		log.debug("ExternalBrokerListenerService: No Application Id found in message body: {}", command.body);

		// Not found 'applicationId'
		log.warn("ExternalBrokerListenerService: No Application Id found in message: {}", command.body);
		sendResponse(modelsResponsePublisher, null, "ERROR: No Application Id found in message: "+ command.body);

		return null;
	}

	private String getModelFile(String appId) {
		return String.format("model-%s--%d.yml", appId, System.currentTimeMillis());
	}

	private void storeModel(String fileName, String modelStr) throws IOException {
		Path path = Paths.get(translatorProperties.getModelsDir(), fileName);
		Files.writeString(path, modelStr);
		log.info("ExternalBrokerListenerService: Stored metric model in file: {}", path);
	}

	private void sendResponse(Publisher publisher, String appId, Object response) {
		publisher.send(Map.of(
				"response", response
		), appId);
	}
}