/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.List;

@Slf4j
public abstract class AbstractExternalBrokerService {
	protected final ExternalBrokerServiceProperties properties;
	protected final TaskScheduler taskScheduler;

	protected Connector connector;

	protected AbstractExternalBrokerService(ExternalBrokerServiceProperties properties, TaskScheduler taskScheduler) {
        this.properties = properties;
        this.taskScheduler = taskScheduler;
    }

    protected boolean checkProperties() {
		return properties!=null
				&& StringUtils.isNotBlank(properties.getBrokerAddress())
				&& (properties.getBrokerPort() > 0 && properties.getBrokerPort() <= 65535);
	}

	protected void connectToBroker(List<Publisher> publishers, List<Consumer> consumers) {
		try {
			log.debug("AbstractExternalBrokerService: Trying to connect to broker: {}@{}:{}",
					properties.getBrokerUsername(), properties.getBrokerAddress(), properties.getBrokerPort());
			Connector connector = createConnector(publishers, consumers);
			connector.start();
			log.info("AbstractExternalBrokerService: Connected to broker");

			Connector old_connector = this.connector;
			this.connector = connector;
			/*XXX:TODO: Report bug!!!
			if (old_connector!=null) {
				log.info("AbstractExternalBrokerService: Stopping old connector");
				old_connector.stop();
			}*/

		} catch (Exception e) {
			log.error("AbstractExternalBrokerService: Could not connect to broker: ", e);
			this.connector = null;
			if (properties.getRetryDelay()>0) {
				log.error("AbstractExternalBrokerService: Next attempt to connect to broker in {}s", properties.getRetryDelay());
				taskScheduler.schedule(() -> connectToBroker(publishers, consumers), Instant.now().plusSeconds(properties.getRetryDelay()));
			} else {
				log.error("AbstractExternalBrokerService: Will not retry to connect to broker (delay={})", properties.getRetryDelay());
			}
		}
	}

	@NonNull
	private Connector createConnector(List<Publisher> publishers, List<Consumer> consumers) {
		return new Connector(
                ExternalBrokerServiceProperties.COMPONENT_NAME,
				new ConnectorHandler() {
					@Override
					public void onReady(Context context) {
						log.debug("AbstractExternalBrokerService: onReady: connected to: {}@{}:{}",
								properties.getBrokerUsername(), properties.getBrokerAddress(), properties.getBrokerPort());
						//((StatePublisher)context.getPublisher("state")).ready();
						// ALSO SET 'enableState=true' AT LINE 83: 'false, false,' --> 'true, false,'
						/*
						if(context.hasPublisher("state")){
							StatePublisher sp = (StatePublisher) context.getPublisher("state");
							sp.starting();
							sp.started();
							sp.custom("forecasting");
							sp.stopping();
							sp.stopped();
						}*/
					}
				},
				publishers, consumers,
				false, false,
				new StaticExnConfig(
						properties.getBrokerAddress(), properties.getBrokerPort(),
						properties.getBrokerUsername(), properties.getBrokerPassword(),
						properties.getHealthTimeout())
		);
	}
}