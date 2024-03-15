/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.collector.prometheus;

import gr.iccs.imu.ems.baguette.client.Collector;
import gr.iccs.imu.ems.baguette.client.collector.ClientCollectorContext;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.collector.prometheus.PrometheusCollectorProperties;
import gr.iccs.imu.ems.util.EventBus;
import gr.iccs.imu.ems.util.GROUPING;
import gr.iccs.imu.ems.util.GroupingConfiguration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Collects measurements from Prometheus exporter
 */
@Slf4j
@Component
public class PrometheusCollector extends gr.iccs.imu.ems.common.collector.prometheus.PrometheusCollector implements Collector {
    private List<Map<String,Object>> configuration;

    public PrometheusCollector(@NonNull PrometheusCollectorProperties properties,
                               @NonNull CollectorContext collectorContext,
                               @NonNull TaskScheduler taskScheduler,
                               @NonNull EventBus<String, Object, Object> eventBus)
    {
        super("PrometheusCollector", properties, collectorContext, taskScheduler, eventBus);
        if (!(collectorContext instanceof ClientCollectorContext))
            throw new IllegalArgumentException("Invalid CollectorContext provided. Expected: ClientCollectorContext, but got "+collectorContext.getClass().getName());
    }

    @Override
    public String getName() {
        return "prometheus";
    }

    @Override
    public void setConfiguration(Object config) {
        if (config instanceof List sensorConfigList) {
            configuration = sensorConfigList.stream()
                    .filter(o -> o instanceof Map)
                    .filter(map -> ((Map)map).keySet().stream().allMatch(k->k instanceof String))
                    .toList();
            log.info("Collectors::Prometheus: setConfiguration: {}", configuration);
        }
    }

    public synchronized void activeGroupingChanged(String oldGrouping, String newGrouping) {
        HashSet<String> topics = new HashSet<>();
        for (String g : GROUPING.getNames()) {
            GroupingConfiguration grp = ((ClientCollectorContext)collectorContext).getGroupings().get(g);
            if (grp!=null)
                topics.addAll(grp.getEventTypeNames());
        }
        log.info("Collectors::Prometheus: activeGroupingChanged: New Allowed Topics for active grouping: {} -- {}", newGrouping, topics);
        processAllowedTopics(topics);
    }

}
