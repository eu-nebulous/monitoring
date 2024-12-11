/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;
import eu.nebulous.ems.translate.analyze.antlr4.ConstraintsBaseVisitor;
import eu.nebulous.ems.translate.analyze.antlr4.ConstraintsLexer;
import eu.nebulous.ems.translate.analyze.antlr4.ConstraintsParser;
import gr.iccs.imu.ems.control.collector.IServerCollector;
import gr.iccs.imu.ems.control.collector.ServerCollectorContext;
import gr.iccs.imu.ems.translate.model.MetricTemplate;
import gr.iccs.imu.ems.translate.model.ValueType;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static eu.nebulous.ems.translate.analyze.AnalysisUtils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShorthandsExpansionHelper implements InitializingBean {
    /*private final static Pattern METRIC_CONSTRAINT_PATTERN =
            Pattern.compile("^([^<>=!]+)([<>]=|=[<>]|<>|!=|[=><])(.+)$");*/
    private final static Pattern METRIC_WINDOW_PATTERN =
            Pattern.compile("^\\s*(\\w+)\\s+(\\d+(?:\\.\\d*)?|\\.\\d+)\\s*(?:(\\w+)\\s*)?");
    private final static Pattern METRIC_WINDOW_SIZE_PATTERN =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d*)?|\\.\\d+)\\s*(?:(\\w+)\\s*)?");
    private final static Pattern METRIC_OUTPUT_PATTERN =
            Pattern.compile("^\\s*(\\w+)\\s+(\\d+(?:\\.\\d*)?|\\.\\d+)\\s*(\\w+)\\s*");
    private final static Pattern METRIC_OUTPUT_SCHEDULE_PATTERN =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d*)?|\\.\\d+)\\s*(\\w+)\\s*");
    private final static Pattern METRIC_SENSOR_PATTERN =
            Pattern.compile("^\\s*(\\w+)\\s+(\\w[\\w\\.]+\\w)\\s*");

    public final static String AS_IS_HACK_TAG = "AS-IS-HACK:";
    public final static String AS_IS_HACK_PART_SEPARATOR = "|";

    private final ServerCollectorContext serverCollectorContext;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (serverCollectorContext!=null)
            log.debug("ShorthandsExpansionHelper: serverCollectorContext={}", serverCollectorContext);
        else
            throw new IllegalArgumentException("Field 'serverCollectorContext' is NULL. Probably a bug?");
    }

    // ------------------------------------------------------------------------
    //  Methods for expanding shorthand expressions
    // ------------------------------------------------------------------------

    public void expandShorthandExpressions(Object metricModel, String modelName) throws Exception {
        log.debug("ShorthandsExpansionHelper: model-name: {}", modelName);

        // -- Initialize jsonpath context -------------------------------------
        Configuration jsonpathConfig = Configuration.defaultConfiguration();
        ParseContext parseContext = JsonPath.using(jsonpathConfig);
        DocumentContext ctx = parseContext.parse(metricModel);

        // ----- Expand SLO constraints -----
        List<Object> expandedConstraints = asList(ctx
                .read("$.spec.*.*.requirements.*[?(@.constraint)]", List.class)).stream()
                .filter(item -> JsonPath.read(item, "$.constraint") instanceof String)
//                .peek(this::expandConstraint)
                .peek(this::expandConstraintExpression)
                .toList();
        log.debug("ShorthandsExpansionHelper: Constraints expanded: {}", expandedConstraints);

        // ----- Read Metric templates -----
        Map<Object, Map> templateSpecs = new LinkedHashMap<>();
        templateSpecs.putAll( readMetricTemplate("$.templates.*", ctx) );
        templateSpecs.putAll( readMetricTemplate("$.spec.templates.*", ctx) );
        log.debug("ShorthandsExpansionHelper: Metric Templates found: {}", templateSpecs);

        // ----- Expand Metric templates in Metric specifications -----
        List<Object> expandedTemplates = asList(ctx
                .read("$.spec.*.*.metrics.*[?(@.template)]", List.class)).stream()
                .filter(item -> JsonPath.read(item, "$.template") instanceof String s && StringUtils.isNotBlank(s))
                .peek(item -> expandTemplate(item, templateSpecs))
                .toList();
        log.debug("ShorthandsExpansionHelper: Templates expanded: {}", expandedTemplates);

        // ----- Expand Metric windows -----
        List<Object> expandedWindows = asList(ctx
                .read("$.spec.*.*.metrics.*[?(@.window)]", List.class)).stream()
                .filter(item -> JsonPath.read(item, "$.window") instanceof String)
                .peek(this::expandWindow)
                .toList();
        log.debug("ShorthandsExpansionHelper: Windows expanded: {}", expandedWindows);

        List<Object> expandedWindowSizes = asList(ctx
                .read("$.spec.*.*.metrics.*.window[?(@.size)]", List.class)).stream()
                .filter(item -> JsonPath.read(item, "$.size") instanceof String)
                .peek(this::expandWindowSize)
                .toList();
        log.debug("ShorthandsExpansionHelper: Windows sizes expanded: {}", expandedWindowSizes);

        // ----- Expand Metric outputs -----
        List<Object> expandedOutputs = asList(ctx
                .read("$.spec.*.*.metrics.*[?(@.output)]", List.class)).stream()
                .filter(item -> JsonPath.read(item, "$.output") instanceof String)
                .peek(this::expandOutput)
                .toList();
        log.debug("ShorthandsExpansionHelper: Outputs expanded: {}", expandedOutputs);

        List<Object> expandedOutputSchedules = asList(ctx
                .read("$.spec.*.*.metrics.*.output[?(@.schedule)]", List.class)).stream()
                .filter(item -> JsonPath.read(item, "$.schedule") instanceof String)
                .peek(this::expandOutputSchedule)
                .toList();
        log.debug("ShorthandsExpansionHelper: Output schedules expanded: {}", expandedOutputSchedules);

        // ----- Expand Metric sensors -----
        List<Object> expandedSensors = asList(ctx
                .read("$.spec.*.*.metrics.*[?(@.sensor)]", List.class)).stream()
                .peek(this::expandSensor)
                .toList();
        log.debug("ShorthandsExpansionHelper: Sensors expanded: {}", expandedSensors);

        // ----- Fix SLO names (due to Nebulous GUI default) -----
        List<Object> fixedSloNames = asList(ctx
                .read("$.spec.*.*.requirements.*[?(@.name)]", List.class)).stream()
                .peek(this::fixSloName)
                .toList();
        log.debug("ShorthandsExpansionHelper: SLO names fixed: {}", fixedSloNames);

        // ----- Replace Composite Metric with Hack override with As-Is Metric -----
        List<Object> metricsWithAsIsHacks = asList(ctx
                .read("$.spec.*.*.metrics.*[?(@.formula)]", List.class)).stream()
                .filter(item -> JsonPath.read(item, "$.formula") instanceof String)
                .filter(item -> StringUtils.startsWithIgnoreCase(
                        JsonPath.read(item, "$.formula").toString(), AS_IS_HACK_TAG))
                .peek(this::prepareReplacementMetric)
                .toList();
        log.debug("ShorthandsExpansionHelper: As-Is Hacks: {}", metricsWithAsIsHacks);
    }

    private void prepareReplacementMetric(Object spec) {
        log.debug("ShorthandsExpansionHelper.prepareReplacementMetric: BEGIN: {}", spec);
        String name = JsonPath.read(spec, "$.name").toString().trim();
        String formula = JsonPath.read(spec, "$.formula").toString().trim().substring(AS_IS_HACK_TAG.length());
        log.debug("ShorthandsExpansionHelper.prepareReplacementMetric: Metric name: {}, formula: {}", name, formula);

        String definition = formula;
        List<String> composingMetrics = List.of();

        int i = formula.indexOf(AS_IS_HACK_PART_SEPARATOR);
        if (i>0) {
            definition = formula.substring(i+1).trim();
            composingMetrics = Arrays.stream(formula.substring(0, i).trim().split("[ \t\r\n,;]+"))
                    .filter(StringUtils::isNotBlank).toList();
        }
        log.debug("ShorthandsExpansionHelper.prepareReplacementMetric: Definition: {}", definition);
        log.debug("ShorthandsExpansionHelper.prepareReplacementMetric: Composing Metrics: {}", composingMetrics);
        if (StringUtils.isBlank(definition))
            throw createException("Invalid AS-IS hack: Definition is empty: metric: " + name);

        // Replace metric specification with a new AS-IS metric
        Map<String, Object> map = asMap(spec);
        map.put("type", "as-is");
        map.put("definition", definition);
        map.put("metrics", composingMetrics);
        map.remove("formula");
        map.remove("window");
        map.remove("output");

        log.debug("ShorthandsExpansionHelper.prepareReplacementMetric: END: spec: {}", spec);
    }

    private void fixSloName(Object spec) {
        log.debug("ShorthandsExpansionHelper.fixSloName: BEGIN: {}", spec);
        String sloName = JsonPath.read(spec, "$.name").toString().trim();
        log.debug("ShorthandsExpansionHelper.fixSloName: SLO name: {}", sloName);

        if ("combined-slo".equalsIgnoreCase(sloName)) {
            sloName = "combined_slo_" + System.currentTimeMillis();
            asMap(spec).put("name", sloName);
            log.debug("ShorthandsExpansionHelper.fixSloName: SLO name fixed to: {}", sloName);
        } else {
            log.debug("ShorthandsExpansionHelper.fixSloName: No need to fix SLO name: {}", sloName);
        }
        log.debug("ShorthandsExpansionHelper.fixSloName: END: spec: {}", spec);
    }

    private static Map<String, Map> readMetricTemplate(@NonNull String path, @NonNull DocumentContext ctx) {
        try {
            return asList(ctx
                    .read(path, List.class)).stream()
                    .filter(x -> x instanceof Map)
                    .map(x -> (Map) x)
                    .filter(x -> x.get("id") != null)
                    .collect(Collectors.toMap(x -> x.get("id").toString(), x -> x));
        } catch (Exception e) {
            log.debug("ShorthandsExpansionHelper.readMetricTemplate: Not found metric templates in path: {}", path);
            return Collections.emptyMap();
        }
    }

    private void expandTemplate(Object spec, Map<Object, Map> templateSpecs) {
        log.debug("ShorthandsExpansionHelper.expandTemplate: {}", spec);
        String templateId = JsonPath.read(spec, "$.template").toString().trim();
        Object tplSpec = templateSpecs.get(templateId);
        if (tplSpec!=null) {
            asMap(spec).put("template", tplSpec);
        } else {
            List<String> parts = Arrays.asList(templateId.split("[ \t\r\n]+"));
            if (parts.size()>=4) {
                MetricTemplate newTemplate;
                if (StringUtils.equalsAnyIgnoreCase(parts.get(0), "double", "float")) {
                    asMap(spec).put("template", MetricTemplate.builder()
                            .valueType(ValueType.DOUBLE_TYPE)
                            .lowerBound("-inf".equalsIgnoreCase(parts.get(1))
                                    ? Double.NEGATIVE_INFINITY : Double.parseDouble(parts.get(1)))
                            .upperBound(StringUtils.equalsAnyIgnoreCase(parts.get(2), "inf", "+inf")
                                    ? Double.POSITIVE_INFINITY : Double.parseDouble(parts.get(2)))
                            .unit(parts.get(3))
                            .build());
                } else
                if (StringUtils.equalsAnyIgnoreCase(parts.get(0), "int", "integer")) {
                    asMap(spec).put("template", MetricTemplate.builder()
                            .valueType(ValueType.INT_TYPE)
                            .lowerBound("-inf".equalsIgnoreCase(parts.get(1))
                                    ? Integer.MIN_VALUE : Integer.parseInt(parts.get(1)))
                            .upperBound(StringUtils.equalsAnyIgnoreCase(parts.get(2), "inf", "+inf")
                                    ? Integer.MAX_VALUE : Integer.parseInt(parts.get(2)))
                            .unit(parts.get(3))
                            .build());
                } else
                    throw createException("Invalid Metric template shorthand expression: " + templateId);
            } else
                throw createException("Metric template id not found: " + templateId);
        }
    }

    private void expandWindow(Object spec) {
        log.debug("ShorthandsExpansionHelper.expandWindow: {}", spec);
        String constraintStr = JsonPath.read(spec, "$.window").toString().trim();
        Matcher matcher = METRIC_WINDOW_PATTERN.matcher(constraintStr);
        if (matcher.matches()) {
            asMap(spec).put("window", Map.of(
                    "type", matcher.group(1),
                    "size", (matcher.groupCount()>2)
                            ? Map.of("value", matcher.group(2), "unit", matcher.group(3))
                            : Map.of("value", matcher.group(2))
            ));
        } else
            throw createException("Invalid metric window shorthand expression: "+spec);
    }

    private void expandWindowSize(Object spec) {
        log.debug("ShorthandsExpansionHelper.expandWindowSize: {}", spec);
        String constraintStr = JsonPath.read(spec, "$.size").toString().trim();
        Matcher matcher = METRIC_WINDOW_SIZE_PATTERN.matcher(constraintStr);
        if (matcher.matches()) {
            asMap(spec).put("size", (matcher.groupCount()>1)
                            ? Map.of("value", matcher.group(1), "unit", matcher.group(2))
                            : Map.of("value", matcher.group(1))
            );
        } else
            throw createException("Invalid metric window shorthand expression: "+spec);
    }

    private void expandOutput(Object spec) {
        log.debug("ShorthandsExpansionHelper.expandOutput: {}", spec);
        String constraintStr = JsonPath.read(spec, "$.output").toString().trim();
        Matcher matcher = METRIC_OUTPUT_PATTERN.matcher(constraintStr);
        if (matcher.matches()) {
            asMap(spec).put("output", Map.of(
                    "type", matcher.group(1),
                    "schedule", Map.of(
                            "value", matcher.group(2),
                            "unit", matcher.group(3))
            ));
        } else
            throw createException("Invalid metric output shorthand expression: "+spec);
    }

    private void expandOutputSchedule(Object spec) {
        log.debug("ShorthandsExpansionHelper.expandOutputSchedule: {}", spec);
        String constraintStr = JsonPath.read(spec, "$.schedule").toString().trim();
        Matcher matcher = METRIC_OUTPUT_SCHEDULE_PATTERN.matcher(constraintStr);
        if (matcher.matches()) {
            asMap(spec).put("schedule", Map.of(
                            "value", matcher.group(1),
                            "unit", matcher.group(2))
            );
        } else
            throw createException("Invalid metric output shorthand expression: "+spec);
    }

    private void expandSensor(Object spec) {
        log.debug("ShorthandsExpansionHelper.expandSensor: BEGIN: {}", spec);
        Object sensorSpec = JsonPath.read(spec, "$.sensor");
        log.trace("ShorthandsExpansionHelper.expandSensor: sensorSpec: {}", sensorSpec);

        // Get sensor type
        String typeSpecStr = null;
        if (sensorSpec instanceof String s) {
            typeSpecStr = s.trim();
            sensorSpec = new LinkedHashMap<>();
            asMap(spec).put("sensor", sensorSpec);
        } else
        if (sensorSpec instanceof Map map) {
            if (map.get("type")==null || !(map.get("type") instanceof String))
                throw createException("Invalid metric sensor spec. No sensor type: " + spec);
            typeSpecStr = map.get("type").toString().trim();
        }

        // If sensor type is not provided, use 'custom'
        if (StringUtils.isBlank(typeSpecStr))
            typeSpecStr = SensorsHelper.DEFAULT_SENSOR_TYPE;

        // Initialize sensor config map, and sensor type
        if (StringUtils.isNotBlank(typeSpecStr)) {
            Matcher matcher = METRIC_SENSOR_PATTERN.matcher(typeSpecStr);
            if (matcher.matches()) {
                asMap(sensorSpec).put("type", matcher.group(1));
                Object sensorConfigSpec = asMap(sensorSpec).get("config");
                Map<String, Object> sensorConfigMap = sensorConfigSpec == null
                        ? new LinkedHashMap<>()
                        : asMap(sensorConfigSpec);
                asMap(sensorSpec).put("config", sensorConfigMap);
                updateSensorConfigMap(sensorConfigMap, matcher.group(1), matcher.group(2));
            } else {
                asMap(sensorSpec).put("type", typeSpecStr);
            }
        } else
            throw createException("Invalid metric sensor shorthand expression or sensor specification: " + spec);
        log.trace("ShorthandsExpansionHelper.expandSensor: END: {}", spec);
    }

    private void updateSensorConfigMap(Map<String, Object> sensorConfigMap, String sensorType, String stringConfigExpression) {
        log.debug("ShorthandsExpansionHelper.updateSensorConfigMap: BEGIN: sensorType={}, expression={}, sensorConfigMap={}", sensorType, stringConfigExpression, sensorConfigMap);
        if (serverCollectorContext!=null) {
            IServerCollector collector = serverCollectorContext.getCollectorByName(sensorType);
            log.debug("ShorthandsExpansionHelper.updateSensorConfigMap: sensorType={}, collector={}", sensorType, collector);
            if (collector != null) {
                Map<String, Object> result = collector.stringToConfigMap(stringConfigExpression);
                log.debug("ShorthandsExpansionHelper.updateSensorConfigMap: stringConfigExpression={}, config-map={}", stringConfigExpression, result);
                if (result != null)
                    sensorConfigMap.putAll(result);
            }
        } else {
            log.warn("ShorthandsExpansionHelper.updateSensorConfigMap: Field 'serverCollectorContext' is NULL");
        }
        log.debug("ShorthandsExpansionHelper.updateSensorConfigMap: END: sensorConfigMap={}", sensorConfigMap);
    }

    /*private void expandConstraint(Object spec) {
        log.debug("ShorthandsExpansionHelper.expandConstraint: {}", spec);
        String constraintStr = JsonPath.read(spec, "$.constraint").toString().trim();
        log.trace("ShorthandsExpansionHelper.expandConstraint: BEFORE removeOuterBrackets: {}", constraintStr);
        constraintStr = removeOuterBrackets(constraintStr);
        log.trace("ShorthandsExpansionHelper.expandConstraint:  AFTER removeOuterBrackets: {}", constraintStr);
        Matcher matcher = METRIC_CONSTRAINT_PATTERN.matcher(constraintStr);
        if (matcher.matches()) {
            String g1 = matcher.group(1);
            String g2 = matcher.group(2);
            String g3 = matcher.group(3);

            if (! isComparisonOperator(g2))
                throw createException("Invalid metric constraint shorthand expression in Requirement [Group 2 not a comparison operator]: "+spec);

            // Swap operands
            if (isDouble(g1)) {
                String tmp = g1;
                g1 = g3;
                g3 = tmp;
            }

            if (StringUtils.isBlank(g1) || StringUtils.isBlank(g3))
                throw createException("Invalid metric constraint shorthand expression in Requirement [Group 1 or 3 is blank]: "+spec);

            String metricName = g1.trim();
            double threshold = Double.parseDouble(g3.trim());

            Map<String, Object> constrMap = Map.of(
                    "type", "metric",
                    "metric", metricName,
                    "operator", g2.trim(),
                    "threshold", threshold
            );

            asMap(spec).put("constraint", constrMap);
        } else
            throw createException("Invalid metric constraint shorthand expression: "+spec);
    }

    private String removeOuterBrackets(String s) {
        if (s==null) return null;
        s = s.trim();
        if (s.isEmpty()) return s;
        while (s.startsWith("(") && s.endsWith(")")
            || s.startsWith("[") && s.endsWith("]")
            || s.startsWith("{") && s.endsWith("}"))
        {
            s = s.substring(1, s.length() - 1).trim();
            if (s.isEmpty()) return s;
        }
        return s;
    }*/

    public void expandConstraintExpression(Object spec) {
        log.debug("ShorthandsExpansionHelper.expandConstraintExpression: {}", spec);

        // Get constraint string
        String constraintStr = JsonPath.read(spec, "$.constraint").toString().trim();
        log.debug("ShorthandsExpansionHelper.expandConstraintExpression: constraint-expression: {}", constraintStr);

        // Check if constraint string is empty or "()"
        if (StringUtils.isBlank(constraintStr) || "()".equals(constraintStr.replaceAll("[ \t\r\n]", ""))) {
            log.warn("ShorthandsExpansionHelper.expandConstraintExpression: Empty constraint-expression: {}", spec);
            return;
        }

        // Create a CharStream that reads from standard input
        CodePointCharStream input = CharStreams.fromString(constraintStr);

        // create a lexer that feeds off of input CharStream
        ConstraintsLexer lexer = new ConstraintsLexer(input);

        // create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // create a parser that feeds off the tokens buffer
        ConstraintsParser parser = new ConstraintsParser(tokens);

        ParseTree tree = parser.constraintExpression();
        if (log.isTraceEnabled())
            // print LISP-style tree
            log.trace("ShorthandsExpansionHelper.expandConstraintExpression: parse-tree: {}", tree.toStringTree(parser));

        // Create expanded constraint specification
        ConstraintVisitor visitor = new ConstraintVisitor();
        Map<String, Object> map = visitor.visit(tree);
        log.trace("ShorthandsExpansionHelper.expandConstraintExpression: resulting-map: {}", map);

        asMap(spec).put("constraint", map);
        log.trace("ShorthandsExpansionHelper.expandConstraintExpression: Spec AFTER update: {}", spec);
    }

    @Getter
    @Setter
    private static class ConstraintVisitor extends ConstraintsBaseVisitor<Map<String,Object>> {
        private boolean optimize = true;
        private boolean logAnyway = false;

        @Override
        public Map<String,Object> visitConstraintExpression(ConstraintsParser.ConstraintExpressionContext ctx) {
            log(ctx, "ConstraintExpression: {}", ctx.children.size());
            return visitOrConstraint(ctx.orConstraint());
        }

        @Override
        public Map<String,Object> visitOrConstraint(ConstraintsParser.OrConstraintContext ctx) {
            if (optimize && ctx.andConstraint().size()==1)
                return visitAndConstraint(ctx.andConstraint(0));
            log(ctx, "OrConstraint: {} -- and: {}", ctx.children.size(), ctx.andConstraint().size());
            ArrayList<Map<String,Object>> childMapList = new ArrayList<>();
            for (ConstraintsParser.AndConstraintContext constraintContext : ctx.andConstraint()) {
                Map<String, Object> childMap = visitAndConstraint(constraintContext);
                childMapList.add(childMap);
            }
            return makeMap(ctx,
                    "type", "logical",
                    "operator", "or",
                    "constraints", childMapList
            );
        }

        @Override
        public Map<String,Object> visitAndConstraint(ConstraintsParser.AndConstraintContext ctx) {
            if (optimize && ctx.constraint().size()==1)
                return visitConstraint(ctx.constraint(0));
            log(ctx, "AndConstraint: {} -- cons: {}", ctx.children.size(), ctx.constraint().size());
            ArrayList<Map<String,Object>> childMapList = new ArrayList<>();
            for (ConstraintsParser.ConstraintContext constraintContext : ctx.constraint()) {
                Map<String, Object> childMap = visitConstraint(constraintContext);
                childMapList.add(childMap);
            }
            return makeMap(ctx,
                    "type", "logical",
                    "operator", "and",
                    "constraints", childMapList
            );
        }

        @Override
        public Map<String,Object> visitConstraint(ConstraintsParser.ConstraintContext ctx) {
            if (ctx.PARENTHESES_OPEN()!=null) {
                if (!optimize) log(ctx, "Constraint: PARENTHESES");
                return visitOrConstraint(ctx.orConstraint());
            }

            if (ctx.metricConstraint()!=null) {
                if (!optimize) log(ctx, "Constraint: METRIC CONSTRAINT");
                return visitMetricConstraint(ctx.metricConstraint());
            }
            if (ctx.notConstraint()!=null) {
                if (!optimize) log(ctx, "Constraint: NOT CONSTRAINT");
                return visitNotConstraint(ctx.notConstraint());
            }
            if (ctx.conditionalConstraint()!=null) {
                if (!optimize) log(ctx, "Constraint: CONDITIONAL CONSTRAINT");
                return visitConditionalConstraint(ctx.conditionalConstraint());
            }

            // error
            log(ctx, true, "Constraint: ERROR: ");
            for (ParseTree child : ctx.children) {
                log(ctx, true, "Constraint: ERROR: --> {}", child.getText());
            }
            throw new IllegalArgumentException("Unexpected constraint type encountered: "+ctx.getText());
        }

        @Override
        public Map<String,Object> visitMetricConstraint(ConstraintsParser.MetricConstraintContext ctx) {
            log(ctx, "MetricConstraint: {}", ctx.children.size());
            String metric = ctx.ID().getText();
            Double threshold = Double.parseDouble( ctx.NUM().getText() );
            String operator = ctx.comparisonOperator().getText();

            // Check if first child is NOT the 'metric' (i.e. NUM op METRIC)
            if (! ctx.getChild(0).getText().equals(metric)) {
                // Inverse operator (implies METRIC inverted-op NUM)
                if (! "<>".equals(operator)) {
                    operator = operator.contains("<")
                            ? operator.replace("<", ">")
                            : operator.replace(">", "<");
                }
            }

            return makeMap(ctx,
                    "type", "metric",
                    "metric", metric,
                    "threshold", threshold,
                    "operator", operator
            );
        }

        @Override
        public Map<String,Object> visitNotConstraint(ConstraintsParser.NotConstraintContext ctx) {
            log(ctx, "NotConstraint: {}", ctx.children.size());
            Map<String, Object> childMap = visitConstraint(ctx.constraint());
            log(ctx, "NotConstraint: --> Constraint to be NEGATED: {}", childMap);
            /*return makeMap(ctx,
                    "type", "logical",
                    "operator", "not",
                    "constraints", List.of( childMap )
            );*/

            childMap = negateConstraint(childMap);
            log(ctx, "NotConstraint: --> Constraint NEGATED: {}", childMap);
            return childMap;
        }

        private Map<String,Object> negateConstraint(Map<String, Object> map) {
            log.trace("ConstraintVisitor.negateConstraint: BEGIN: {}", map);
            if (map==null) return null;

            String type = map.get("type").toString();
            log.trace("ConstraintVisitor.negateConstraint: type: {}", type);
            if (StringUtils.isBlank(type))
                throw new IllegalArgumentException("Missing constraint type: map: "+map);

            if ("metric".equalsIgnoreCase(type)) {
                String operator = map.get("operator").toString();
                log.error("ConstraintVisitor.negateConstraint: METRIC-CONSTRAINT: operator-BEFORE: {}", operator);

                // Negate comparison operator
                if (StringUtils.equalsAny(operator, "=", "==")) operator = "<>";
                else if (StringUtils.equalsAny(operator, "<>")) operator = "=";
                else if (StringUtils.equalsAny(operator, "<")) operator = ">=";
                else if (StringUtils.equalsAny(operator, "<=", "=<")) operator = ">";
                else if (StringUtils.equalsAny(operator, ">")) operator = "<=";
                else if (StringUtils.equalsAny(operator, ">=", "=>")) operator = "<";
                else
                    throw new IllegalArgumentException("Invalid comparison operator: "+operator);

                log.error("ConstraintVisitor.negateConstraint: METRIC-CONS: operator-AFTER: {}", operator);
                map.put("operator", operator);
                return map;
            } else
            if ("logical".equalsIgnoreCase(type)) {
                String operator = map.get("operator").toString();
                log.trace("ConstraintVisitor.negateConstraint: LOGICAL-CONSTRAINT: operator-BEFORE: {}", operator);

                // Negate (second) Not-constraint
                if ("not".equalsIgnoreCase(operator)) {
                    Map<String, Object> result = (Map<String, Object>) asList(map.get("constraints")).get(0);
                    log.trace("ConstraintVisitor.negateConstraint: NOT-CONSTRAINT: END: {}", result);
                    return result;
                }

                // Negate and/or constraint
                if ("and".equalsIgnoreCase(operator))
                    operator = "or";
                if ("or".equalsIgnoreCase(operator))
                    operator = "and";
                log.trace("ConstraintVisitor.negateConstraint: LOGICAL-CONSTRAINT: operator-AFTER: {}", operator);
                map.put("operator", operator);
                asList(map.get("constraints")).forEach(c -> negateConstraint((Map<String,Object>)c));
                return map;
            } else
            if ("conditional".equalsIgnoreCase(type)) {
                log.trace("ConstraintVisitor.negateConstraint: CONDITIONAL-CONSTRAINT: ");

                // IF-THEN      <==> IF AND THEN                      --- Negation: IF AND NOT(THEN)  (See: https://www.math.toronto.edu/preparing-for-calculus/3_logic/we_3_negation.html)
                // IF-ELSE      <==> NOT(IF) AND ELSE                 --- Negation: NOT(IF) AND NOT(ELSE)
                // IF-THEN-ELSE <==> IF AND THEN XOR NOT(IF) AND ELSE --- Negation: IF AND NOT(THEN) XOR NOT(IF) AND NOT(ELSE)  ?????

                Map<String, Object> ifConstraint = asMap(map.get("if"));
                Map<String, Object> thenConstraint = asMap(map.get("then"));
                Map<String, Object> elseConstraint = asMap(map.get("else"));
                ////ifConstraint = negateConstraint(ifConstraint);
                thenConstraint = negateConstraint(thenConstraint);
                elseConstraint = negateConstraint(elseConstraint);
                map.put("if",   ifConstraint);
                map.put("then", thenConstraint);
                map.put("else", elseConstraint);
                return map;
            } else
                throw new IllegalArgumentException("Invalid constraint type: "+type);
        }

        @Override
        public Map<String,Object> visitConditionalConstraint(ConstraintsParser.ConditionalConstraintContext ctx) {
            log(ctx, "ConditionalConstraint: {}", ctx.children.size());
            Map<String, Object> ifMap = visitOrConstraint(ctx.orConstraint(0));
            Map<String, Object> thenMap = visitOrConstraint(ctx.orConstraint(1));
            Map<String, Object> elseMap = ctx.orConstraint().size()>2
                    ? visitOrConstraint(ctx.orConstraint(2)) : null;

            return elseMap!=null
                    ? makeMap(ctx, "type", "conditional",
                            "if", ifMap,
                            "then", thenMap,
                            "else", elseMap)
                    : makeMap(ctx, "type", "conditional",
                            "if", ifMap,
                            "then", thenMap);
        }

        // --------------------------------------------------------------------

        private void log(ParserRuleContext ctx, String formatter, Object...args) {
            log(ctx, false, formatter, args);
        }

        private void log(ParserRuleContext ctx, boolean isError, String formatter, Object...args) {
            if (isError || logAnyway || log.isDebugEnabled()) {
                String indent = StringUtils.repeat(' ', 2 * (ctx.depth() - 1));
                if (isError) {
                    log.error("ConstraintVisitor: " + indent + formatter, args);
                } else if (logAnyway) {
                    log.warn("ConstraintVisitor: " + indent + formatter + " [::] " + ctx.getText(), args);
                } else {
                    log.debug("ConstraintVisitor: " + indent + formatter + " [::] " + ctx.getText(), args);
                }
            }
        }

        private Map<String,Object> makeMap(ParserRuleContext ctx, Object...args) {
            if (args.length%2==1)
                throw new IllegalArgumentException("makeMap argument number is not even: "+args.length);
            LinkedHashMap<String,Object> map = new LinkedHashMap<>();
            for (int i=0, n=args.length; i<n; i+=2) {
                if (args[i]==null || args[i+1]==null)
                    throw new IllegalArgumentException("makeMap arguments cannot be null: pos="+i);
                if (args[i] instanceof String key)
                    map.put(key, args[i+1]);
                else
                    throw new IllegalArgumentException("makeMap argument at key position is not a string: pos="+i);
            }
            log(ctx, "--> result: {}", map);
            return map;
        }
    }
}