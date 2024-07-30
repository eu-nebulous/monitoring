/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.collector.generator;

import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.common.collector.AbstractEndpointCollector;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.collector.prometheus.IPrometheusCollector;
import gr.iccs.imu.ems.util.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates measurements. Can be used for testing and debugging
 */
@Slf4j
public class GeneratorCollector extends AbstractEndpointCollector<Map<String,Double>> implements IPrometheusCollector {
    protected GeneratorCollectorProperties properties;
    private long currentCount;
    private Double currentCountValue;

    @SuppressWarnings("unchecked")
    public GeneratorCollector(String id, GeneratorCollectorProperties properties, CollectorContext collectorContext, TaskScheduler taskScheduler, EventBus<String,Object,Object> eventBus) {
        super(id, properties, collectorContext, taskScheduler, eventBus);
        this.properties = properties;
        currentCountValue = properties.getLimitsStart();
    }

    @Override
    public void afterPropertiesSet() {
        log.debug("Collectors::{}: properties: {}", collectorId, properties);
        super.afterPropertiesSet();

        properties.setLimitsLower( asValueType(properties.getLimitsLower(), properties.getValueType()) );
        properties.setLimitsUpper( asValueType(properties.getLimitsUpper(), properties.getValueType()) );
        properties.setLimitsStart( asValueType(properties.getLimitsStart(), properties.getValueType()) );

        if (properties.getLimitsStart()<properties.getLimitsLower()) properties.setLimitsStart(properties.getLimitsLower());
        if (properties.getLimitsStart()>properties.getLimitsUpper()) properties.setLimitsStart(properties.getLimitsUpper());
        if (properties.getType()==GeneratorCollectorProperties.GENERATOR_TYPE.count && properties.getLimitsStep()==0)
            throw new IllegalArgumentException("GeneratorCollector: Step cannot be 0.0 when type is 'count'");
    }

    protected ResponseEntity<Map<String,Double>> getData(String url) {
        final LinkedHashMap<String,Double> map = new LinkedHashMap<>();
        properties.getDestinations().forEach(metric -> {
            map.put(metric, generateValue());
        });
        return ResponseEntity.ok().body(map);
    }

    private Double generateValue() {
        if (properties.getLimitsMaxCount()>0 && currentCount+1 > properties.getLimitsMaxCount()) {
            stop();
            return null;
        }
        if (properties.getType()==GeneratorCollectorProperties.GENERATOR_TYPE.count) {
            Double newCountValue = (currentCount==0)
                ? properties.getLimitsStart()
                : currentCountValue + properties.getLimitsStep();
            if (properties.getLimitsStep()>0 && newCountValue > properties.getLimitsUpper()
                || properties.getLimitsStep()<0 && newCountValue < properties.getLimitsLower())
            {
                newCountValue = switch (properties.getLimitsOnOverflow()) {
                    case round ->
                            newCountValue > properties.getLimitsUpper() ? properties.getLimitsLower() : properties.getLimitsUpper();
                    case reset ->
                            properties.getLimitsStart();
                    case stop -> {
                        stop();
                        yield null;
                    }
                    case reverse -> {
                        properties.setLimitsStep(-1 * properties.getLimitsStep());
                        yield  currentCountValue + properties.getLimitsStep();
                    }
                };
            }
            currentCountValue = newCountValue;
            if (currentCountValue==null)
                return null;
            currentCount++;
            return asValueType(newCountValue, properties.getValueType());
        }

        // else GENERATOR_TYPE.random
        double range = properties.getLimitsUpper() - properties.getLimitsLower();
        return asValueType(Math.random() * range + properties.getLimitsLower(), properties.getValueType());
    }

    private Double asValueType(double value, GeneratorCollectorProperties.GENERATOR_VALUE_TYPE valueType) {
        return valueType==GeneratorCollectorProperties.GENERATOR_VALUE_TYPE.Int
                ? Math.round(value) : value;
    }

    protected void processData(Map<String,Double> data, String nodeAddress, ProcessingStats stats) {
        long timestamp = System.currentTimeMillis();
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            String destination = entry.getKey();
            double value = entry.getValue();

            // Create event
            log.debug("Collectors::{}: Publishing event to destination: {}", collectorId, destination);
            EventMap event = new EventMap(value, 1, timestamp);
            updateStats(publishMetricEvent(destination, event, nodeAddress), stats);
            log.debug("Collectors::{}: Published event to destination: {} :: {}", collectorId, destination, event);

            if (Thread.currentThread().isInterrupted()) break;
        }
    }
}
