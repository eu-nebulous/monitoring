/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Data
@Slf4j
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class EventMap extends LinkedHashMap<String, Object> implements Serializable {

    private static Gson gson = new GsonBuilder().create();
    private static AtomicLong eventIdSequence = new AtomicLong(0);

    // Standard/Known Event fields configuration
    @Data
    public static class EventField {
        private final String name;
        private final Class<?> type;
        private final boolean nullable;
        private final boolean skipIfNull;
        private final Function<String,Object> parser;
        private final Function<EventMap,Object> defaultValue;
    }

    public final static String METRIC_VALUE_NAME = "metricValue";
    public final static String LEVEL_NAME = "level";
    public final static String TIMESTAMP_NAME = "timestamp";

    private static final Pattern PROPERTY_NAME_INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9_]");

    public final static List<EventField> STANDARD_EVENT_FIELDS = Collections.unmodifiableList(Arrays.asList(
            new EventField(METRIC_VALUE_NAME, Double.class, false, false, Double::parseDouble, null),
            new EventField(LEVEL_NAME, Integer.class, true, true, (v)->(int)Double.parseDouble(v), null),
            new EventField(TIMESTAMP_NAME, Long.class, true, true, v->(long)Double.parseDouble(v), (f)->System.currentTimeMillis())
    ));

    public final static Map<String,EventField> STANDARD_EVENT_FIELDS_MAP = Collections.unmodifiableMap(
            STANDARD_EVENT_FIELDS.stream().collect(Collectors.toMap(EventField::getName, x->x)));

    public final static String[] PROPERTY_NAMES_ARRAY = STANDARD_EVENT_FIELDS.stream()
            .map(EventField::getName).toList().toArray(new String[0]);
    public final static Class<?>[] PROPERTY_CLASSES_ARRAY = STANDARD_EVENT_FIELDS.stream()
            .map(EventField::getType).toList().toArray(new Class[0]);

    public static String[] getPropertyNames() {
        return PROPERTY_NAMES_ARRAY;
    }

    public static Class<?>[] getPropertyClasses() {
        return PROPERTY_CLASSES_ARRAY;
    }


    // Event Id
    private final long eventId = eventIdSequence.getAndIncrement();

    // Event properties
    private Map<String,Object> eventProperties;

    public Object getEventProperty(@NonNull String name) {
        return eventProperties.get(name);
    }

    public synchronized Object setEventProperty(@NonNull String name, Object value) {
        if (eventProperties == null) eventProperties = new LinkedHashMap<>();
        return eventProperties.put(name, value);
    }

    // Constructors
    /*public EventMap() {
        super();
        put(TIMESTAMP_NAME, System.currentTimeMillis());
    }*/

    public EventMap(Map<String, Object> map) {
        checkEvent(map);
        map.forEach((k, v) -> {
            log.trace("EventMap.<init>: key={}, value={}", k, v);
            this.put(k, v);
        });
        if (map instanceof EventMap eventMap) {
            Map<String, Object> properties = eventMap.getEventProperties();
            if (properties!=null && ! properties.isEmpty())
                setEventProperties(new LinkedHashMap<>(properties));
        }
        checkEvent();
    }

    public EventMap(double metricValue) {
        put(METRIC_VALUE_NAME, metricValue);
        put(TIMESTAMP_NAME, System.currentTimeMillis());
        checkEvent();
    }

    public EventMap(double metricValue, long timestamp) {
        put(METRIC_VALUE_NAME, metricValue);
        put(TIMESTAMP_NAME, timestamp);
        checkEvent();
    }

    public EventMap(double metricValue, int level) {
        put(METRIC_VALUE_NAME, metricValue);
        put(LEVEL_NAME, level);
        put(TIMESTAMP_NAME, System.currentTimeMillis());
        checkEvent();
    }

    public EventMap(double metricValue, int level, long timestamp) {
        put(METRIC_VALUE_NAME, metricValue);
        put(LEVEL_NAME, level);
        put(TIMESTAMP_NAME, timestamp);
        checkEvent();
    }


    // Convert Object to EventMap
    public static EventMap toEventMap(@NonNull Object o) {
        if (o instanceof EventMap map) return map;
        if (o instanceof Map) return new EventMap( StrUtil.castToMapStringObject(o) );
        return parseEventMap(o.toString());
    }

    // Parse from string
    public static EventMap parseEventMap(@NonNull String s) {
        /*if (s==null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("{") && s.endsWith("}")) s = s.substring(1, s.length()-1).trim();
        String[] pairs = s.split(",");
        EventMap eventMap = new EventMap();
        for (String pair : pairs) {
            if (StringUtils.isBlank(pair))
                continue;
            String[] kv = pair.split("[:=]", 2);
            if (kv.length==2)
                eventMap.put(kv[0], kv[1]);
            else
                eventMap.put(kv[0], null);
        }
        return eventMap;
        */
        EventMap eventMap = gson.fromJson(s, EventMap.class);
        eventMap.checkEvent();
        return eventMap;
    }

    public static Map parseMap(@NonNull String s) {
        Map map = gson.fromJson(s, Map.class);
        Object ts = map.get(TIMESTAMP_NAME);
        if (ts instanceof Double d) map.put(TIMESTAMP_NAME, d.longValue());
        return map;
    }

    public void checkEvent() {
        checkEvent(this);
    }

    public static void checkEvent(Map<String, Object> map) {
        // Check metric value
        Object m = map.get(METRIC_VALUE_NAME);
        if (m==null) throw new IllegalArgumentException("Argument does not contain a 'metricValue'");
        if (!(m instanceof Number n)) throw new IllegalArgumentException("Argument contains a non-numeric 'metricValue' : "+m);
        else {
            double d = n.doubleValue();
            if (Double.isInfinite(d) || Double.isNaN(d)) throw new IllegalArgumentException("Argument contains NaN or Infinite 'metricValue' : "+m);
        }
        // Check level value
        // Check timestamp value
    }


    // Methods overridden
    @Override
    public Object put(String key, Object value) {
        log.trace("EventMap.put(): BEGIN: key={}, value={}", key, value);
        key = removeQuotes(key);
        log.trace("EventMap.put(): KEY with Quotes Stripped: key={}", key);

        // Process known (standard) event fields
        EventField field = STANDARD_EVENT_FIELDS_MAP.get(key);
        if (field!=null) {
            log.trace("EventMap.put(): STANDARD_EVENT_FIELD: key={}, value={}", key, value);
            if (value==null) {
                if (!field.isNullable())
                    throw new NullPointerException("Event field cannot be null: " + key);
                if (field.isSkipIfNull()) return null;
                value = field.getDefaultValue().apply(this);
                log.debug("EventMap.put(): Assigned default value to: key={}, value={}", key, value);
            }
            if (!field.getType().isInstance(value)) {
                log.trace("EventMap.put(): Value type is different than Event field type: key={}, value={}, value-type={}, field-type={}",
                        key, value, value.getClass().getName(), field.getType().getName());
                value = field.getParser().apply(removeQuotes(value));
                log.debug("EventMap.put(): Value after parsing: key={}, value={}, value-type={}, field-type={}",
                        key, value, value.getClass().getName(), field.getType().getName());
            }
        }

        log.trace("EventMap.put(): Putting in EventMap: key={}, value={}", key, value);
        return super.put(key, value);
    }

    protected static String removeQuotes(@NonNull Object o) {
        String s = o.toString();
        int l = s.length()-1;
        s = (s.charAt(0)=='"' && s.charAt(l)=='"' || s.charAt(0)=='\'' && s.charAt(l)=='\'')
                ? s.substring(1, l) : s;
        log.trace("EventMap.removeQuotes(): INPUT={}, RESULT={}", o, s);
        return s;
    }

    // Getters for standard event fields
    public double getMetricValue() {
        Object v = get(METRIC_VALUE_NAME);
        if (v==null)
            throw new NullPointerException("No '"+METRIC_VALUE_NAME+"' found in EventMap: "+this);
        if (v instanceof Double doubleValue) return doubleValue;
        if (v instanceof Number numberValue) return numberValue.doubleValue();
        return Double.parseDouble(removeQuotes(v));
    }

    public long getTimestamp() {
        Object v = get(TIMESTAMP_NAME);
        if (v==null)
            throw new NullPointerException("No '"+TIMESTAMP_NAME+"' found in EventMap: "+this);
        if (v instanceof Long longValue) return longValue;
        if (v instanceof Number numberValue) return numberValue.longValue();
        return Long.parseLong(removeQuotes(v));
    }

    public Map<String,Object> getPayload() {
        return new LinkedHashMap<>(this);
    }

    public Map<String,String> getEventPropertiesAsStringMap(boolean normalizeKeys) {
        return getEventProperties().entrySet().stream().collect(Collectors.toMap(
                x-> normalizeKeys ? normalizeKey(x.getKey()) : x.getKey(),
                x->x!=null ? x.getValue().toString() : null
        ));
    }

    private String normalizeKey(String key) {
        if (key==null || key.isBlank()) return key;
        String sanitized = PROPERTY_NAME_INVALID_CHARS.matcher(key).replaceAll("_");
        if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    public String toString() {
        return getEventProperties()!=null
                ? "{ payload: "+super.toString() + ", properties: " + getEventProperties().toString() + " }"
                : super.toString();
    }

    public String toJsonString() {
        return gson.toJson(this);
    }
}