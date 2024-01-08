/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class StrUtil {

    // ------------------------------------------------------------------------
    // Key variations methods
    // ------------------------------------------------------------------------

    private final static Pattern pUppercase = Pattern.compile("(?=\\p{Lu})");
    private final static Pattern pDelims = Pattern.compile("[\\.\\-_ ]");

    public static Set<String> getVariations(@NonNull String s) {
        return getVariations(s, false, null);
    }

    public static Set<String> getVariations(@NonNull String s, boolean alsoSplitOnUppercase) {
        return getVariations(s, alsoSplitOnUppercase, null);
    }

    public static Set<String> getVariations(@NonNull String s, String delims) {
        return getVariations(s, false, delims);
    }

    public static Set<String> getVariations(@NonNull String str, boolean alsoSplitOnUppercase, String delims) {
        log.trace("StrUtil: getVariations: BEGIN: str={}, also-split-on-uppercase={}, delimiters={}", str, alsoSplitOnUppercase, delims);
        // Split input string into separate words
        List<String> words = _splitToWords(str, alsoSplitOnUppercase, delims);

        // Create variations
        Set<String> variations = new LinkedHashSet<>(Arrays.asList(
                // All letters plain separated with underscores
                String.join("_", words),
                // All letters capital separated with underscores
                words.stream().map(String::toUpperCase).collect(Collectors.joining("_")),
                // Title case
                words.stream().map(StringUtils::capitalize).collect(Collectors.joining()),
                // Camel case
                StringUtils.uncapitalize(words.stream().map(StringUtils::capitalize).collect(Collectors.joining())),
                // All letters lower case separated with periods
                String.join(".", words),
                // All letters lower case separated with dashes
                String.join("-", words)
        ));
        log.trace("StrUtil: getVariations: END: variations: {}", variations);
        return variations;
    }

    public static String getNormalizedForm(@NonNull String str, boolean alsoSplitOnUppercase) {
        return getNormalizedForm(str, alsoSplitOnUppercase, null);
    }

    public static String getNormalizedForm(@NonNull String str, boolean alsoSplitOnUppercase, String delims) {
        log.trace("StrUtil: getNormalizedForm: BEGIN: str={}, also-split-on-uppercase={}, delimiters={}", str, alsoSplitOnUppercase, delims);
        // Split input string into separate words
        List<String> words = _splitToWords(str, alsoSplitOnUppercase, delims);

        // Create normalized form
        String normalizedForm = words.stream().map(String::toUpperCase).collect(Collectors.joining("_"));
        log.trace("StrUtil: getNormalizedForm: END: normalized form: {}", normalizedForm);
        return normalizedForm;
    }

    private static List<String> _splitToWords(String str, boolean alsoSplitOnUppercase, String delims) {
        log.trace("StrUtil: _splitToWords: BEGIN: str={}, also-split-on-uppercase={}, delimiters={}", str, alsoSplitOnUppercase, delims);
        Pattern _delims = StringUtils.isNotBlank(delims)
                ? Pattern.compile(delims)
                : pDelims;
        log.trace("StrUtil: _splitToWords: Effective delimiters={}", _delims);

        List<String> words =
                (alsoSplitOnUppercase
                        ? pUppercase.splitAsStream(str)
                                .filter(StringUtils::isNotBlank)
                                .flatMap(_delims::splitAsStream)
                        : _delims.splitAsStream(str)
                )
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        log.trace("StrUtil: _splitToWords: END: words: {}", words);
        return words;
    }

    // ------------------------------------------------------------------------
    // Get Map values using key variations
    // ------------------------------------------------------------------------

    public static String getWithVariations(@NonNull Map<String, String> configuration, @NonNull String key) {
        return getWithVariations(configuration, key, null);
    }

    public static String getWithVariations(@NonNull Map<String, String> configuration, @NonNull String key, String defaultValue) {
        log.trace("StrUtil: getWithVariations: BEGIN: key={}, default={}, map={}", key, defaultValue, configuration);

        // Create key variations
        Set<String> variations = StrUtil.getVariations(key, true);
        variations.add(key);
        log.trace("StrUtil: getWithVariations: variations={}", variations);

        // Search for value
        for (String k : variations) {
            if (configuration.containsKey(k)) {
                log.trace("StrUtil: getWithVariations: Variation matched: name={}, value={}", k, configuration.get(k));
                return configuration.get(k);
            }
        }
        log.trace("StrUtil: getWithVariations: No variations matched. Returning default: {}", defaultValue);
        return defaultValue;
    }

    public static String getWithNormalized(@NonNull Map<String, String> configuration, @NonNull String key) {
        return getWithNormalized(configuration, key, null);
    }

    public static String getWithNormalized(@NonNull Map<String, String> configuration, @NonNull String key, String defaultValue) {
        log.trace("StrUtil: getWithNormalized: BEGIN: key={}, default={}, map={}", key, defaultValue, configuration);

        // Normalize key
        String normalizedForm = StrUtil.getNormalizedForm(key, true);
        log.trace("StrUtil: getWithNormalized: Normalized key form: {}", normalizedForm);

        // Search for value
        for (String k : configuration.keySet()) {
            String normalizedKey = StrUtil.getNormalizedForm(k, true);
            if (normalizedForm.equals(normalizedKey)) {
                log.trace("StrUtil: getWithNormalized: Key matched: name={}, value={}", k, configuration.get(k));
                return configuration.get(k);
            }
        }
        log.trace("StrUtil: getWithNormalized: key not found. Returning default: {}", defaultValue);
        return defaultValue;
    }

    public static boolean compareNormalized(@NonNull String key1, @NonNull String key2) {
        log.trace("StrUtil: compareNormalized: BEGIN: key1={}, key2={}", key1, key2);
        if (key1.equals(key2)) {
            log.trace("StrUtil: compareNormalized: END: Original keys are equal");
            return true;
        }

        // Normalized keys
        String normalizedKey1 = StrUtil.getNormalizedForm(key1, true);
        String normalizedKey2 = StrUtil.getNormalizedForm(key2, true);
        log.trace("StrUtil: compareNormalized: Normalized keys: key1={}, key2={}", key1, key2);

        // Compare keys
        boolean areEqual = normalizedKey1.equals(normalizedKey2);
        log.trace("StrUtil: compareNormalized: END: Normalized keys are {}", areEqual ? "EQUAL" : "NOT EQUAL");
        return areEqual;
    }

    // ------------------------------------------------------------------------
    // Convert string to primitives
    // ------------------------------------------------------------------------

    protected static class StrConverter<T> {
        public T convert(String str, T defaultValue, Function<String,T> converter, Predicate<T> checker, boolean throwException, String exceptionMessage) {
            T result = defaultValue;
            if (StringUtils.isNotBlank(str)) {
                try {
                    result = converter.apply(str.trim());
                    if (checker!=null && ! checker.test(result)) {
                        if (throwException)
                            throw new IllegalArgumentException("Value check failed: str="+str);
                        log.warn("StrConverter: Value check failed. Default value will be returned: str={}, default={}", str, defaultValue);
                        result = defaultValue;
                    }
                } catch (Exception e) {
                    if (throwException)
                        throw new IllegalArgumentException("Invalid value: str="+str, e);
                    String formatter = exceptionMessage;
                    if (StringUtils.isBlank(exceptionMessage)) {
                        String typeName;
                        if (result!=null) {
                            typeName = result.getClass().getSimpleName();
                        } else {
                            /*List<T> dummy = new ArrayList<>(0);
                            Type[] actualTypeArguments = ((ParameterizedType) dummy.getClass().getGenericSuperclass()).getActualTypeArguments();
                            Type clazz = actualTypeArguments[0];
                            Class<T> theClass = (Class<T>) clazz.getClass();
                            typeName = theClass.getSimpleName();
                            */
                            typeName = "unknown_type";
                        }
                        formatter = "StrConverter: Invalid %s value: str=%s, Exception: ".formatted(typeName, str);
                    }
                    log.warn(formatter, e);
                }
            }
            return result;
        }
    }

    protected final static StrConverter<Integer> strToIntConverter = new StrConverter<>();
    protected final static StrConverter<Long> strToLongConverter = new StrConverter<>();
    protected final static StrConverter<Double> strToDoubleConverter = new StrConverter<>();

    public static int strToInt(String str, int defaultValue, Predicate<Integer> checker, boolean throwException, String exceptionMessage) {
        return strToIntConverter.convert(str, defaultValue, Integer::parseInt, checker, throwException, exceptionMessage);
    }

    public static long strToLong(String str, long defaultValue, Predicate<Long> checker, boolean throwException, String exceptionMessage) {
        return strToLongConverter.convert(str, defaultValue, Long::parseLong, checker, throwException, exceptionMessage);
    }

    public static double strToDouble(String str, double defaultValue, Predicate<Double> checker, boolean throwException, String exceptionMessage) {
        return strToDoubleConverter.convert(str, defaultValue, Double::parseDouble, checker, throwException, exceptionMessage);
    }

    public static <T extends Enum<T>> T strToEnum(String str, Class<T> enumType, T defaultValue, boolean throwException, String exceptionMessage) {
        String formatter = StringUtils.isNotBlank(exceptionMessage)
                ? exceptionMessage : "strToEnum: Invalid enum "+enumType.getSimpleName()+" value: str={}, Exception: ";
        StrConverter<T> converter = new StrConverter<>();
        return converter.convert(str, defaultValue, (s)->Enum.valueOf(enumType, s), null, throwException, formatter);
    }

    // ------------------------------------------------------------------------
    // Convert Exceptions to details string
    // ------------------------------------------------------------------------

    public static String exceptionToDetailsString(Throwable t) {
        return exceptionToDetailsString(t, true, true, false, "; ", ": ");
    }

    public static String exceptionToDetailsString(Throwable t, boolean printRootCauseFirst) {
        return exceptionToDetailsString(t, true, true, printRootCauseFirst, "; ", ": ");
    }

    public static String exceptionToDetailsString(Throwable t,
                                                  boolean printExceptionClass,
                                                  boolean printExceptionMessage,
                                                  boolean printRootCauseFirst,
                                                  String exceptionDelimiter,
                                                  String messageDelimiter)
    {
        if (!printExceptionClass && !printExceptionMessage)
            return null;

        StringBuilder s = new StringBuilder();
        String _m = t.getMessage();
        String _d = null;
        if (printExceptionMessage && StringUtils.isNotBlank(_m))
            s.append(_d = _m);
        if (printExceptionClass)
            s.insert(0, _d == null ? "" : messageDelimiter).insert(0, t.getClass().getName());

        Throwable _t = t.getCause();
        //if (_t==null) return null;
        if (printRootCauseFirst) {
            while (_t != null) {
                _m = _t.getMessage();
                _d = null;
                s.insert(0, exceptionDelimiter);
                if (printExceptionMessage && StringUtils.isNotBlank(_m))
                    s.insert(0, _d = _m);
                if (printExceptionClass)
                    s.insert(0, _d == null ? "" : messageDelimiter).insert(0, _t.getClass().getName());
                _t = _t.getCause();
            }
        } else {
            while (_t != null) {
                _m = _t.getMessage();
                _d = null;
                s.append(exceptionDelimiter);
                if (printExceptionClass)
                    s.append(_d = _t.getClass().getName());
                if (printExceptionMessage && StringUtils.isNotBlank(_m))
                    s.append(_d==null ? "" : messageDelimiter).append(_m);
                _t = _t.getCause();
            }
        }
        return s.toString();
    }

    // ------------------------------------------------------------------------
    // Object Map-to-String Map conversion methods
    // ------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String,Object> castToMapStringObject(Object o) {
        return (Map<String,Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static EventBus<String,Object,Object> castToEventBusStringObjectObject(Object o) {
        return (EventBus<String,Object,Object>) o;
    }

    public static Map<String,Object> deepStringifyMap(Map<String,Object> inputMap) {
        Map<String,Object> outMap = new LinkedHashMap<>();
        for (Map.Entry<String,Object> entry : inputMap.entrySet()) {
            if (entry.getValue()!=null && entry.getValue() instanceof Map) {
                Map<String,Object> tmpMap = deepStringifyMap(castToMapStringObject(entry.getValue()));
                outMap.put(entry.getKey(), tmpMap);
            } else {
                outMap.put(entry.getKey(), entry.getValue()!=null ? entry.getValue().toString() : null);
            }
        }
        return outMap;
    }

    public static Map<String,String> deepFlattenMap(Map<String,Object> inputMap) {
        return deepFlattenMap(inputMap, "");
    }

    public static Map<String,String> deepFlattenMap(Map<String,Object> inputMap, String prefix) {
        if (inputMap==null)
            return Collections.emptyMap();
        Map<String,String> outMap = new LinkedHashMap<>();
        for (Map.Entry<String,Object> entry : inputMap.entrySet()) {
            String newKey = prefix.isEmpty()
                    ? entry.getKey()
                    : (entry.getKey()!=null) ? prefix+"."+entry.getKey() : prefix;
            if (entry.getValue()!=null && entry.getValue() instanceof Map) {
                Map<String, String> tmpMap = deepFlattenMap(castToMapStringObject(entry.getValue()), newKey);
                outMap.putAll(tmpMap);
            } else {
                outMap.put(newKey, entry.getValue()!=null ? entry.getValue().toString() : null);
            }
        }
        return outMap;
    }

    // ------------------------------------------------------------------------
    // Main for command-line use
    // ------------------------------------------------------------------------

    public static void main(String[] args) {
        boolean uc = false;
        String key = null;
        String delims = null;

        for (String s : args) {
            if ("-u".equals(s)) uc = true;
            else if (s.startsWith("-D")) delims = s.substring(2);
            else key = s;
        }

        Set<String> v;
        if (uc) {
            if (delims!=null) v = getVariations(key, uc, delims);
            else v = getVariations(key, uc);
        } else {
            if (delims!=null) v = getVariations(key, delims);
            else v = getVariations(key);
        }

        System.out.println("> "+v);
        // with Original key
        v.add(key);
        System.out.println("> "+v);
    }
}
