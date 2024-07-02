/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.collector.prometheus;

import gr.iccs.imu.ems.common.collector.CollectorConstant;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.collector.prometheus.PrometheusCollector;
import gr.iccs.imu.ems.common.collector.prometheus.PrometheusCollectorProperties;
import gr.iccs.imu.ems.control.collector.IServerCollector;
import gr.iccs.imu.ems.control.collector.ServerCollectorContext;
import gr.iccs.imu.ems.util.EventBus;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Collects measurements from Prometheus endpoints
 */
@Slf4j
@Component
public class ServerPrometheusCollector extends PrometheusCollector implements IServerCollector {
    public ServerPrometheusCollector(@NonNull PrometheusCollectorProperties properties,
                                     @NonNull CollectorContext collectorContext,
                                     @NonNull TaskScheduler taskScheduler,
                                     @NonNull EventBus<String, Object, Object> eventBus)
    {
        super("ServerPrometheusCollector", properties, collectorContext, taskScheduler, eventBus);
        if (!(collectorContext instanceof ServerCollectorContext))
            throw new IllegalArgumentException("Invalid CollectorContext provided. Expected: ServerCollectorContext, but got "+collectorContext.getClass().getName());
    }

    public Map<String,Object> stringToConfigMap(String s) {
        return Map.of(CollectorConstant.PROMETHEUS_METRIC_KEY, s);
    }
}
