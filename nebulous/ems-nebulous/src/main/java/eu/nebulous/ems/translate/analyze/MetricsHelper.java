/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import gr.iccs.imu.ems.brokercep.cep.MathUtil;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.model.*;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import eu.nebulous.ems.translate.generate.RuleGenerator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static eu.nebulous.ems.translate.analyze.AnalysisUtils.*;

// ------------------------------------------------------------------------
//  Metric decomposition methods
// ------------------------------------------------------------------------

@Slf4j
@Service
@RequiredArgsConstructor
class MetricsHelper extends AbstractHelper {
    private final static String DEFAULT_METRIC_NAME_SUFFIX = "_METRIC";

    private final NebulousEmsTranslatorProperties properties;
    private final SensorsHelper sensorsHelper;

    MetricContext decomposeMetric(@NonNull TranslationContext _TC, Map<String, Object> metricSpec, NamesKey parentNamesKey, NamedElement parent) {
        log.debug("decomposeMetric: ARGS: spec: {}, parent-name: {}, parent-elem: {}", metricSpec, parentNamesKey, parent);

        // Get needed fields
        String metricName = getSpecName(metricSpec).toLowerCase();
        String metricType = getSpecField(metricSpec, "type");

        NamesKey metricNamesKey = getNamesKey(metricSpec, metricName);
        if ($$(_TC).metricsUsed.containsKey(metricNamesKey)) {
            MetricContext cachedMetric = $$(_TC).metricsUsed.get(metricNamesKey);
            _TC.getDAG().addNode(parent, cachedMetric);
            return cachedMetric;
        }

        // Infer metric type
        if (StringUtils.isBlank(metricType)) {
            boolean hasSensor = (metricSpec.get("sensor") != null);
            boolean hasFormula = (metricSpec.get("formula") != null);
            boolean hasConstant = (metricSpec.get("constant") != null);
            boolean hasRef = (metricSpec.get("ref") != null);

                 if ( hasSensor && !hasFormula && !hasConstant && !hasRef) metricType = "raw";
            else if (!hasSensor &&  hasFormula && !hasConstant && !hasRef) metricType = "composite";
            else if (!hasSensor && !hasFormula &&  hasConstant && !hasRef) metricType = "constant";
            else if (!hasSensor && !hasFormula && !hasConstant &&  hasRef) metricType = "ref";
            else
                throw createException("Cannot infer type of metric '"+metricName+"': " + metricSpec);
        }

        // If a 'constant' stop, because constants have already been registered
        if ("constant".equals(metricType)) {
            return null;
        }
        // ...else continue with metric decomposition

        // Delegate decomposition based on metric type
        MetricContext metric = switch (metricType) {
            case "raw" ->
                    decomposeRawMetric(_TC, metricSpec, parent);
            case "composite" ->
                    decomposeCompositeMetric(_TC, metricSpec, parent);
            case "ref" ->
                    processRef(_TC, metricSpec, parentNamesKey, parent);
            default ->
                    throw createException("Unknown metric type '" + metricType + "' in metric '" + metricName + "': " + metricSpec);
        };
        $$(_TC).metricsUsed.put(metricNamesKey, metric);

        // Set 'object'
        metric.setObject(metricSpec);

        // Process template
        MetricTemplate template = processMetricTemplate(metricSpec, metricNamesKey);
        if (template!=null)
            metric.getMetric().setMetricTemplate(template);

        // Check if it is a Busy-Status metric
        List<Object> requiredMetrics =
                _TC.getAdditionalArguments()!=null && _TC.getAdditionalArguments().get("required-metrics")!=null
                        ? asList(_TC.getAdditionalArguments().get("required-metrics")) : List.of();
        boolean isRequired = requiredMetrics.contains(metric.getName());
        boolean isBusyStatus = getSpecBoolean(metricSpec, "busy-status", false);
        log.trace("decomposeMetric: {}: is-required={}, is-busy-metric={}, required-metrics={}",
                metricNamesKey.name(), isRequired, isBusyStatus, requiredMetrics);
        if (isBusyStatus || isRequired) {
            if (isBusyStatus)
                log.trace("decomposeMetric: {}: It is a BUSY-STATUS metric", metricNamesKey.name());
            if (isRequired)
                log.trace("decomposeMetric: {}: It is a REQUIRED metric", metricNamesKey.name());

            // Add a top-level node to connect busy-status metric
            String busyStatusMetricName =
                    String.format(properties.getBusyStatusDestinationNameFormatter(), metric.getName());
            BusyStatusMetricVariable newMv = BusyStatusMetricVariable.builder()
                    .name(busyStatusMetricName)
                    .metricContext(metric)
                    .componentMetrics(List.of(metric.getMetric()))
                    .metricTemplate(metric.getMetric().getMetricTemplate())
                    .build();
            log.debug("decomposeMetric: {}: New Busy-Status metric variable: {}", metricNamesKey.name(), newMv.getName());

            // Update TC
            if (isBusyStatus) {
                _TC.addBusyStatusMetric(metric.getName());
                _TC.addBusyStatusDestinationNameToMetricContextName(metric.getName(), busyStatusMetricName);
            }

            // Update DAG
            //_TC.addElementToNamePair(newMv, newMv.getName());
            _TC.getDAG().addTopLevelNode(newMv);
            _TC.getDAG().addNode(newMv, metric);
            log.trace("decomposeMetric: {}: New Required / Busy-Status metric added to DAG: {}", metricNamesKey.name(), newMv.getName());
        }

        return metric;
    }

