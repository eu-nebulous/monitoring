/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.plugins;

import eu.nebulous.ems.service.ExternalBrokerPublisherService;
import eu.nebulous.ems.service.ExternalBrokerServiceProperties;
import gr.iccs.imu.ems.control.plugin.BeaconPlugin;
import gr.iccs.imu.ems.control.properties.TopicBeaconProperties;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import gr.iccs.imu.ems.translate.TranslationContext;
import jakarta.jms.JMSException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionInfoBeaconPlugin implements BeaconPlugin {
    private final ExternalBrokerServiceProperties properties;
    private final ExternalBrokerPublisherService externalBrokerPublisherService;
    private final String externalBrokerMetricsToPredictTopic = ExternalBrokerServiceProperties.BASE_TOPIC_PREFIX + "metric_list";
    private final String externalBrokerSloViolationDetectorTopics = ExternalBrokerServiceProperties.BASE_TOPIC_PREFIX + "slo.new";

    public void init(TopicBeacon.BeaconContext context) {
        log.debug("PredictionInfoBeaconPlugin.init(): Invoked");
        if (properties.isEnabled()) {
            externalBrokerPublisherService.addAdditionalTopic(externalBrokerMetricsToPredictTopic, externalBrokerMetricsToPredictTopic);
            externalBrokerPublisherService.addAdditionalTopic(externalBrokerSloViolationDetectorTopics, externalBrokerSloViolationDetectorTopics);
            log.debug("PredictionInfoBeaconPlugin.init(): Initialized ExternalBrokerService");
        } else
            log.debug("PredictionInfoBeaconPlugin.init(): ExternalBrokerService is disabled due to configuration");
    }

    public void transmit(TopicBeacon.BeaconContext context) {
        log.debug("PredictionInfoBeaconPlugin.transmit(): Invoked");
        transmitPredictionInfo(context);
        transmitSloViolatorInfo(context);
        log.trace("PredictionInfoBeaconPlugin.transmit(): Transmitted ");
    }

    private <T>Set<T> emptyIfNull(Set<T> s) {
        if (s==null) return Collections.emptySet();
        return s;
    }

    public void transmitPredictionInfo(TopicBeacon.BeaconContext context) {
        TopicBeaconProperties properties = context.getProperties();
        if (emptyIfNull(properties.getPredictionTopics()).isEmpty()) return;

        // Get TranslationContext for current model
        String modelId = context.getCurrentAppModelId();
        log.trace("Topic Beacon: transmitPredictionInfo: app-model-id: {}", modelId);
        TranslationContext translationContext = context.getTranslationContextOfAppModel(modelId);
        if (translationContext==null) {
            log.trace("Topic Beacon: transmitPredictionInfo: No TranslationContext for app-model-id: {}", modelId);
            return;
        }

        //Set<String> topLevelMetrics = coordinator.getGlobalGroupingMetrics(modelId);
        //log.debug("Topic Beacon: transmitPredictionInfo: DAG Global-Level Metrics: {}", topLevelMetrics);

        // Get metric contexts of top-level DAG nodes
        String metricContexts = translationContext.getAdditionalResultsAs(
                PredictionsPostTranslationPlugin.PREDICTION_TOP_LEVEL_NODES_METRICS, String.class);
        Map metricContextsMap = translationContext.getAdditionalResultsAs(
                PredictionsPostTranslationPlugin.PREDICTION_TOP_LEVEL_NODES_METRICS_MAP, Map.class);
        log.debug("Topic Beacon: transmitPredictionInfo: Metric Contexts for prediction: {}", metricContexts);

        // Skip event sending if payload is empty
        if (StringUtils.isBlank(metricContexts)) {
            log.debug("Topic Beacon: transmitSloViolatorInfo: Payload is empty. Not sending event");
            return;
        }

        // Send event with payload
        log.debug("Topic Beacon: Transmitting Prediction info: event={}, topics={}", metricContexts, properties.getPredictionTopics());
        try {
            context.sendMessageToTopics(metricContexts, properties.getPredictionTopics());
            if (properties.isEnabled())
                externalBrokerPublisherService.publishMessage(externalBrokerMetricsToPredictTopic, metricContextsMap);
        } catch (JMSException e) {
            log.error("Topic Beacon: EXCEPTION while transmitting Prediction info: event={}, topics={}, exception: ",
                    metricContexts, properties.getPredictionTopics(), e);
        }
    }

    public void transmitSloViolatorInfo(TopicBeacon.BeaconContext context) {
        TopicBeaconProperties properties = context.getProperties();
        if (emptyIfNull(properties.getSloViolationDetectorTopics()).isEmpty()) return;

        // Get TranslationContext for current model
        String modelId = context.getCurrentAppModelId();
        log.trace("Topic Beacon: transmitSloViolatorInfo: current-app-model-id: {}", modelId);
        TranslationContext translationContext = context.getTranslationContextOfAppModel(modelId);
        if (translationContext==null) {
            log.trace("Topic Beacon: transmitSloViolatorInfo: No TranslationContext for app-model-id: {}", modelId);
            return;
        }

        // Get SLO metric decompositions (String) from TranslationContext
        String sloMetricDecompositions = translationContext.getAdditionalResultsAs(
                PredictionsPostTranslationPlugin.PREDICTION_SLO_METRIC_DECOMPOSITION, String.class);
        Map sloMetricDecompositionsMap = translationContext.getAdditionalResultsAs(
                PredictionsPostTranslationPlugin.PREDICTION_SLO_METRIC_DECOMPOSITION_MAP, Map.class);
        log.debug("Topic Beacon: transmitSloViolatorInfo: SLO metric decompositions: {}", sloMetricDecompositions);

        if (StringUtils.isBlank(sloMetricDecompositions)) {
            log.debug("Topic Beacon: transmitSloViolatorInfo: Payload is empty. Not sending event");
            return;
        }

        // Send event with payload
        log.debug("Topic Beacon: Transmitting SLO Violator info: event={}, topics={}", sloMetricDecompositions, properties.getSloViolationDetectorTopics());
        try {
            context.sendMessageToTopics(sloMetricDecompositions, properties.getSloViolationDetectorTopics());
            if (properties.isEnabled())
                externalBrokerPublisherService.publishMessage(externalBrokerSloViolationDetectorTopics, sloMetricDecompositionsMap);
        } catch (JMSException e) {
            log.error("Topic Beacon: EXCEPTION while transmitting SLO Violator info: event={}, topics={}, exception: ",
                    sloMetricDecompositions, properties.getSloViolationDetectorTopics(), e);
        }
    }
}
