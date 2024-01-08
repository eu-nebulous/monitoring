/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokerclient.event;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Slf4j
public class EventMap extends LinkedHashMap<String, Object> implements Serializable {
    public EventMap() {
        super();
    }

    public EventMap(Map<String, Object> map) {
        super(map);
    }

    public EventMap(double metricValue, int level, long timestamp) {
        put("metricValue", metricValue);
        put("level", level);
        put("timestamp", timestamp);
    }

    public static String[] getPropertyNames() {
        return new String[]{"metricValue", "level", "timestamp"};
    }

    public static Class[] getPropertyClasses() {
        return new Class[]{Double.class, Integer.class, Long.class};
    }
}