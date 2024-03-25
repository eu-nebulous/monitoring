/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.boot;

import eu.nebulous.ems.service.ExternalBrokerServiceProperties;
import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.*;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class NebulousEventsService implements InitializingBean {
	private final EmsBootProperties properties;
	private final ExternalBrokerServiceProperties externalBrokerServiceProperties;
	private final TaskScheduler taskScheduler;
	private final BootService bootService;
	private final ModelsService modelsService;
    private Publisher modelsResponsePublisher;
	private Publisher emsBootResponsePublisher;
	private final ArrayBlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(100);
	private ScheduledFuture<?> processorFuture;

	@Override
	public void afterPropertiesSet() throws Exception {
		if (! properties.isEnabled()) {
			log.info("EMS Boot is disabled");
			return;
		}

		log.info("EMS Boot is enabled");
		initializeConnector();
		startCommandProcessor();
	}

	private void initializeConnector() {
		// Create message handler
		Handler messageHandler = new Handler() {
			@Override
			public void onMessage(String key, String address, Map body, Message message, Context context) {
				try {
					log.debug("MessageHandler: Got new message: key={}, address={}, body={}, message={}",
							key, address, body, message);
					super.onMessage(key, address, body, message, context);
					commandQueue.add(new Command(key, address, body, message, context));
				} catch (IllegalStateException e) {
					log.warn("MessageHandler: Commands queue is full. Dropping command: queue-size={}", commandQueue.size());
				} catch (Exception e) {
					log.warn("MessageHandler: Error while processing message: ", e);
				}
			}
		};

		// Create consumer
        List<Consumer> consumers = List.of(
				new Consumer(properties.getDslTopic(), properties.getDslTopic(), messageHandler, null, true, true),
				new Consumer(properties.getModelsTopic(), properties.getModelsTopic(), messageHandler, null, true, true),
                new Consumer(properties.getEmsBootTopic(), properties.getEmsBootTopic(), messageHandler, null, true, true)
        );

		// Create publisher
		modelsResponsePublisher = new Publisher(properties.getModelsResponseTopic(), properties.getModelsResponseTopic(), true, true);
		emsBootResponsePublisher = new Publisher(properties.getEmsBootResponseTopic(), properties.getEmsBootResponseTopic(), true, true);
        List<Publisher> publishers = List.of(
                modelsResponsePublisher, emsBootResponsePublisher
        );

		// Create connector
		createConnector(publishers, consumers)
				.start();
	}

	@NonNull
	private Connector createConnector(List<Publisher> publishers, List<Consumer> consumers) {
		return new Connector(
				EmsBootProperties.COMPONENT_NAME,
				new ConnectorHandler() {
					@Override
					public void onReady(Context context) {
						log.info("Sending state 'ready' event");
						((StatePublisher)context.getPublisher("state")).ready();
					}
				},
				publishers, consumers,
				true, true,
				new StaticExnConfig(
						externalBrokerServiceProperties.getBrokerAddress(),
						externalBrokerServiceProperties.getBrokerPort(),
						externalBrokerServiceProperties.getBrokerUsername(),
						externalBrokerServiceProperties.getBrokerPassword(),
						externalBrokerServiceProperties.getHealthTimeout())
		);
	}

	private void startCommandProcessor() {
		processorFuture = taskScheduler.scheduleAtFixedRate(() -> {
			if (! commandQueue.isEmpty()) {
				try {
					Command command = commandQueue.take();
					processMessage(command);
				} catch (InterruptedException e) {
					log.warn("Command processor interrupted. Exiting process loop");
					processorFuture.cancel(false);
				} catch (Exception e) {
					log.warn("Exception while processing command: {}\n", commandQueue, e);
				}
			}
		}, Instant.now(), Duration.ofMillis(properties.getProcessorPeriod()));
	}

	private void processMessage(Command command) throws ClientException, IOException {
		// Get application id
		String appId = getAppId(command);
		if (appId == null) {
			log.warn("ERROR: No app id found in message. Ignoring message: topic={}, body={}", command.address(), command.body());
			sendResponse(modelsResponsePublisher, null, "ERROR: No app id found in message: topic=" + command.address() + ", body=" + command.body());
			return;
		}

		if (properties.getDslTopic().equals(command.address())) {
			String result = modelsService.extractBindings(command, appId);
			if (!"OK".equalsIgnoreCase(result))
				sendResponse(modelsResponsePublisher, appId, result);
		} else
		if (properties.getModelsTopic().equals(command.address())) {
			String result = modelsService.processMetricModelMessage(command, appId);
			if (!"OK".equalsIgnoreCase(result))
				sendResponse(modelsResponsePublisher, appId, result);
		} else
		if (properties.getEmsBootTopic().equals(command.address())) {
			bootService.processEmsBootMessage(command, appId, emsBootResponsePublisher);
		} else
			log.error("ERROR: Received message from unexpected topic: {}", command.address());
	}

	private String getAppId(Command command) throws ClientException {
		// Check if 'applicationId' is provided in message body
		String appId = null;
		if (StringUtils.isBlank(appId)) appId = command.body().getOrDefault("application", "").toString();
		if (StringUtils.isBlank(appId)) appId = command.body().getOrDefault("uuid", "").toString();
		if (StringUtils.isNotBlank(appId)) {
			log.debug("Application Id found in message body: {}", appId);
			return appId;
		}
		log.debug("No Application Id found in message body: {}", command.body());

		// Check if 'applicationId' is provided in message properties
		Object propApp = command.message().property(properties.getApplicationIdPropertyName());
		appId = propApp != null ? propApp.toString() : null;
		if (StringUtils.isNotBlank(appId)) {
			log.debug("Application Id found in message properties: {}", appId);
			return appId;
		}
		log.warn("No Application Id found in message body or properties: {}", command.body());

		return null;
	}

	private void sendResponse(Publisher publisher, String appId, Object response) {
		publisher.send(Map.of(
				"response", response
		), appId);
	}
}