    private RawMetricContext decomposeRawMetric(TranslationContext _TC, Map<String, Object> metricSpec, NamedElement parent) {
        log.debug("decomposeRawMetric: ARGS: spec: {}, parent: {}", metricSpec, parent);

        // Get needed fields
        String metricName = getSpecName(metricSpec);
        Map<String, Object> sensorSpec = asMap(metricSpec.get("sensor"));
        Map<String, Object> outputSpec = asMap(metricSpec.get("output"));

        NamesKey metricNamesKey = getNamesKey(metricSpec, metricName);

        // Update TC
        RawMetricContext rawMetric = RawMetricContext.builder()
                .name(metricNamesKey.name())
                .object(metricSpec)
                .build();
        _TC.getDAG().addNode(parent, rawMetric);

        // Process child blocks
        Schedule schedule = outputSpec!=null
                ? processSchedule(outputSpec, metricNamesKey) : null;
        Sensor sensor = sensorsHelper.processSensor(_TC, sensorSpec, metricNamesKey, rawMetric, schedule);

        // Complete TC update
        rawMetric.setSensor(sensor);
        rawMetric.setSchedule(schedule);
        rawMetric.setMetric(RawMetric.builder()
                .name(metricName + DEFAULT_METRIC_NAME_SUFFIX)
                .build());

        return rawMetric;
    }

