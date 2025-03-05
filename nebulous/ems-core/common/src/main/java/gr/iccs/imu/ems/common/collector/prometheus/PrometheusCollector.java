/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.collector.prometheus;

import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.common.collector.AbstractEndpointCollector;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.util.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects measurements from a Prometheus exporter endpoint
 */
@Slf4j
public class PrometheusCollector extends AbstractEndpointCollector<String> implements IPrometheusCollector {
    protected PrometheusCollectorProperties properties;
    protected RestClient restClient;

    private Set<String> allowedTags;
    private boolean allowTagsInDestinationName;
    private String destinationNameFormatter = "${metricName}";
    private boolean addTagsAsEventProperties;
    private boolean addTagsInEventPayload;

    @SuppressWarnings("unchecked")
    public PrometheusCollector(String id, PrometheusCollectorProperties properties, CollectorContext collectorContext, TaskScheduler taskScheduler, EventBus<String,Object,Object> eventBus) {
        super(id, properties, collectorContext, taskScheduler, eventBus);
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        log.debug("Collectors::{}: properties: {}", collectorId, properties);
        super.afterPropertiesSet();

        this.allowedTags = properties.getAllowedTags();
        this.allowTagsInDestinationName = properties.isAllowTagsInDestinationName();
        this.destinationNameFormatter = properties.getDestinationNameFormatter();
        this.addTagsAsEventProperties = properties.isAddTagsAsEventProperties();
        this.addTagsInEventPayload = properties.isAddTagsInEventPayload();

        if (StringUtils.isBlank(properties.getUrl())) {
            String url = "http://127.0.0.1:9090/metrics";
            log.debug("Collectors::{}: URL not specified. Assuming {}", collectorId, url);
            properties.setUrl(url);
        }

        // Initialize REST client
        this.restClient = createRestClient();
    }

    protected ResponseEntity<String> getData(String url) {
        return restClient.get().uri(url).retrieve().toEntity(String.class);
    }

    protected void processData(String data, String nodeAddress, ProcessingStats stats) {
        String[] lines = data.split("\n");

        List<OpenMetricsParser.MetricInstance> metricInstances =
                new OpenMetricsParser(properties.isThrowExceptionWhenExcessiveCharsOccur()).processInput(lines);
        log.debug("Collectors::{}: Metric instances extracted: {}", collectorId, metricInstances);

        for (OpenMetricsParser.MetricInstance instance : metricInstances) {
            // Create event
            EventMap event = new EventMap(instance.getMetricValue(), 1, instance.getTimestamp());

            // Add tags into event properties and/or payload
            Map<String, String> tags = instance.getTags();
            if (tags != null) {
                if (allowedTags != null && ! allowedTags.isEmpty()) {
                    tags.keySet().retainAll(allowedTags);
                }

                if (addTagsAsEventProperties)
                    event.getEventProperties().putAll(tags);
                if (addTagsInEventPayload)
                    event.putAll(tags);
            }

            // Get destination names and publish event
            String baseMetricName = instance.getMetricName();
            String destination = StringUtils.isNotBlank(destinationNameFormatter)
                    ? destinationNameFormatter.replace("${metricName}", baseMetricName)
                    : baseMetricName;
            log.debug("Collectors::{}: Metric instances extracted: {}", collectorId, destination);

            if (!destination.contains("${")) {
                log.debug("Collectors::{}: Publishing event to destination: {}", collectorId, destination);
                updateStats(publishMetricEvent(destination, event, nodeAddress), stats);
            } else
            if (allowTagsInDestinationName && tags!=null && ! tags.isEmpty()) {
                tags.forEach((name,value) -> {
                    String d = destination.replace("${"+name+"}", value);
                    log.debug("Collectors::{}: Publishing event to tagged destination: {}", collectorId, d);
                    updateStats(publishMetricEvent(d, event, nodeAddress), stats);
                });
            }

            if (Thread.currentThread().isInterrupted()) break;
        }
    }
}
