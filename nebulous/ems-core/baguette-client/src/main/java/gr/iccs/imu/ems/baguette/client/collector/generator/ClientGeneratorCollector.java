/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.collector.generator;

import gr.iccs.imu.ems.baguette.client.IClientCollector;
import gr.iccs.imu.ems.baguette.client.collector.ClientCollectorContext;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.collector.generator.GeneratorCollectorProperties;
import gr.iccs.imu.ems.util.EventBus;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Generates measurements
 */
@Slf4j
@Component
public class ClientGeneratorCollector extends gr.iccs.imu.ems.common.collector.generator.GeneratorCollector implements IClientCollector {
    private List<Map<String,Object>> configurations;

    public ClientGeneratorCollector(@NonNull GeneratorCollectorProperties properties,
                                    @NonNull CollectorContext collectorContext,
                                    @NonNull TaskScheduler taskScheduler,
                                    @NonNull EventBus<String, Object, Object> eventBus)
    {
        super("ClientGeneratorCollector", properties, collectorContext, taskScheduler, eventBus);
        if (!(collectorContext instanceof ClientCollectorContext))
            throw new IllegalArgumentException("Invalid CollectorContext provided. Expected: ClientCollectorContext, but got "+collectorContext.getClass().getName());
    }

    @Override
    public void setConfiguration(Object config) {
        log.info("Collectors::{}: setConfiguration: {}", collectorId, config);
    }

    @Override
    public synchronized void activeGroupingChanged(String oldGrouping, String newGrouping) {
    }
}
