/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.plugins;

import gr.iccs.imu.ems.translate.model.Sensor;
import gr.iccs.imu.ems.util.EmsConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NetdataSensorPostProcessorPlugin implements SensorPostProcessorPlugin {
    public final static String NETDATA_TYPE = "netdata";

    @Override
    public List<String> getSupportedTypes() {
        return List.of(NETDATA_TYPE);
    }

    @Override
    public void postProcessSensor(Sensor sensor, String sensorType, Map<String, Object> sensorSpec) {
        if (NETDATA_TYPE.equalsIgnoreCase(sensorType)) {
            Object o = sensor.getConfiguration().get("mapping");
            if (o instanceof String s && StringUtils.isNotBlank(s)) {
                if (! sensor.getConfiguration().containsKey(EmsConstant.COLLECTOR_DESTINATION_ALIASES)) {
                    sensor.getConfiguration().put(EmsConstant.COLLECTOR_DESTINATION_ALIASES, s.trim());
                }
            }
        }
    }
}
