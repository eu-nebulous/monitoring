/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.plugins;

import eu.nebulous.ems.translate.NameNormalization;
import gr.iccs.imu.ems.control.plugin.PostTranslationPlugin;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.dag.DAGNode;
import gr.iccs.imu.ems.translate.model.*;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionsPostTranslationPlugin implements PostTranslationPlugin {
    public final static String PREDICTION_SLO_METRIC_DECOMPOSITION_MAP = "NEBULOUS_PREDICTION_SLO_METRIC_DECOMPOSITION_MAP";
    public final static String PREDICTION_SLO_METRIC_DECOMPOSITION = "NEBULOUS_PREDICTION_SLO_METRIC_DECOMPOSITION";
    public final static String PREDICTION_TOP_LEVEL_NODES_METRICS_MAP = "NEBULOUS_PREDICTION_TOP_LEVEL_NODES_METRICS_MAP";
    public final static String PREDICTION_TOP_LEVEL_NODES_METRICS = "NEBULOUS_PREDICTION_TOP_LEVEL_NODES_METRICS";

    private final NameNormalization nameNormalization;

    @PostConstruct
    public void created() {
        log.debug("PredictionsPostTranslationPlugin: CREATED");
    }

    @Override
    public void processTranslationResults(TranslationContext translationContext, TopicBeacon topicBeacon) {
        log.debug("PredictionsPostTranslationPlugin.processTranslationResults(): BEGIN");

        // PREDICTION_SLO_METRIC_DECOMPOSITION
        Map<String, Object> sloMetricDecompositionsMap = getSLOMetricDecompositionPayload(translationContext, topicBeacon);
        translationContext.getAdditionalResults().put(PREDICTION_SLO_METRIC_DECOMPOSITION_MAP, sloMetricDecompositionsMap);
        log.debug("PredictionsPostTranslationPlugin.processTranslationResults(): SLO metrics decompositions: model={}, decompositions-map={}",
                translationContext.getModelName(), sloMetricDecompositionsMap);

        String sloMetricDecompositionsStr = topicBeacon.toJson(sloMetricDecompositionsMap);
        translationContext.getAdditionalResults().put(PREDICTION_SLO_METRIC_DECOMPOSITION, sloMetricDecompositionsStr);
        log.debug("PredictionsPostTranslationPlugin.processTranslationResults(): SLO metrics decompositions: model={}, decompositions={}",
                translationContext.getModelName(), sloMetricDecompositionsStr);

        // PREDICTION_TOP_LEVEL_NODES_METRICS
        HashMap<String, Object> metricsOfTopLevelNodesMap = getMetricsForPredictionPayload(translationContext, topicBeacon);
        translationContext.getAdditionalResults().put(PREDICTION_TOP_LEVEL_NODES_METRICS_MAP, metricsOfTopLevelNodesMap);
        log.debug("PredictionsPostTranslationPlugin.processTranslationResults(): Metrics of Top-Level nodes of model: model={}, metrics-map={}",
                translationContext.getModelName(), metricsOfTopLevelNodesMap);

        String metricsOfTopLevelNodesStr = topicBeacon.toJson(metricsOfTopLevelNodesMap);
        translationContext.getAdditionalResults().put(PREDICTION_TOP_LEVEL_NODES_METRICS, metricsOfTopLevelNodesStr);
        log.debug("PredictionsPostTranslationPlugin.processTranslationResults(): Metrics of Top-Level nodes of model: model={}, metrics={}",
                translationContext.getModelName(), metricsOfTopLevelNodesMap);

        log.debug("PredictionsPostTranslationPlugin.processTranslationResults(): END");
    }

    // ------------------------------------------------------------------------

    /*public Set<String> getGlobalGroupingMetrics(TranslationContext translationContext) {
        // get all top-level nodes their component metrics
        final Set<DAGNode> nodes = new HashSet<>();
        final Deque<DAGNode> q = new ArrayDeque<>(translationContext.getDAG().getTopLevelNodes());
        while (!q.isEmpty()) {
            DAGNode node = q.pop();
            if (node.getGrouping()==Grouping.GLOBAL) {
                nodes.add(node);
                q.addAll(translationContext.getDAG().getNodeChildren(node));
            }
        }

        // return metric names
        return nodes.stream()
                .map(DAGNode::getElementName)
                .collect(Collectors.toSet());
    }*/

    public Map<String, Object> getSLOMetricDecompositionPayload(TranslationContext translationContext, TopicBeacon topicBeacon) {
        List<Object> slos = _getSLOMetricDecomposition(translationContext);
        if (slos.isEmpty()) {
            return null;
        }

        HashMap<String,Object> result = new HashMap<>();
        result.put("name", translationContext.getAppId());
        result.put("operator", "OR");
        result.put("constraints", slos);
        result.put("version", topicBeacon.getModelVersion());

        return result;
    }

    private @NonNull List<Object> _getSLOMetricDecomposition(TranslationContext translationContext) {
        // Get metric and logical constraints
        Map<String, MetricConstraint> mcMap = translationContext.getMetricConstraints().stream()
                .collect(Collectors.toMap(MetricConstraint::getName, mc -> mc));
        Map<String, LogicalConstraint> lcMap = translationContext.getLogicalConstraints().stream()
                .collect(Collectors.toMap(LogicalConstraint::getName, lc -> lc));
        Map<String, IfThenConstraint> itcMap = translationContext.getIfThenConstraints().stream()
                .collect(Collectors.toMap(IfThenConstraint::getName, ic -> ic));

        // Create map of top-level element names and instances
        Set<DAGNode> topLevelNodes = translationContext.getDAG().getTopLevelNodes();
        Map<String, DAGNode> topLevelNodesMap = topLevelNodes.stream()
                .collect(Collectors.toMap(DAGNode::getElementName, x -> x));

        // process each SLO
        List<Object> sloMetricDecompositions = new ArrayList<>();
        for (String sloName : translationContext.getSLO()) {
            DAGNode node = topLevelNodesMap.get(sloName);
            if (node.getElement() instanceof ServiceLevelObjective slo) {
                // get SLO constraint
                Constraint sloConstraint = slo.getConstraint();
                // SLO must contain exactly one constraint
                if (sloConstraint!=null) {
                    // decompose constraint
                    Object decomposition = _decomposeConstraint(sloConstraint, mcMap, lcMap, itcMap);
                    // cache decomposition
                    sloMetricDecompositions.add(decomposition);
                }
            }
        }

        return sloMetricDecompositions;
    }

    private Object _decomposeConstraint(Constraint constraintNode, Map<String, MetricConstraint> mcMap,
                                        Map<String, LogicalConstraint> lcMap, Map<String, IfThenConstraint> itcMap)
    {
        String elementName = constraintNode.getName();
        String elementClassName = constraintNode.getClass().getName();
        if (constraintNode instanceof MetricConstraint mc) {
            //MetricConstraint mc = mcMap.get(elementName);

            String metricName = mc.getMetricContext().getName();
            String metricTopic = nameNormalization.apply(metricName);
            return Map.of(
                    "name", nameNormalization.apply(mc.getName()),
                    "metric", metricTopic,
                    "operator", mc.getComparisonOperator().getOperator(),
                    "threshold", mc.getThreshold());
        } else
        if (constraintNode instanceof LogicalConstraint) {
            LogicalConstraint lc = lcMap.get(elementName);

            // decompose child constraints
            List<Object> list = new ArrayList<>();
            for (Constraint node : lc.getConstraints()) {
                Object o = _decomposeConstraint(node, mcMap, lcMap, itcMap);
                if (o!=null) list.add(o);
            }

            // create decomposition result
            Map<String,Object> result = new HashMap<>();
            result.put("name", nameNormalization.apply( lc.getName() ));
            result.put("operator", lc.getLogicalOperator());
            result.put("constraints", list);
            return result;
        } else
        if (constraintNode instanceof IfThenConstraint) {
            IfThenConstraint itc = itcMap.get(elementName);

            // decompose child constraints
            Object itcIf = _decomposeConstraint(itc.getIfConstraint(), mcMap, lcMap, itcMap);
            Object itcThen = _decomposeConstraint(itc.getThenConstraint(), mcMap, lcMap, itcMap);
            Object itcElse = _decomposeConstraint(itc.getElseConstraint(), mcMap, lcMap, itcMap);

            // create decomposition result
            Map<String,Object> result = new HashMap<>();
            result.put("name", nameNormalization.apply( itc.getName() ));
            result.put("if", itcIf);
            result.put("then", itcThen);
            result.put("else", itcElse);
            return result;
        } else
            log.warn("_decomposeConstraint: Unsupported Constraint type: {} {}", elementName, elementClassName);
        return null;
    }

    // ------------------------------------------------------------------------

    private HashMap<String, Object> getMetricsForPredictionPayload(@NonNull TranslationContext _TC, TopicBeacon topicBeacon) {
        HashSet<MetricContext> metricsOfTopLevelNodes = getMetricsForPrediction(_TC);
        if (metricsOfTopLevelNodes.isEmpty()) {
            return null;
        }

        // Convert to Translator-to-Forecasting Methods event format
        /*final long currVersion = topicBeacon.getModelVersion();
        final TopicBeaconProperties properties = topicBeacon.getProperties();
        final ValueRange allowedPredictionRateRange =
                ValueRange.of(properties.getPredictionMinAllowedRate(), properties.getPredictionMaxAllowedRate());
        List<HashMap<String, Object>> payload = metricsOfTopLevelNodes.stream().map(mc -> {
            HashMap<String, Object> map = new HashMap<>();
            map.put("metric", NebulousEmsTranslator.nameNormalization.apply( mc.getName() ));
            map.put("component", mc.getComponentName());
            map.put("level", 3);
            map.put("version", currVersion);
            map.put("publish_rate",
                    mc.getSchedule()!=null && allowedPredictionRateRange.isValidValue(mc.getSchedule().getIntervalInMillis())
                            ? mc.getSchedule().getIntervalInMillis()
                            : properties.getPredictionRate());
            return map;
        }).collect(Collectors.toList());
        log.debug("PredictionsPostTranslationPlugin: Metric Contexts in event format: {}", payload);

        // Skip event sending if payload is empty
        if (payload.isEmpty()) {
            return null;
        }*/

        // Convert to SLO Severity-based Violation Detector Event Type III format
        // See: https://158.39.75.54/projects/nebulous-collaboration-hub/wiki/slo-severity-based-violation-detector
        HashMap<String,Object> payload = new HashMap<>();
        payload.put("name", _TC.getAppId());
        payload.put("version", topicBeacon.getModelVersion());
        payload.put("metric_list",
                metricsOfTopLevelNodes.stream().map(mc -> {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("name", nameNormalization.apply( mc.getName() ));
                    MetricTemplate template = mc.getMetric().getMetricTemplate();
                    if (template!=null) {
                        switch (template.getValueType()) {
                            case FLOAT_TYPE, DOUBLE_TYPE, FloatType, DoubleType -> {
                                map.put("upper_bound", Double.toString(template.getUpperBound()));
                                map.put("lower_bound", Double.toString(template.getLowerBound()));
                            }
                            case INT_TYPE, IntType -> {
                                map.put("upper_bound", Integer.toString((int)template.getUpperBound()));
                                map.put("lower_bound", Integer.toString((int)template.getLowerBound()));
                            }
                            default -> {
                                log.warn("PredictionsPostTranslationPlugin: Metric Template type not supported. Will be ignored: type={}, metric={}",
                                        template.getValueType(), mc.getMetric());
                                return null;
                            }
                        }
                    } else {
                        map.put("upper_bound", Double.toString(Double.POSITIVE_INFINITY));
                        map.put("lower_bound", Double.toString(Double.NEGATIVE_INFINITY));
                    }
                    return map;
                })
                .filter(Objects::nonNull)
                .toList() );

        return payload;
    }

    private @NonNull HashSet<MetricContext> getMetricsForPrediction(@NonNull TranslationContext _TC) {
        // Process DAG top-level nodes
        if (_TC.getDAG()==null)
            throw new IllegalStateException("PredictionsPostTranslationPlugin: TranslationContext with 'null' DAG passed");
        Set<DAGNode> topLevelNodes = _TC.getDAG().getTopLevelNodes();
        HashSet<MetricContext> tcMetricsOfTopLevelNodes = new HashSet<>();

        final Deque<DAGNode> q = topLevelNodes.stream()
                .filter(x ->
                        x.getElement() instanceof ServiceLevelObjective ||
                        x.getElement() instanceof Metric)
                .distinct()
                .collect(Collectors.toCollection(ArrayDeque::new));

        while (!q.isEmpty()) {
            DAGNode node = q.pop();
            if (node.getElement() instanceof MetricContext) {
                MetricContext metricContext = (node.getElement() instanceof MetricContext mc) ? mc : null;
                tcMetricsOfTopLevelNodes.add(metricContext);
            } else {
                Set<DAGNode> children = _TC.getDAG().getNodeChildren(node);
                if (children!=null) q.addAll(children);
            }
        }
        return tcMetricsOfTopLevelNodes;
    }
}