    private CompositeMetricContext decomposeCompositeMetric(TranslationContext _TC, Map<String, Object> metricSpec, NamedElement parent) {
        log.debug("decomposeCompositeMetric: ARGS: spec: {}, parent: {}", metricSpec, parent);

        // Get needed fields
        String metricName = getSpecName(metricSpec);
        String formula = getMandatorySpecField(metricSpec, "formula", "Composite Metric '"+metricName+"' without 'formula': ");
        Map<String, Object> windowSpec = asMap(metricSpec.get("window"));
        Map<String, Object> outputSpec = asMap(metricSpec.get("output"));

        NamesKey metricNamesKey = getNamesKey(metricSpec, metricName);

        // Check formula and extract metrics
        if (StringUtils.isBlank(formula))
            throw createException("Composite metric 'formula' cannot be blank: at '"+metricNamesKey.name()+"': " + metricSpec);
        @NonNull Set<String> formulaArgs = MathUtil.getFormulaArguments(formula);
        log.trace("decomposeCompositeMetric: {}: formula={}, args={}", metricNamesKey.name(), formula, formulaArgs);

        // Remove constants and custom function names from 'formulaArgs'
        String containerName = getContainerName(metricSpec);
        formulaArgs.removeAll( $$(_TC).constants.keySet().stream()
                .filter(nk ->  nk.parent.equals(containerName))         // Check that all formula args are metrics under the same parent
                .map(nk->nk.child)
                .collect(Collectors.toSet()));
        formulaArgs.removeAll( $$(_TC).functionNames );
        log.trace("decomposeCompositeMetric: {}: After removing constants: formula={}, args={}", metricNamesKey.name(), formula, formulaArgs);

        // Update TC
        CompositeMetricContext compositeMetric = CompositeMetricContext.builder()
                .name(metricNamesKey.name())
                .object(metricSpec)
                .build();
        _TC.getDAG().addNode(parent, compositeMetric);

        // Decompose to metrics used in formula (i.e. composing or child metrics)
        // NOTE: child metric decomposition MUST be done before this metric has been altered in any way (or else
        //       hashCode() will return new value, different from that cached in the DAG)
        List<MetricContext> childMetricsList = new ArrayList<>();
        for (String childMetricName : formulaArgs) {
            NamesKey childMetricNamesKey = getNamesKey(metricSpec, childMetricName);
            Map<String, Object> childMetricSpec = asMap($$(_TC).allMetrics.get(childMetricNamesKey));
            log.trace("decomposeCompositeMetric: {}: Checking formula arg={} -- key: {}, spec: {}", metricNamesKey.name(), childMetricName, childMetricNamesKey, childMetricSpec);
            if (childMetricSpec==null)
                throw createException("Formula argument in metric '" + metricName + "' not found: "+childMetricName+", formula: " + formula);
            MetricContext childMetric = decomposeMetric(
                    _TC, childMetricSpec, metricNamesKey, compositeMetric);
            log.trace("decomposeCompositeMetric: {}: Checking formula arg={} -- childMetric: {}", metricNamesKey.name(), childMetricName, childMetric);
            if (childMetric!=null)
                childMetricsList.add(childMetric);
        }
        compositeMetric.setComposingMetricContexts(childMetricsList);

        // Process child blocks
        Window window = windowSpec!=null
                ? processWindow(windowSpec, metricNamesKey) : null;
        Schedule schedule = outputSpec!=null
                ? processSchedule(outputSpec, metricNamesKey) : null;

        // Complete TC update
        compositeMetric.setWindow(window);
        compositeMetric.setSchedule(schedule);
        compositeMetric.setMetric(CompositeMetric.builder()
                .name(metricName + DEFAULT_METRIC_NAME_SUFFIX)
                .formula(formula)
                .componentMetrics(childMetricsList.stream().map(MetricContext::getMetric).toList())
                .build());

        return compositeMetric;
    }

    private MetricContext processRef(TranslationContext _TC, Map<String, Object> metricSpec, NamesKey parentNamesKey, NamedElement parent) {
        // Get needed fields
        String refStr = getMandatorySpecField(metricSpec, "ref", "Metric reference must provide a value for 'ref' field: at metric '" + parentNamesKey.name() + "': " + metricSpec);

        // Process 'ref'
        refStr = refStr.replace("[", "").replace("]", "");
        NamesKey referencedNamesKey = createNamesKey(parentNamesKey, refStr);
        MetricContext referencedMetric = $$(_TC).metricsUsed.get(referencedNamesKey);
        if (referencedMetric==null) {
            Object spec = $$(_TC).allMetrics.get(referencedNamesKey);
            if (spec==null)
                throw createException("Cannot find referenced metric '"+referencedNamesKey.name()+"': " + metricSpec);
            referencedMetric = decomposeMetric(_TC, asMap(spec), parentNamesKey, parent);
            // 'decomposeMetric' will take care to add referencedMetric into the DAG
        } else {
            // add a new edge to the referencedMetric in the DAG
            _TC.getDAG().addNode(parent, referencedMetric);
        }

        return referencedMetric;
    }

