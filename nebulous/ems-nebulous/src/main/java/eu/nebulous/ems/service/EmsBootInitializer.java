/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import gr.iccs.imu.ems.util.NetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.protonj2.client.Message;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmsBootInitializer extends AbstractExternalBrokerService implements ApplicationListener<ApplicationReadyEvent> {
	private final ExternalBrokerListenerService listener;
	private Consumer consumer;
	private Publisher publisher;

	public EmsBootInitializer(ExternalBrokerServiceProperties properties,
							  ExternalBrokerListenerService listener,
							  TaskScheduler scheduler)
	{
		super(properties, scheduler);
		this.listener = listener;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		if (! properties.isEnabled()) {
			log.warn("===================> EMS is ready -- EMS Boot disabled due to configuration");
			return;
		}
		log.info("===================> EMS is ready -- Scheduling EMS Boot message");

		// Start connector used for EMS Booting
		startConnector();

		// Schedule sending EMS Boot message
		taskScheduler.schedule(this::sendEmsBootReadyEvent, Instant.now().plusSeconds(1));
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
//XXX:TODO: Work in PROGRESS...
		Map<String, String> message = Map.of(
				"internal-address", NetUtil.getDefaultIpAddress(),
				"public-address", NetUtil.getPublicIpAddress(),
				"address", NetUtil.getIpAddress()
		);
		log.debug("ExternalBrokerPublisherService: Sending message to EMS Boot: {}", message);
		publisher.send(message, null,true);
		log.debug("ExternalBrokerPublisherService: Sent message to EMS Boot");
	}

	protected void processEmsBootResponseMessage(Map body) {
		try {
			// Process EMS Boot Response message
			String appId = body.get("application").toString();
			String modelStr = body.get("yaml").toString();
			log.info("EmsBootInitializer: Received a new EMS Boot Response: App-Id: {}, Model:\n{}", appId, modelStr);

			try {
				listener.processMetricModel(appId, modelStr, null);
			} catch (Exception e) {
				log.warn("EmsBootInitializer: EXCEPTION while processing Metric Model for: app-id={} -- Exception: ", appId, e);
			}
		} catch (Exception e) {
			log.warn("EmsBootInitializer: EXCEPTION while processing EMS Boot Response message: ", e);
		}
	}
}