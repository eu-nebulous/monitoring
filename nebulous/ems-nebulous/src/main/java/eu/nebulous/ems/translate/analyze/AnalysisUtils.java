/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import eu.nebulous.ems.translate.ModelException;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisUtils implements InitializingBean {
    final static String CONTAINER_NAME_KEY = "_containerName";

    final static List<String> COMPARISON_OPERATORS =
            List.of("<", "<=", "=<", ">", ">=", "=<", "=", "<>", "!=");
    final static List<String> LOGICAL_OPERATORS =
            List.of("and", "or");
    final static String NOT_OPERATOR = "not";

    private static boolean useCompositeNames = true;

    private final NebulousEmsTranslatorProperties properties;

    @Override
    public void afterPropertiesSet() throws Exception {
        useCompositeNames = properties.isUseCompositeNames();
        log.info("AnalysisUtils: useCompositeNames: {}", useCompositeNames);
    }

    // ------------------------------------------------------------------------
    //  Exceptions and Casting method
    // ------------------------------------------------------------------------

    static RuntimeException createException(String s) {
        log.error("Parse error: {}", s);
        return new RuntimeException(new ModelException(s));
    }

    static RuntimeException createException(String s, Throwable t) {
        log.error("Parse error: {}: ", s, t);
        return new RuntimeException(s, t);
    }

    static List<Object> asList(Object o) {
        if (o==null) return null;
        if (o instanceof List l) return l;
        log.error("AnalysisUtils.asList: input: {}", o);
        throw createException("Object is not a List: "+o);
    }

    static Map<String, Object> asMap(Object o) {
        if (o==null) return null;
        if (o instanceof Map m) return m;
        log.error("AnalysisUtils.asMap: input: {}", o);
        throw createException("Object is not a Map: "+o);
    }

    static boolean isList(Object o) {
        return (o instanceof List);
    }

    static boolean isMap(Object o) {
        return (o instanceof Map);
    }

    static boolean isNullOrEmpty(Object o) {
        if (o instanceof Collection c)
            return isNullOrEmpty(c);
        if (o instanceof Map m)
            return isNullOrEmpty(m);
        return (o==null);
    }

    static boolean isNullOrEmpty(Collection c) {
        return (c==null || c.isEmpty());
    }

    static boolean isNullOrEmpty(Map m) {
        return (m==null || m.isEmpty());
    }

    // ------------------------------------------------------------------------
    //  Name and Field methods
    // ------------------------------------------------------------------------

    static NamesKey createNamesKey(@NonNull NamesKey parentNamesKey, @NonNull String name) {
        if (useCompositeNames)
            return (NamesKey.isFullName(name))
                    ? NamesKey.create(name) : NamesKey.create(parentNamesKey.parent, name);
        else
            return NamesKey.create(null, name);
    }

    static NamesKey createNamesKey(@NonNull String parentName, @NonNull String name) {
        if (useCompositeNames)
            return NamesKey.create(parentName, name);
        else
            return NamesKey.create(null, name);
    }

    static NamesKey createNamesKey2(String parentName, @NonNull String name) {
        if (useCompositeNames)
            return NamesKey.create(parentName, name);
        else
            return NamesKey.create(null, name);
    }

    static NamesKey createNamesKey(@NonNull String name) {
        if (useCompositeNames)
            return NamesKey.create(name);
        else
            return NamesKey.create(null, name);
    }

    static NamesKey getNamesKey(@NonNull Object spec, @NonNull String name) {
        return createNamesKey(getContainerName(spec), name);
    }

    static String getContainerName(@NonNull Object spec) {
        return getSpecField(spec, CONTAINER_NAME_KEY);
    }

    static String getSpecField(Object o, String field) {
        return getSpecField(o, field, "Block '%s' is not String: ");
    }

    static String getSpecField(Object o, String field, String exceptionMessage) {
        try {
            Map<String, Object> spec = asMap(o);
            Object oValue = spec.get(field);
            if (oValue == null)
                return null;
            if (oValue instanceof String s) {
                s = s.trim();
                return s;
            }
            throw createException(exceptionMessage.formatted(field) + spec);
        } catch (Exception e) {
            throw createException(exceptionMessage.formatted(field) + o, e);
        }
    }

    static String getMandatorySpecField(Object o, String field, String exceptionMessage) {
        String val = getSpecField(o, field, exceptionMessage);
        if (val==null)
            throw createException(exceptionMessage.formatted(field) + o);
        return val;
    }

    static Object getSpecFieldAsObject(Object o, String field) {
        return getSpecFieldAsObject(o, field, "Block '%s' is not String or Map: ");
    }

    static Object getSpecFieldAsObject(Object o, String field, String exceptionMessage) {
        try {
            Map<String, Object> spec = asMap(o);
            Object oValue = spec.get(field);
            if (oValue == null)
                return null;
            if (oValue instanceof String || oValue instanceof Map)
                return oValue;
            throw createException(exceptionMessage.formatted(field) + spec);
        } catch (Exception e) {
            throw createException(exceptionMessage.formatted(field) + o, e);
        }
    }

    static Object getMandatorySpecFieldAsObject(Object o, String field, String exceptionMessage) {
        Object val = getSpecFieldAsObject(o, field, exceptionMessage);
        if (val==null)
            throw createException(exceptionMessage.formatted(field) + o);
        return val;
    }

    static List<Object> getSpecFieldAsList(Object o, String field) {
        return getSpecFieldAsList(o, field, "Block '%s' is not List: ");
    }

    static List<Object> getSpecFieldAsList(Object o, String field, String exceptionMessage) {
        try {
            Map<String, Object> spec = asMap(o);
            Object oValue = spec.get(field);
            if (oValue == null)
                return null;
            if (oValue instanceof String)
                return List.of(oValue);
            if (oValue instanceof List list)
                return list;
            throw createException(exceptionMessage.formatted(field) + spec);
        } catch (Exception e) {
            throw createException(exceptionMessage.formatted(field) + o, e);
        }
    }

    static List<Object> getMandatorySpecFieldAsList(Object o, String field, String exceptionMessage) {
        List<Object>  val = getSpecFieldAsList(o, field, exceptionMessage);
        if (val==null)
            throw createException(exceptionMessage.formatted(field) + o);
        return val;
    }

    static Map<String,Object>  getSpecFieldAsMap(Object o, String field) {
        return getSpecFieldAsMap(o, field, "Block '%s' is not Map: ");
    }

    static Map<String,Object> getSpecFieldAsMap(Object o, String field, String exceptionMessage) {
        try {
            Map<String, Object> spec = asMap(o);
            Object oValue = spec.get(field);
            if (oValue == null)
                return null;
            if (oValue instanceof Map map)
                return map;
            throw createException(exceptionMessage.formatted(field) + spec);
        } catch (Exception e) {
            throw createException(exceptionMessage.formatted(field) + o, e);
        }
    }

    static Map<String,Object> getMandatorySpecFieldAsMap(Object o, String field, String exceptionMessage) {
        Map<String,Object> val = getSpecFieldAsMap(o, field, exceptionMessage);
        if (val==null)
            throw createException(exceptionMessage.formatted(field) + o);
        return val;
    }

    static String getSpecName(Object o) {
        return getSpecField(o, "name");
    }

    static Double getSpecNumber(Object o, String field) {
        try {
            Map<String, Object> spec = asMap(o);
            Object oValue = spec.get(field);
            if (oValue == null) return null;
            if (oValue instanceof Number n) {
                return n.doubleValue();
            }
            if (oValue instanceof String s) {
                return Double.parseDouble(s);
            }
            throw createException("Block '"+field+"' is not Number: " + spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Double getSpecNumber(Object o, String field, String exceptionMessage) {
        Double val = getSpecNumber(o, field);
        if (val==null)
            throw createException(exceptionMessage+o);
        return val;
    }

    static Boolean getSpecBoolean(Object o, String field) {
        try {
            Map<String, Object> spec = asMap(o);
            Object oValue = spec.get(field);
            if (oValue == null) return null;
            if (oValue instanceof Boolean b) {
                return b;
            }
            if (oValue instanceof String s) {
                return getBooleanValue(s, false);
            }
            throw createException("Block '"+field+"' is not Boolean: " + spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Boolean getSpecBoolean(Object o, String field, boolean defaultValue) {
        Boolean val = getSpecBoolean(o, field);
        return val!=null ? val : defaultValue;
    }

    static Boolean getSpecBoolean(Object o, String field, String exceptionMessage) {
        Boolean val = getSpecBoolean(o, field);
        if (val==null)
            throw createException(exceptionMessage+o);
        return val;
    }

    static boolean getBooleanValue(String val, boolean defaultValue) {
        if (StringUtils.isBlank(val)) return defaultValue;
        return "true".equalsIgnoreCase(val.trim());
    }

    // ------------------------------------------------------------------------
    //  Unit normalization methods
    // ------------------------------------------------------------------------

    public static ChronoUnit normalizeTimeUnit(String s) {
        s = s.trim().toLowerCase();
        return switch (s) {
            case "ms", "msec", "milli", "millis", "millisecond", "milliseconds" -> ChronoUnit.MILLIS;
            case "s", "sec", "second", "seconds" -> ChronoUnit.SECONDS;
            case "m", "min", "minute", "minutes" -> ChronoUnit.MINUTES;
            case "h", "hr", "hrs", "hour", "hours" -> ChronoUnit.HOURS;
            case "d", "day", "days" -> ChronoUnit.DAYS;
            case "w", "week", "weeks" -> ChronoUnit.WEEKS;
            case "mon", "month", "months" -> ChronoUnit.MONTHS;
            case "yr", "year", "years" -> ChronoUnit.YEARS;
            default -> throw createException("Not supported time unit: "+s);
        };
    }

    // ------------------------------------------------------------------------
    //  Query methods
    // ------------------------------------------------------------------------

    static boolean isDouble(String s) {
        try {
            Double.parseDouble(s);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    static boolean isDoubleOrInfinity(String s) {
        s = s.trim();
        if (StringUtils.equalsAnyIgnoreCase(s, "-inf", "+inf", "inf")) return true;
        return isDouble(s);
    }

    static double getDoubleValue(String s) {
        s = s.trim();
        return switch (s.toLowerCase()) {
            case "-inf" -> Double.NEGATIVE_INFINITY;
            case "+inf", "inf" -> Double.POSITIVE_INFINITY;
            default -> Double.parseDouble(s);
        };
    }

    static boolean isComparisonOperator(String s) {
        return COMPARISON_OPERATORS.contains(s);
    }

    static boolean isLogicalOperator(String s) {
        return LOGICAL_OPERATORS.contains(s);
    }

    static boolean isNotOperator(String s) {
        return NOT_OPERATOR.equalsIgnoreCase(s);
    }

}