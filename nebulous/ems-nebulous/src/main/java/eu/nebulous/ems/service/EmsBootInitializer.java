/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import eu.nebulous.ems.EmsNebulousProperties;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.controller.ControlServiceRequestInfo;
import gr.iccs.imu.ems.util.NetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.qpid.protonj2.client.Message;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class EmsBootInitializer extends AbstractExternalBrokerService implements ApplicationListener<ApplicationReadyEvent> {
	private final ApplicationContext applicationContext;
	private final EmsBootInitializerProperties bootInitializerProperties;
	private final NebulousEmsTranslatorProperties translatorProperties;
	private final String applicationId;
	private final AtomicBoolean processingResponse = new AtomicBoolean(false);
	private ScheduledFuture<?> bootFuture;
	private Consumer consumer;
	private Publisher publisher;

	public EmsBootInitializer(ApplicationContext applicationContext,
							  EmsNebulousProperties emsNebulousProperties,
							  EmsBootInitializerProperties bootInitializerProperties,
							  NebulousEmsTranslatorProperties translatorProperties,
							  ExternalBrokerServiceProperties properties,
							  TaskScheduler scheduler)
	{
		super(properties, scheduler);
		this.applicationContext = applicationContext;
		this.bootInitializerProperties = bootInitializerProperties;
		this.translatorProperties = translatorProperties;
		this.applicationId = emsNebulousProperties.getApplicationId();
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		if (StringUtils.isBlank(applicationId)) {
			log.warn("===================> EMS is ready -- Application Id is blank. EMS Boot disabled");
			return;
		}
		if (! bootInitializerProperties.isEnabled()) {
			log.warn("===================> EMS is ready -- EMS Boot disabled due to configuration");
			return;
		}
		if (! properties.isEnabled()) {
			log.warn("===================> EMS is ready -- EMS Boot disabled because External broker service is disabled");
			return;
		}
		log.info("===================> EMS is ready -- Scheduling EMS Boot message -- App Id: {}", applicationId);

		// Start connector used for EMS Booting
		startConnector();

		// Schedule sending EMS Boot message
		bootFuture = taskScheduler.scheduleAtFixedRate(this::sendEmsBootReadyEvent,
				Instant.now().plus(bootInitializerProperties.getInitialWait()), bootInitializerProperties.getRetryPeriod());
	}

	private void startConnector() {
		Handler messageHandler = new Handler() {
			@Override
			public void onMessage(String key, String address, Map body, Message message, Context context) {
				log.debug("EmsBootInitializer: Received a new EMS Boot Response message: key={}, address={}, body={}, message={}",
						key, address, body, message);
				processEmsBootResponseMessage(body);
			}
		};
		consumer = new Consumer(properties.getEmsBootResponseTopic(), properties.getEmsBootResponseTopic(), messageHandler, null, true, true);
		publisher = new Publisher(properties.getEmsBootTopic(), properties.getEmsBootTopic(), true, true);
		connectToBroker(List.of(publisher), List.of(consumer));
	}

	protected void sendEmsBootReadyEvent() {
		if (publisher==null) {
			log.warn("ExternalBrokerPublisherService: EMS Boot message not sent because External broker publisher is null");
			return;
		}
		Map<String, String> message = Map.of(
				"application", applicationId,
//				"internal-address", NetUtil.getDefaultIpAddress(),
//				"public-address", NetUtil.getPublicIpAddress(),
				"address", NetUtil.getIpAddress()
		);
		log.debug("ExternalBrokerPublisherService: Sending message to EMS Boot: {}", message);
		publisher.send(message, null, true);
		log.debug("ExternalBrokerPublisherService: Sent message to EMS Boot");
	}

	protected void processEmsBootResponseMessage(Map body) {
		if (! processingResponse.compareAndSet(false, true)) {
			log.warn("EmsBootInitializer: A previous EMS Boot response is still being processed. Ignoring this one.");
			return;
		}

		try {
			// Process EMS Boot Response message
			String appId = body.get("application").toString();
			String modelStr = body.get("metric-model").toString();
			Map<String,String> bindingsMap = (Map) body.get("bindings");
			log.info("""
					EmsBootInitializer: Received an EMS Boot Response:
					    App-Id: {}
					  Bindings: {}
					     Model: {}
					""", appId, bindingsMap, modelStr);

			try {
				// Process metric model and bindings
				processMetricModel(appId, modelStr);
				processBindings(appId, bindingsMap);

				// Stop further EMS Boot requests
				if (bootFuture!=null && ! bootFuture.isDone())
					bootFuture.cancel(true);
			} catch (Exception e) {
				log.warn("EmsBootInitializer: EXCEPTION while processing Metric Model for: app-id={} -- Exception: ", appId, e);
			}
		} catch (Exception e) {
			log.warn("EmsBootInitializer: EXCEPTION while processing EMS Boot Response message: ", e);
		}

		processingResponse.set(false);
	}

	public void processBindings(String appId, Map<String, String> bindingsMap) {
		applicationContext.getBean(MvvService.class).setBindings(bindingsMap);
		log.info("Set MVV bindings to: {}", bindingsMap);
	}

	public void processMetricModel(String appId, String modelStr) throws IOException {
		// If 'model' string is provided, store it in a file
		String modelFile;
		if (StringUtils.isNotBlank(modelStr)) {
			modelFile = getModelFile(appId);
			storeModel(modelFile, modelStr);
		} else {
			log.warn("ExternalBrokerListenerService: Parameters 'modelStr' and 'modelFile' are both blank");
			throw new IllegalArgumentException("Parameters 'modelStr' and 'modelFile' are both blank");
		}

		// Call control-service to process model, also pass a callback to get the result
		applicationContext.getBean(ControlServiceCoordinator.class).processAppModel(modelFile, null,
				ControlServiceRequestInfo.create(appId, null, null, null,
						(result) -> {
							// Send message with the processing result
							log.info("Metric model processing result: {}", result);
						}));
	}

	private String getModelFile(String appId) {
		return String.format("model-%s--%d.yml", appId, System.currentTimeMillis());
	}

	private void storeModel(String fileName, String modelStr) throws IOException {
		Path path = Paths.get(translatorProperties.getModelsDir(), fileName);
		Files.writeString(path, modelStr);
		log.info("ExternalBrokerListenerService: Stored metric model in file: {}", path);
	}
}