    void processConstant(TranslationContext _TC, Map<String, Object> metricSpec, String parentName) {
        // Get needed fields
        String metricName = getSpecName(metricSpec);
        Double defaultValue = getSpecNumber(metricSpec, "default");

        NamesKey metricNamesKey = createNamesKey(parentName, metricName);

        // Register metric constant, and initial value (if available)
        $$(_TC).constants.put(metricNamesKey, defaultValue);
        if (defaultValue!=null)
            _TC.addConstantDefault(metricName, defaultValue);
    }

    private MetricTemplate processMetricTemplate(Map<String, Object> metricSpec, NamesKey parentNamesKey) {
        Object templateObj = metricSpec.get("template");
        if (templateObj instanceof Map templateSpec) {
            String id = getSpecField(templateSpec, "id");
            String type = getMandatorySpecField(templateSpec, "type",
                    "Metric template without 'type': at metric '" + parentNamesKey.name() + "': " + metricSpec);
            ValueType valueType = ValueType.valueOf(type.trim().toUpperCase() + "_TYPE");
            String dirStr = getSpecField(templateSpec, "direction");
            short valueDirection = StringUtils.isNotBlank(dirStr)
                    ? Short.parseShort(dirStr.trim()) : MetricTemplate.FORWARD_DIRECTION;
            List<Object> range = asList(templateSpec.get("range"));
            List<Object> values = asList(templateSpec.get("values"));
            String unit = getSpecField(templateSpec, "unit");

            if (range!=null && (range.size()!=2 || range.get(0)==null || range.get(1)==null))
                throw createException("Invalid range spec in metric template: "+metricSpec);
            if (range!=null && ! AnalysisUtils.isDoubleOrInfinity(range.get(0).toString()))
                throw createException("Invalid lower range bound in metric template: "+metricSpec);
            if (range!=null && ! AnalysisUtils.isDoubleOrInfinity(range.get(1).toString()))
                throw createException("Invalid upper range bound in metric template: "+metricSpec);
            double lowerBound = range!=null ? AnalysisUtils.getDoubleValue(range.get(0).toString().trim()) : Double.NEGATIVE_INFINITY;
            double upperBound = range!=null ? AnalysisUtils.getDoubleValue(range.get(1).toString().trim()) : Double.POSITIVE_INFINITY;
            if (lowerBound > upperBound)
                throw createException("Lower range bound > upper range bound in metric template: "+metricSpec);

            values = values!=null ? values.stream().filter(Objects::nonNull).toList() : null;
            if (values!=null && values.isEmpty()) values = null;

            return MetricTemplate.builder()
                    .object(templateSpec)
                    .name(id)
                    .valueType(valueType)
                    .valueDirection(valueDirection)
                    .unit(unit)
                    .lowerBound(lowerBound)
                    .upperBound(upperBound)
                    .allowedValues(values)
                    .build();
        }
        return null;
    }

    void processOrphanMetrics(TranslationContext _TC) {
        if (properties.isIncludeOrphanMetrics()) {
            HashSet<NamesKey> orphanMetrics = new HashSet<>($$(_TC).allMetrics.keySet());
            orphanMetrics.removeAll($$(_TC).metricsUsed.keySet());
            orphanMetrics.removeAll($$(_TC).constants.keySet());
            log.debug("Orphan metrics: {}", orphanMetrics);

            MetricVariable metricVar = MetricVariable.builder()
                    .name(properties.getOrphanMetricsParentName())
                    .build();
            _TC.getDAG().addTopLevelNode(metricVar);
            orphanMetrics.forEach(metricNamesKey -> {
                Map<String, Object> metricSpec = asMap($$(_TC).allMetrics.get(metricNamesKey));
                String parentName = getContainerName(metricSpec);
                NamesKey parentNamesKey = createNamesKey(parentName, "DUMMY");
                decomposeMetric(_TC, metricSpec, parentNamesKey, metricVar);
            });
        }
    }


    // ------------------------------------------------------------------------
    //  Window block processing methods
    // ------------------------------------------------------------------------

