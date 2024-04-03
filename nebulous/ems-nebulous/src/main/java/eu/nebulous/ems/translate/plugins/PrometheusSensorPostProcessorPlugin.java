/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.plugins;

import gr.iccs.imu.ems.translate.model.PullSensor;
import gr.iccs.imu.ems.translate.model.Sensor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PrometheusSensorPostProcessorPlugin implements SensorPostProcessorPlugin {
    public final static List<String> PROMETHEUS_TYPES = List.of("prometheus");

    @Override
    public List<String> getSupportedTypes() {
        return PROMETHEUS_TYPES;
    }

    @Override
    public void postProcessSensor(Sensor sensor, String sensorType, Map<String, Object> sensorSpec) {
        if (sensorType!=null && PROMETHEUS_TYPES.contains(sensorType.toLowerCase())) {
            log.trace("PrometheusSensorPostProcessorPlugin: SPEC: {}", sensorSpec);
            log.trace("PrometheusSensorPostProcessorPlugin: SENSOR: {}", sensor);
            log.trace("PrometheusSensorPostProcessorPlugin: CONFIG: {}", sensor.getConfiguration());
            log.trace("PrometheusSensorPostProcessorPlugin: INTERVAL: {}", ((PullSensor)sensor).getInterval());
        }
    }
}
