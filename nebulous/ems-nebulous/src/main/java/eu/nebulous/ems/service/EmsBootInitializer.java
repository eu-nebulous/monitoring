/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import eu.nebulous.ems.EmsNebulousProperties;
import eu.nebulous.ems.plugins.PredictionsPostTranslationPlugin;
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
	private final AtomicBoolean alreadyInitialized = new AtomicBoolean(false);
	private ScheduledFuture<?> bootFuture;
	private ScheduledFuture<?> periodicReportsFuture;
	private Consumer consumer;
	private Publisher publisher;
	private Publisher reportPublisher;

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
		periodicReportsFuture = taskScheduler.scheduleAtFixedRate(this::sendPeriodicReport,
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
		consumer = new Consumer(properties.getEmsBootResponseTopic(), properties.getEmsBootResponseTopic(), messageHandler, applicationId, true, true);
		publisher = new Publisher(properties.getEmsBootTopic(), properties.getEmsBootTopic(), true, true);
		if (StringUtils.isNotBlank(properties.getEmsReportTopic()))
			reportPublisher = new Publisher(properties.getEmsReportTopic(), properties.getEmsReportTopic(), true, true);
		connectToBroker(List.of(publisher, reportPublisher), List.of(consumer));
	}

	protected void sendPeriodicReport() {
		if (reportPublisher==null) {
			log.warn("ExternalBrokerPublisherService: EMS Boot message not sent because External broker publisher is null");
			return;
		}
		try {
			String internalAddress = StringUtils.defaultIfBlank(NetUtil.getDefaultIpAddress(), "");
			String publicAddress = StringUtils.defaultIfBlank(NetUtil.getPublicIpAddress(), "");
			Map<String, String> message = Map.of(
					"application", applicationId,
					"internal-address", internalAddress,
					"public-address", publicAddress,
					"address", NetUtil.getIpAddress()
			);
			log.debug("ExternalBrokerPublisherService: Sending periodic report: {}", message);
			reportPublisher.send(message, null, true);
			log.debug("ExternalBrokerPublisherService: Sent periodic report");
		} catch (Exception e) {
			log.warn("ExternalBrokerPublisherService: Exception while sending periodic report: ", e);
		}
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
		if (alreadyInitialized.get()) {
			log.warn("EmsBootInitializer: Received EMS Boot response but EMS is already initialized. Ignoring it.");
			return;
		}
		if (! processingResponse.compareAndSet(false, true)) {
			log.warn("EmsBootInitializer: A previous EMS Boot response is still being processed. Ignoring this one.");
			return;
		}

		try {
			// Process EMS Boot Response message
			String appId = body.get("application").toString();
			String modelStr = body.get("metric-model").toString();
			Map<String,Map<String,String>> bindingsMap = (Map) body.get("bindings");
			Map<String,Object> solutionMap = (Map) body.get("solution");
			List<String> metricsList = (List) body.get("optimiser-metrics");
			log.info("""
					EmsBootInitializer: Received an EMS Boot Response:
					      App-Id: {}
					    Bindings: {}
					    Solution: {}
					Opt. Metrics: {}
					       Model: {}
					""", appId, bindingsMap, solutionMap, metricsList, modelStr);

			if (! StringUtils.equals(applicationId, appId)) {
				log.warn("EmsBootInitializer: Ignoring EMS Boot response. Response App-Id does not match the configured App Id: {} != {}", appId, applicationId);
				return;
			}

			try {
				// Process metric model, bindings, optimiser metrics, and (current) solution
				processMetricModel(appId, modelStr, metricsList);
				processBindings(appId, bindingsMap);
				processSolution(appId, solutionMap);
				processOptimiserMetrics(appId, metricsList);

				// Stop further EMS Boot requests
				if (bootFuture!=null && ! bootFuture.isDone())
					bootFuture.cancel(true);
			} catch (Exception e) {
				log.warn("EmsBootInitializer: EXCEPTION while processing Metric Model for: app-id={} -- Exception: ", appId, e);
			}
		} catch (Exception e) {
			log.warn("EmsBootInitializer: EXCEPTION while processing EMS Boot Response message: ", e);
		}

		// Mark EMS as initialized
		alreadyInitialized.set(true);

		processingResponse.set(false);
	}

	private void processOptimiserMetrics(String appId, List<String> metricsList) {
		applicationContext.getBean(PredictionsPostTranslationPlugin.class).setOptimiserMetrics(metricsList);
		log.info("Set optimiser metrics to: {}", metricsList);
	}

	public void processBindings(String appId, Map<String, Map<String, String>> bindingsMap) {
		applicationContext.getBean(MvvService.class).setBindings(bindingsMap);
		log.info("Set MVV bindings to: {}", bindingsMap);
	}

	private void processSolution(String appId, Map<String, Object> solutionMap) {
		applicationContext.getBean(MvvService.class).translateAndSetValues(solutionMap);
		log.info("Set solution to: {}", solutionMap);
	}

	public void processMetricModel(String appId, String modelStr, List<String> requiredMetricsList) throws IOException {
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
						Map.of("required-metrics", requiredMetricsList),
						(result) -> {
							// Send message with the processing result
							log.info("Metric model processing result: {}", result);
						}));
	}

	private String getModelFile(String appId) {
		//return String.format("model-%s--%d.yml", appId, System.currentTimeMillis());
		return String.format("%s.yml", appId);
	}

	private void storeModel(String fileName, String modelStr) throws IOException {
		Path path = Paths.get(translatorProperties.getModelsDir(), fileName);
		Files.writeString(path, modelStr);
		log.info("ExternalBrokerListenerService: Stored metric model in file: {}", path);
	}
}