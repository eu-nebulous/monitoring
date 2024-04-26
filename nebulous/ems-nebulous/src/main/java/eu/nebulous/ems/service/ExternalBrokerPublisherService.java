/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import com.google.gson.Gson;
import eu.nebulous.ems.translate.NameNormalization;
import eu.nebulouscloud.exn.core.Publisher;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.control.plugin.PostTranslationPlugin;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import gr.iccs.imu.ems.translate.Grouping;
import gr.iccs.imu.ems.translate.TranslationContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExternalBrokerPublisherService extends AbstractExternalBrokerService
		implements PostTranslationPlugin, MessageListener
{
	private static final String COMBINED_SLO_PUBLISHER_KEY = "COMBINED_SLO_PUBLISHER_KEY_" + System.currentTimeMillis();
	private final BrokerCepService brokerCepService;
	private final NameNormalization nameNormalization;
	private final Map<String, String> additionalTopicsMap = new HashMap<>();
	private final Gson gson = new Gson();
	private Map<String, Publisher> publishersMap = Map.of();
	private String applicationId;
	private Set<String> sloSet;

	protected ExternalBrokerPublisherService(ExternalBrokerServiceProperties properties,
											 TaskScheduler taskScheduler, BrokerCepService brokerCepService,
											 NameNormalization nameNormalization)
	{
		super(properties, taskScheduler);
		this.brokerCepService = brokerCepService;
		this.nameNormalization = nameNormalization;
	}

	public void addAdditionalTopic(@NonNull String topic, @NonNull String externalBrokerTopic) {
		if (StringUtils.isNotBlank(topic) && StringUtils.isNotBlank(externalBrokerTopic))
			additionalTopicsMap.put(topic.trim(), externalBrokerTopic.trim());
		else
			log.warn("ExternalBrokerPublisherService: Ignoring call to 'addAdditionalTopic' with blank argument(s): topic={}, externalBrokerTopic={}",
					topic, externalBrokerTopic);
	}

	@Override
	public void processTranslationResults(TranslationContext translationContext, TopicBeacon topicBeacon) {
		if (!properties.isEnabled()) {
			log.info("ExternalBrokerPublisherService: Disabled due to configuration");
			return;
		}
		log.debug("ExternalBrokerPublisherService: Initializing...");

		// Get application id
		applicationId = translationContext.getAppId();

		// Get Top-Level topics (i.e. those at GLOBAL grouping)
		Map.Entry<String, Set<String>> tmp = translationContext.getG2T().entrySet().stream()
				.filter(e -> e.getKey().equalsIgnoreCase(Grouping.GLOBAL.name()))
				.findAny().orElse(null);
		Set<String> topLevelTopics = tmp!=null ? tmp.getValue() : null;
		log.debug("ExternalBrokerPublisherService: Top-Level topics: {}", topLevelTopics);

		if (topLevelTopics==null || topLevelTopics.isEmpty()) {
			log.warn("ExternalBrokerPublisherService: No top-level topics found.");
			return;
		}

		// Find top-level topics that correspond to SLOs (or other requirements)
		log.trace("ExternalBrokerPublisherService:  SLOs-BEFORE: {}", translationContext.getSLO());
		sloSet = translationContext.getSLO().stream()
				.map(nameNormalization)
				.collect(Collectors.toSet());
		log.trace("ExternalBrokerPublisherService:   SLOs-AFTER: {}", sloSet);

		// Prepare publishers
		publishersMap = topLevelTopics.stream().collect(Collectors.toMap(
				t -> t, t -> {
					/*if (sloSet.contains(t)) {
						// Send SLO violations to combined SLO topic
						return new Publisher(t, properties.getCombinedSloTopic(), true, true);
					} else {
						// Send non-SLO events to their corresponding Nebulous topics
						return new Publisher(t, properties.getMetricsTopicPrefix() + t, true, true);
					}*/
					return new Publisher(t, properties.getMetricsTopicPrefix() + t, true, true);
				},
				(publisher, publisher2) -> publisher, HashMap::new
		));

		// Also add publisher for Event Type VI messages
		publishersMap.put(COMBINED_SLO_PUBLISHER_KEY,
				new Publisher(COMBINED_SLO_PUBLISHER_KEY, properties.getCombinedSloTopic(), true, true));

		// Also prepare additional publishers
		log.debug("ExternalBrokerPublisherService: additionalTopicsMap: {}", additionalTopicsMap);
		if (! additionalTopicsMap.isEmpty()) {
			additionalTopicsMap.forEach((topic, externalBrokerTopic) -> {
				log.trace("ExternalBrokerPublisherService: additionalTopicsMap: Additional publisher for: {} --> {}", topic, externalBrokerTopic);
				publishersMap.put(topic, new Publisher(topic, externalBrokerTopic, true, true));
			});
		}

		// Create connector to external broker
		connectToBroker(publishersMap.values().stream().toList(), List.of());

		// Register for EMS broker events
		brokerCepService.getBrokerCepBridge().getListeners().add(this);

		log.info("ExternalBrokerPublisherService: initialized publishers");
	}

	@Override
	public void onMessage(Message message) {
        if (message instanceof ActiveMQMessage amqMessage) {
            String topic = amqMessage.getDestination().getPhysicalName();
			Publisher publisher = publishersMap.get(topic);
			if (publisher!=null) {
				log.trace("ExternalBrokerPublisherService: Sending message to external broker: topic: {}, message: {}", topic, amqMessage);
                try {
					// Send metric event
					Map body = getMessageAsMap(amqMessage);
					if (body!=null) {
						publishMessage(publisher, body);
						log.trace("ExternalBrokerPublisherService: Sent message to external broker: topic: {}, message: {}", topic, body);

						// If an SLO, also send an Event Type VI event to combined SLO topics
						if (sloSet.contains(topic)) {
							publishMessage(publishersMap.get(COMBINED_SLO_PUBLISHER_KEY), Map.of(
									"severity", 0.5,
									"predictionTime", Instant.now().toEpochMilli(),
									"probability", 1.0
							));
							log.trace("ExternalBrokerPublisherService: Also sent message to combined SLO topic: topic: {}, message: {}", topic, body);
						}
					} else
						log.warn("ExternalBrokerPublisherService: Could not get body from internal broker message: topic: {}, message: {}", topic, amqMessage);
                } catch (JMSException e) {
					log.warn("ExternalBrokerPublisherService: Error while sending message: {}, Exception: ", topic, e);
                }
            } else
				log.warn("ExternalBrokerPublisherService: No publisher found for topic: {}, message: {}", topic, message);
        } else
			log.trace("ExternalBrokerPublisherService: Ignoring non-ActiveMQ message from internal broker: {}", message);
	}

	private Map getMessageAsMap(ActiveMQMessage amqMessage) throws JMSException {
        return switch (amqMessage) {
            case ActiveMQTextMessage textMessage -> EventMap.parseEventMap(textMessage.getText());
            case ActiveMQObjectMessage objectMessage -> EventMap.parseEventMap(objectMessage.getObject().toString());
            case ActiveMQMapMessage mapMessage -> mapMessage.getContentMap();
            case null, default -> null;
        };
	}

	public boolean publishMessage(String topic, String bodyStr) {
		if (! properties.isEnabled()) return false;
		Publisher publisher = publishersMap.get(topic);
		if (publisher!=null)
			publishMessage(publisher, gson.fromJson(bodyStr, Map.class));
		return publisher!=null;
	}

	public boolean publishMessage(String topic, Map body) {
		if (! properties.isEnabled()) return false;
		Publisher publisher = publishersMap.get(topic);
		if (publisher!=null)
			publishMessage(publisher, body);
		return publisher!=null;
	}

	private void publishMessage(Publisher publisher, Map body) {
		publisher.send(body, applicationId, Map.of(/*"prop1", "zz", "prop2", "cc"*/));
	}
}