    private Window processWindow(Map<String, Object> windowSpec, NamesKey metricNamesKey) {
        // Get window type
        String typeStr = getMandatorySpecField(windowSpec, "type", "Window without 'type': at metric '"+metricNamesKey+"': ");
        WindowType windowType = WindowType.valueOf(typeStr.toUpperCase());

        // Get window size
        Map<String, Object> sizeMap = asMap(windowSpec.get("size"));
        String sizeTypeStr = getSpecField(sizeMap, "type");
        Object valueObj = sizeMap.get("value");
        String unitStr = getSpecField(sizeMap, "unit");

        // Check window size value
        if (!(valueObj instanceof Number) && !(valueObj instanceof String))
            throw createException("Window size value is mandatory and MUST be a positive integer: at metric '" + metricNamesKey + "': " + windowSpec);
        long value = valueObj instanceof Number n
                ? n.longValue() : Long.parseLong(valueObj.toString());
        if (value<=0)
            throw createException("Window size value cannot be zero or negative: at metric '" + metricNamesKey + "': " + windowSpec);

        // Check window size (time) unit
        ChronoUnit unit = StringUtils.isNotBlank(unitStr)
                ? normalizeTimeUnit(unitStr) : ChronoUnit.SECONDS;

        // Get or infer window size type
        WindowSizeType sizeType;
        if (StringUtils.isNotBlank(sizeTypeStr))
            sizeType = WindowSizeType.valueOf(sizeTypeStr.trim().toUpperCase());
        else
            sizeType = (unit !=null) ? WindowSizeType.TIME_ONLY : WindowSizeType.MEASUREMENTS_ONLY;

        // Initialize size parameters
        long measurementSize = -1;
        long timeSize = -1;
        String timeUnit = null;
        if (sizeType!=WindowSizeType.MEASUREMENTS_ONLY) {
            timeSize = value;
            timeUnit = unit.toString();
        }
        if (sizeType!=WindowSizeType.TIME_ONLY) {
            measurementSize = value;
        }

        // Process any 'processing' blocks
        List<Object> processingSpecs = asList(windowSpec.get("processing"));
        List<WindowProcessing> processingsList = null;
        if (processingSpecs!=null) {
            for (Object processingObj : processingSpecs) {
                WindowProcessing processing = processProcessing(asMap(processingObj), metricNamesKey);
                if (processingsList==null)
                    processingsList = new ArrayList<>();
                processingsList.add(processing);
            }
        }

        return Window.builder()
                .object(windowSpec)
                .windowType(windowType)
                .sizeType(sizeType)
                .measurementSize(measurementSize)
                .timeSize(timeSize)
                .timeUnit(timeUnit)
                .processings(processingsList)
                .subFeatures(getTranslationFeature(windowSpec))
                .build();
    }

    private WindowProcessing processProcessing(Map<String, Object> processingSpec, NamesKey metricNamesKey) {
        // Get processing type
        String typeStr = getMandatorySpecField(processingSpec, "type", "Window Processing without 'type': at metric '"+metricNamesKey+"': ");
        WindowProcessingType processingType = WindowProcessingType.valueOf(typeStr.trim().toUpperCase());

        // Process any 'criteria' blocks
        List<Object> criteriaSpecs = asList(processingSpec.get("criteria"));
        List<WindowCriterion> criteriaList = null;
        if (criteriaSpecs!=null) {
            for (Object criterionObj : criteriaSpecs) {
                WindowCriterion criterion = processCriterion(criterionObj, metricNamesKey);
                if (criteriaList==null)
                    criteriaList = new ArrayList<>();
                criteriaList.add(criterion);
            }
        }

        return WindowProcessing.builder()
                .object(processingSpec)
                .processingType(processingType)
                .groupingCriteria( processingType==WindowProcessingType.GROUP ? criteriaList : null )
                .rankingCriteria( processingType!=WindowProcessingType.GROUP ? criteriaList : null )
                .subFeatures(getTranslationFeature(processingSpec))
                .build();
    }

