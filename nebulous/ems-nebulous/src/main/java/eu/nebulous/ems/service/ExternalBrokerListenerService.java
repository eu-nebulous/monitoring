/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import eu.nebulous.ems.EmsNebulousProperties;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

@Slf4j
@Service
public class ExternalBrokerListenerService extends AbstractExternalBrokerService implements InitializingBean {
	private final ArrayBlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(100);
    private final EmsNebulousProperties emsNebulousProperties;
    private final MvvService mvvService;
	private List<Consumer> consumers;
	private Publisher commandsResponsePublisher;
	private String applicationId;

	record Command(String key, String address, Map body, Message message, Context context) {
	}

	public ExternalBrokerListenerService(ExternalBrokerServiceProperties properties,
										 EmsNebulousProperties emsNebulousProperties,
                                         TaskScheduler taskScheduler,
										 MvvService mvvService)
	{
		super(properties, taskScheduler);
		this.emsNebulousProperties = emsNebulousProperties;
        this.mvvService = mvvService;
    }

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!properties.isEnabled()) {
			log.info("ExternalBrokerListenerService: Disabled due to configuration");
			return;
		}

		applicationId = emsNebulousProperties.getApplicationId();
		log.info("ExternalBrokerListenerService: Application Id: {}", applicationId);
		if (StringUtils.isBlank(applicationId))
			log.warn("ExternalBrokerListenerService: APPLICATION_ID env. var. is missing");
			//throw new IllegalArgumentException("APPLICATION_UID not provided as an env. var");

		if (checkProperties()) {
			initializeConsumers();
			initializePublishers();
			startCommandProcessor();
			connectToBroker(List.of(commandsResponsePublisher), consumers);
			log.info("ExternalBrokerListenerService: Initialized listeners and publishers");
		} else {
			log.warn("ExternalBrokerListenerService: Not configured or misconfigured. Will not initialize");
		}
	}

	private void initializeConsumers() {
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
				new Consumer(properties.getSolutionsTopic(), properties.getSolutionsTopic(), messageHandler, applicationId, true, true)
		);
		log.info("ExternalBrokerListenerService: created subscribers");
	}

	private void initializePublishers() {
		commandsResponsePublisher = new Publisher(properties.getCommandsResponseTopic(), properties.getCommandsResponseTopic(), true, true);
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

	private void processMessage(Command command) throws ClientException {
		log.debug("ExternalBrokerListenerService: Command: {}", command);
		log.debug("ExternalBrokerListenerService: Command: message: {}", command.message);
		log.debug("ExternalBrokerListenerService: Command: body: {}", command.message.body());
		command.message.forEachProperty((s, o) ->
				log.debug("ExternalBrokerListenerService: Command: --- property: {} = {}", s, o));
		/*if (properties.getCommandsTopic().equals(command.address)) {
			// Process command
			log.info("ExternalBrokerListenerService: Received a command from external broker: {}", command.body);
			processCommandMessage(command);
		} else*/
		if (properties.getSolutionsTopic().equals(command.address)) {
			// Process new solution message
			log.info("ExternalBrokerListenerService: Received a new Solution message from external broker: {}", command.body);
			processSolutionMessage(command);
		}
	}

	private void processSolutionMessage(Command command) {
		if (command.body.get("VariableValues") instanceof Map varValues) {
			log.info("ExternalBrokerListenerService: New Variable Values: {}", varValues);
			mvvService.translateAndSetValues(varValues);
		}
	}

	/*private void processCommandMessage(Command command) throws ClientException {
		// Get application id
		String appId = getAppId(command, commandsResponsePublisher);
		if (appId == null) return;

		// Get command string
		String commandStr = command.body.getOrDefault("command", "").toString();
		log.debug("ExternalBrokerListenerService: Command: {}", commandStr);

		sendResponse(commandsResponsePublisher, appId, "ERROR: ---NOT YET IMPLEMENTED---: "+ command.body);
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
		sendResponse(publisher, null, "ERROR: No Application Id found in message: "+ command.body);

		return null;
	}

	private void sendResponse(Publisher publisher, String appId, Object response) {
		publisher.send(Map.of(
				"response", response
		), appId);
	}*/
}