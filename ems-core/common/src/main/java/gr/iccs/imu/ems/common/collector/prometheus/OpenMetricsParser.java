/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.collector.prometheus;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Parses OpenMetrics-formatted input
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenMetricsParser {

    public static void main(String[] args) throws IOException {
        OpenMetricsParser parser = new OpenMetricsParser();
        for (String file : args) {
            log.info("Processing file: {}", file);
            List<String> lines = Files.readAllLines(Paths.get(file));
            List<MetricInstance> metricInstances = parser.processInput(lines.toArray(new String[0]));
            log.info("Results:\n{}", metricInstances);
        }
    }

    private boolean throwExceptionWhenExcessiveCharsOccur;

    public List<MetricInstance> processInput(String[] lines) {
        LinkedHashMap<String,String> tags = new LinkedHashMap<>();

        ProcessingContext context = new ProcessingContext();
        List<MetricInstance> results = new ArrayList<>();
        for (String line : lines) {
            log.debug("OpenMetricsParser: processInput: Looping...: line: {}", line);
            line = line.trim();
            if (line.isEmpty()) {
                log.trace("OpenMetricsParser: Skip blank line");
                continue;
            }

            MetricInstance metricInstance = processLine(line, tags, context);
            if (metricInstance!=null)
                results.add( metricInstance );
        }
        return results;
    }

    public MetricInstance processLine(@NonNull String line) {
        return processLine(line, new LinkedHashMap<>(), null);
    }

    public MetricInstance processLine(@NonNull String line, @NonNull Map<String,String> tags, ProcessingContext context) {
        try {
            if (line.charAt(0) == '#') {
                line = line.substring(1).trim();
                String[] part = line.split(" ", 2);

                if ("HELP".equalsIgnoreCase(part[0])) {
                    log.debug("OpenMetricsParser: processLine: Found HELP line: {}", line);    // Ignore HELP line
                    if (part.length<2)
                        throw new MalformedMetricLineException("HELP line is malformed: "+line);
                    part = part[1].split("[ \t\r]+", 2);
                    if (StringUtils.isBlank(part[0]))
                        throw new MalformedMetricLineException("HELP line is malformed: "+line);
                    String newMetricName = part[0].trim();
                    String helpText = part.length>1 ? part[1] : null;
                    if (context.getMetricHelpTexts().containsKey(newMetricName))
                        throw new MalformedMetricLineException("HELP for metric has already been set: " + newMetricName);
                    context.getMetricHelpTexts().put(newMetricName, processHelpText(helpText));

                } else if ("TYPE".equalsIgnoreCase(part[0])) {
                    log.debug("OpenMetricsParser: processLine: Found TYPE line: {}", line);    // Ignore TYPE line
                    if (part.length<2)
                        throw new MalformedMetricLineException("TYPE line is malformed: "+line);
                    part = part[1].split("[ \t\r]+");
                    if (part.length!=2)
                        throw new MalformedMetricLineException("TYPE line is malformed: "+line);
                    if (StringUtils.isBlank(part[0]))
                        throw new MalformedMetricLineException("TYPE line is malformed: "+line);
                    String newMetricName = part[0].trim();
                    METRIC_TYPE newMetricType = METRIC_TYPE.valueOf(part[1].trim().toUpperCase());
                    if (context.getMetricTypes().containsKey(newMetricName))
                        throw new MalformedMetricLineException("TYPE for metric has already been set: " + newMetricName);
                    context.getMetricTypes().put(newMetricName, newMetricType);
                } else
                    log.debug("OpenMetricsParser: processLine: Found comment line: {}", line);    // Ignore comment

                return null;

            } else {
                log.debug("OpenMetricsParser: processLine: Found metric line: {}", line);

                // init line processing
                int i = 0;
                int lineLength = line.length();
                tags.clear();

                // get metric name
                String metricName = getIdentifier(line, i);
                log.trace("OpenMetricsParser: processLine:     metricName: {}", metricName);
                i += metricName.length();
                i = skipWhites(line, i);

                // check for tag list opening ('{')
                if (line.charAt(i)=='{') {
                    // tag list found... skip white chars
                    i = skipWhites(line, i+1);

                    // process tags...
                    while (true) {
                        // get tag name
                        String tagName = getIdentifier(line, i);
                        log.trace("OpenMetricsParser: processLine:    tagName: {}", tagName);
                        i += tagName.length();
                        i = skipWhites(line, i);

                        if (line.charAt(i)!='=')
                            throw new MalformedMetricLineException("Expected '=' after tag name");
                        i = skipWhites(line, i+1);

                        // get tag value
                        String tagValue = getTagValue(line, i);
                        i += tagValue.length();
                        i++;    // skip tag value closing quote
                        i = skipWhites(line, i+1);
                        tagValue = processEscapeSequences(tagValue);
                        log.trace("OpenMetricsParser: processLine:    tagValue: {}", tagValue);

                        if (i==lineLength)
                            throw new MalformedMetricLineException("Line end reached. Tag list not closed after last tag value");

                        // check for a comma following tag value
                        boolean commaFound = false;
                        if (line.charAt(i)==',') {
                            commaFound = true;
                            i = skipWhites(line, i+1);
                            if (i==lineLength)
                                throw new MalformedMetricLineException("Line end reached. Tag list not closed after last comma");
                        }

                        // add tag pair in tags map
                        log.trace("OpenMetricsParser: processLine:    tag pair: {} = {}", tagName, tagValue);
                        tags.put(tagName, tagValue);

                        // check for tag list closing
                        if (line.charAt(i)=='}') {
                            i = skipWhites(line, i+1);
                            break;
                        } else if (!commaFound)
                            throw new MalformedMetricLineException("Expected ',' or '}' after tag value");
                        else
                            ; // repeat
                    }
                }
                if (i==lineLength)
                    throw new MalformedMetricLineException("Line end reached. No metric value found after tag list");

                // get metric value
                String valueStr = getNonWhites(line, i);
                log.trace("OpenMetricsParser:    metricValue: {}", valueStr);
                if (valueStr.isEmpty())
                    throw new MalformedMetricLineException("No valid metric value found");
                i += valueStr.length();

                // check for (optional) timestamp
                String tmStr = null;
                if (i<lineLength && isWhite(line.charAt(i))) {
                    i = skipWhites(line, i);

                    // get timestamp
                    tmStr = getNonWhites(line, i);
                    log.trace("OpenMetricsParser: processLine:    tmStr: {}", tmStr);
                    i += tmStr.length();
                }

                // check for excessive chars
                if (i<lineLength && isWhite(line.charAt(i))) {
                    // Report excessive chars
                    String excessive = line.substring(i);
                    log.warn("OpenMetricsParser: processLine:    Excessive chars are ignored: {}", excessive);

                    if (throwExceptionWhenExcessiveCharsOccur)
                        throw new MalformedMetricLineException("Excessive characters found in input line: "+line);
                }

                // process collected metric data
                return createMetricInstance(metricName, valueStr, tmStr, tags, context);
            }
        } catch (Exception e) {
            log.warn("OpenMetricsParser: processLine: Malformed line: ", e);
            throw new MalformedMetricLineException(e);
        }
    }

    protected int skipWhites(String line, int i) {
        int lineLength = line.length();
        while (i<lineLength && isWhite(line.charAt(i))) i++;
        return i;
    }

    protected String processHelpText(String helpText) {
        return helpText==null ? null : helpText.replaceAll("\\n", "\n").replaceAll("\\\\", "\\");
    }

    protected String getIdentifier(String line, int i) {
        int start = i;
        int lineLength = line.length();
        while (i<lineLength && isAlphanumeric(line.charAt(i), i>start)) i++;
        String identifier = line.substring(start, i);
        if (identifier.isEmpty()) throw new MalformedMetricLineException("No valid identifier found");
        return identifier;
    }

    protected String getTagValue(String line, int i) {
        int start = i;
        int lineLength = line.length();

        // check for tag value opening quote (")
        if (line.charAt(i)!='\"')
            throw new MalformedMetricLineException("Expected '\"' to open tag value");
        i++;

        // read tag value (chars until first unescaped quote)
        while (i<lineLength && (line.charAt(i)!='\"')) {
            i++;
            if (line.charAt(i)=='\\') {
                if (i==lineLength)
                    throw new MalformedMetricLineException("Tag value not closed");
                if (line.charAt(i)=='\"' || line.charAt(i)=='t' || line.charAt(i)=='r') i++;
                else
                    throw new MalformedMetricLineException("Invalid escape sequence in tag value");
            }
        }
        // check if line ended before tag closing quote
        if (i==lineLength)
            throw new MalformedMetricLineException("Tag value not closed");

        // check we are at tag value closing quote
        if (line.charAt(i)!='\"')
            throw new MalformedMetricLineException("Expected '\"' to close tag value");

        return line.substring(start+1, i);
    }

    protected String processEscapeSequences(String value) {
        return value.replaceAll("\\\"", "\"").replaceAll("\\t", "\t").replaceAll("\\r", "\r");
    }

    protected String getNonWhites(String line, int i) {
        int start = i;
        int lineLength = line.length();
        while (i<lineLength && !isWhite(line.charAt(i))) i++;
        return line.substring(start, i);
    }

    protected boolean isWhite(char c) {
        return c==' ' || c=='\t' || c=='\r';
    }

    protected boolean isAlphanumeric(char c, boolean includeDigits) {
        return ('A'<=c && c<='Z' || 'a'<=c && c<='z' || c=='_' || includeDigits && '0'<=c && c<='9');
    }

    protected MetricInstance createMetricInstance(String metricName, String valueStr, String tmStr, Map<String,String> tags, ProcessingContext context) {
        // Prepare value
        valueStr = valueStr.trim();
        double value;
        try {
            if ("NaN".equalsIgnoreCase(valueStr)) value = Double.NaN;
            else if ("Inf".equalsIgnoreCase(valueStr)) value = Double.POSITIVE_INFINITY;
            else if ("+Inf".equalsIgnoreCase(valueStr)) value = Double.POSITIVE_INFINITY;
            else if ("-Inf".equalsIgnoreCase(valueStr)) value = Double.NEGATIVE_INFINITY;
            else value = Double.parseDouble(valueStr);
        } catch (Exception e) {
            throw new MalformedMetricLineException("Invalid metric value: "+valueStr, e);
        }

        // Prepare timestamp
        long timestamp;
        try {
            timestamp = (tmStr != null && !tmStr.trim().isEmpty()) ? Long.parseLong(tmStr.trim()) : System.currentTimeMillis();
        } catch (Exception e) {
            throw new MalformedMetricLineException("Invalid timestamp: "+tmStr, e);
        }

        // Prepare type and help text
        METRIC_TYPE metricType = context!=null ? context.getMetricTypes().computeIfAbsent(metricName, s->METRIC_TYPE.UNTYPED) : METRIC_TYPE.UNTYPED;
        String helpText = context!=null ? context.getMetricHelpTexts().computeIfAbsent(metricName, s->null) : null;

        // Create metric instance
        return MetricInstance.builder()
                .metricName(metricName)
                .metricType(metricType)
                .metricValue(value)
                .timestamp(timestamp)
                .tags(new LinkedHashMap<>(tags))
                .helpText(helpText)
                .build();
    }

    public enum METRIC_TYPE { UNTYPED, COUNTER, GAUGE, HISTOGRAM, SUMMARY }

    @Data
    public static class ProcessingContext {
        private Map<String,METRIC_TYPE> metricTypes = new HashMap<>();
        private Map<String,String> metricHelpTexts = new HashMap<>();
    }

    @Data
    @Builder
    public static class MetricInstance {
        @NonNull private final String metricName;
        @NonNull private final METRIC_TYPE metricType;
        private final double metricValue;
        private final long timestamp;
        private final Map<String,String> tags;
        private final String helpText;
    }

    public static class MalformedMetricLineException extends RuntimeException {
        public MalformedMetricLineException() { super(); }
        public MalformedMetricLineException(String message) { super(message); }
        public MalformedMetricLineException(String message, Throwable t) { super(message, t); }
        public MalformedMetricLineException(Throwable t) { super(t); }
    }
}
