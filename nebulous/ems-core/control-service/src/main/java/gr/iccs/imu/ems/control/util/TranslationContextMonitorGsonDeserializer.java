/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util;

import com.google.gson.*;
import gr.iccs.imu.ems.translate.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class TranslationContextMonitorGsonDeserializer implements JsonDeserializer<Monitor> {
    @Override
    public Monitor deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        log.debug("TranslationContextMonitorGsonDeserializer: INPUT:  jsonElement={}, type={}, context={}", jsonElement, type, jsonDeserializationContext);

        JsonObject jsonObject = (JsonObject) jsonElement;
        Monitor monitor = new Monitor();

        String metricName = jsonObject.getAsJsonPrimitive("metric").getAsString();
        monitor.setMetric(metricName);

        String _componentName = null;
        if (jsonObject.has("component")) {
            JsonPrimitive compNameElem = jsonObject.getAsJsonPrimitive("component");
            _componentName = compNameElem!=null ? compNameElem.getAsString() : null;
            monitor.setComponent(_componentName);
        }
        final String componentName = _componentName;

        // Find and initialize sensor
        JsonObject jsonSensorObject = jsonObject.getAsJsonObject("sensor");
        if (jsonSensorObject.has("anyType") && jsonSensorObject.get("anyType").isJsonObject()) {
            jsonSensorObject = jsonSensorObject.getAsJsonObject("anyType");
        }

        boolean isPull = jsonSensorObject.has("className")
                || jsonSensorObject.has("configuration")
                || jsonSensorObject.has("interval");
        boolean isPush = jsonSensorObject.has("port");
        if (isPull && isPush)
            throw new JsonParseException("Monitor.Sensor contains fields of both PullSensor and PushSensor class: "
                    + "metric=" + metricName + ", component=" + componentName);
        if (!isPull && !isPush)
            throw new JsonParseException("Monitor.Sensor contain no fields of either PullSensor or PushSensor class: "
                    + "metric=" + metricName + ", component=" + componentName);

        Sensor sensor;
        if (isPull) {
            PullSensor pullSensor = new PullSensor();
            if (jsonSensorObject.has("className") && !jsonSensorObject.get("className").isJsonNull()) {
                JsonPrimitive classNameElem = jsonSensorObject.getAsJsonPrimitive("className");
                String className = classNameElem!=null ? classNameElem.getAsString() : null;
                pullSensor.setClassName(className);
            }

            pullSensor.setInterval( getInterval(jsonSensorObject, metricName, componentName, "PullSensor") );

            pullSensor.setConfiguration( castToMapStringObject(
                    getConfiguration(jsonSensorObject, metricName, componentName, "PullSensor") ) );

            sensor = pullSensor;
        } else if (isPush) {
            PushSensor pushSensor = new PushSensor();
            int port = jsonSensorObject.getAsJsonPrimitive("port").getAsInt();
            pushSensor.setPort(port);

            pushSensor.setConfiguration( castToMapStringObject(
                    getConfiguration(jsonSensorObject, metricName, componentName, "PushSensor") ) );

            sensor = pushSensor;
        } else {
            throw new JsonParseException("Monitor.Sensor is neither Pull or Push: "
                    + "jsonSensorObject=" + jsonSensorObject);
        }
        monitor.setSensor(sensor);

        // Get sinks
        if (jsonObject.has("sinks")) {
            if (!jsonObject.get("sinks").isJsonNull()) {
                if (!jsonObject.get("sinks").isJsonArray())
                    throw new JsonParseException("Monitor.sinks must be an array or null: "
                            + "metric=" + metricName + ", component=" + componentName);

                List<Sink> sinks = new ArrayList<>();
                JsonArray jsonSinkArray = jsonObject.getAsJsonArray("sinks");
                jsonSinkArray.forEach(elem -> {
                    if (!elem.isJsonNull()) {
                        JsonObject jsonSinkElem = elem.getAsJsonObject();
                        Sink sink = new Sink();
                        sink.setType(Sink.Type.valueOf(jsonSinkElem.getAsJsonPrimitive("type").getAsString()));
                        sink.setConfiguration(getConfiguration(jsonSinkElem, metricName, componentName, "PullSensor.sinks[]"));
                        sinks.add(sink);
                    }
                });

                monitor.setSinks(sinks);
            }
        }

        log.debug("TranslationContextMonitorGsonDeserializer: OUTPUT: monitor={}", monitor);
        return monitor;
    }

    public Map<String, String> getConfiguration(JsonObject jsonObject, String metricName, String componentName, String field) {
        if (!jsonObject.has("configuration")) return null;
        if (jsonObject.get("configuration").isJsonNull()) return null;
        if (!jsonObject.get("configuration").isJsonObject())
            throw new JsonParseException("Monitor."+field+".configuration must be a map or null: "
                    + "metric=" + metricName + ", component=" + componentName);

        Map<String,String> configPairs = new LinkedHashMap<>();
        JsonObject jsonConfigMap = jsonObject.getAsJsonObject("configuration");
        jsonConfigMap.entrySet().forEach(entry -> {
            String key = entry.getKey();
            String val = null;
            JsonElement jsonElem = entry.getValue();

            if (StringUtils.isBlank(key))
                throw new JsonParseException("Monitor."+field+".configuration contains a blank key: "
                        + "metric=" + metricName + ", component=" + componentName);

            if (jsonElem.isJsonNull())
                val = null;
            else if (jsonElem.isJsonPrimitive())
                val = jsonElem.getAsString();
            else
                throw new JsonParseException("Monitor."+field+".configuration entry contains a non-string value: "
                        + "metric=" + metricName + ", component=" + componentName + ", configuration[].key=" + key);

            configPairs.put(key, val);
        });

        return configPairs;
    }

    public Map<String, Object> castToMapStringObject(Map<String,String> m) {
        return m.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue
        ));
    }

    public Interval getInterval(JsonObject jsonObject, String metricName, String componentName, String field) {
        if (!jsonObject.has("interval")) return null;
        if (jsonObject.get("interval").isJsonNull()) return null;
        if (!jsonObject.get("interval").isJsonObject())
            throw new JsonParseException("Monitor.Sensor."+field+".interval must be an object or null: "
                    + "metric=" + metricName + ", component=" + componentName);

        JsonObject jsonIntervalObject = jsonObject.getAsJsonObject("interval");
        JsonElement unitElem = jsonIntervalObject.get("unit");
        JsonElement periodElem = jsonIntervalObject.get("period");

        if (unitElem.isJsonNull())
            throw new JsonParseException("Monitor."+field+".interval.unit cannot be null: "
                    + "metric=" + metricName + ", component=" + componentName);
        if (!unitElem.isJsonPrimitive())
            throw new JsonParseException("Monitor."+field+".interval.unit must be a member of Interval.UnitType enum: "
                    + "metric=" + metricName + ", component=" + componentName);

        if (periodElem.isJsonNull())
            throw new JsonParseException("Monitor."+field+".interval.period cannot be null: "
                    + "metric=" + metricName + ", component=" + componentName);
        if (!periodElem.isJsonPrimitive())
            throw new JsonParseException("Monitor."+field+".interval.period must be an integer: "
                    + "metric=" + metricName + ", component=" + componentName);

        Interval interval = new Interval();
        interval.setUnit(Interval.UnitType.valueOf(unitElem.getAsString()));
        interval.setPeriod(periodElem.getAsInt());
        return interval;
    }
}