    private WindowCriterion processCriterion(Object criterionSpec, NamesKey metricNamesKey) {
        if (criterionSpec instanceof String typeStr) {
            CriterionType criterionType = CriterionType.valueOf(typeStr);
            if (criterionType!=CriterionType.CUSTOM)
                return WindowCriterion.builder()
                        .type(criterionType).build();
            else
                throw createException("Window Criterion of '"+criterionType+"' type cannot be a string: at metric '" + metricNamesKey + "': " + criterionSpec);
        }

        if (criterionSpec instanceof Map) {
            // Get processing type
            String typeStr = getMandatorySpecField(criterionSpec, "type", "Window Criterion without 'type': at metric '"+metricNamesKey+"': ");
            CriterionType criterionType = CriterionType.valueOf(typeStr);

            // Get 'custom' field (if CUSTOM)
            String custom = null;
            if (criterionType==CriterionType.CUSTOM) {
                custom = getMandatorySpecField(criterionSpec, "custom",
                        "CUSTOM window criterion must provide 'custom' field: at metric '" + metricNamesKey + "': ");
            }

            // Get sort direction (ascending, or descending)
            String ascStr = getSpecField(criterionSpec, "ascending");
            boolean isAscending = getBooleanValue(ascStr.trim(), true);

            return WindowCriterion.builder()
                    .object(criterionSpec)
                    .type(criterionType)
                    .custom(custom)
                    .ascending(isAscending)
                    .build();
        }

        throw createException("Window Criterion specification not supported: at metric '" + metricNamesKey + "': " + criterionSpec);
    }

    private List<Feature> getTranslationFeature(Map<String, Object> spec) {
        String eplStr = getSpecField(spec, "epl");
        String functionStr = getSpecField(spec, "function");
        if (StringUtils.isBlank(eplStr))
            eplStr = functionStr;

        if (StringUtils.isNotBlank(eplStr)) {
            Attribute eplAttr = Attribute.builder()
                    .name(RuleGenerator.EPL_VALUE)
                    .valueType(ValueType.STRING_TYPE)
                    .value(eplStr)
                    .build();
            List<Attribute> translationAttributes = List.of(eplAttr);

            Feature translationFeature = Feature.builder()
                    .name(RuleGenerator.TRANSLATION_CONFIG)
                    .attributes(translationAttributes)
                    .build();
            return List.of(translationFeature);
        }
        return null;
    }

    // ------------------------------------------------------------------------
    //  Schedule block processing methods
    // ------------------------------------------------------------------------

    private Schedule processSchedule(Map<String, Object> scheduleSpec, NamesKey metricNamesKey) {
        // Get needed fields
        String type = getSpecField(scheduleSpec, "type");
        if (StringUtils.isBlank(type)) type = "all";

        // Get 'schedule'
        Map<String, Object> schedMap = asMap(scheduleSpec.get("schedule"));

        // Get schedule value
        Object valueObj = schedMap.get("value");
        long interval;
        if (!(valueObj instanceof Number) && !(valueObj instanceof String))
            throw createException("Schedule value is mandatory and MUST be a positive integer: at metric '" + metricNamesKey.name() + "': " + scheduleSpec);
        else
            interval = (valueObj instanceof Number n) ? n.longValue() : Long.parseLong(valueObj.toString());
        if (interval<=0)
            throw createException("Schedule value cannot be zero or negative: at metric '" + metricNamesKey.name() + "': " + scheduleSpec);

        // Get schedule unit
        String unitStr = getSpecField(schedMap, "unit");
        ChronoUnit unit = StringUtils.isNotBlank(unitStr)
                ? normalizeTimeUnit(unitStr) : ChronoUnit.SECONDS;

        return Schedule.builder()
                .object(scheduleSpec)
                .interval(interval)
                .timeUnit(unit.toString())
                .type(Schedule.SCHEDULE_TYPE.valueOf(type.trim().toUpperCase()))
                .build();
    }
}