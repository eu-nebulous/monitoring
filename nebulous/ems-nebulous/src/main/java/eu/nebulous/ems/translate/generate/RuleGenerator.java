/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.generate;

import eu.nebulous.ems.translate.NameNormalization;
import eu.nebulous.ems.translate.analyze.AnalysisUtils;
import gr.iccs.imu.ems.brokercep.cep.MathUtil;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.model.*;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleGenerator implements InitializingBean {
    public final static String TRANSLATION_CONFIG = "translation_config";
    public final static String EPL_VALUE = "epl_value";
    public final static String EPL_FORMULA_TAG = "%EPL%>";

    private final NebulousEmsTranslatorProperties properties;
    private final RuleTemplateRegistry ruleTemplatesRegistry;
    private final NameNormalization nameNormalization;
    private SpringTemplateEngine templateEngine;

    // ========================================================================
    // Public API
    // ========================================================================

    @Override
    public void afterPropertiesSet() {
        initTemplateEngine();
    }

    public void generateRules(TranslationContext _TC) {
        log.debug("RuleGenerator.ruleTemplates:\n{}", ruleTemplatesRegistry.getRuleTemplates());
        _generateRules(_TC);
        _TC.getTopicConnections();  // force topicConnections population
        _updateMonitors(_TC);
    }

    // ========================================================================
    // Initialization methods
    // ========================================================================

    private void initTemplateEngine() {
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        SpringStandardDialect dialect = new SpringStandardDialect();
        dialect.setEnableSpringELCompiler(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setDialect(dialect);
        engine.setEnableSpringELCompiler(true);
        engine.setTemplateResolver(templateResolver);

        this.templateEngine = engine;
        log.debug("RuleGenerator.initTemplateEngine(): Template engine initialized: {}", engine.getClass().getName());
    }

    // ========================================================================
    // Rule generation methods
    // ========================================================================

    private void _generateRules(TranslationContext _TC) {
        // traverse DAG and generate EPL rules
        log.debug("RuleGenerator.generateRules(): Traversing DAG...");
        _TC.getDAG().traverseDAG(node -> {
            String grouping = node.getGrouping() != null ? node.getGrouping().toString() : null;
            NamedElement elem = node.getElement();
            String elemName = elem != null ? getElemNameNormalized(elem) : null;
            node.setTopicName(elemName);
            Class<?> elemClass = elem != null ? elem.getClass() : null;
            log.debug("RuleGenerator.generateRules():  node: {}, grouping={}, elem-name={}, elem-class={}", node, grouping, elemName, elemClass);

            // Generate rules depending on the type of element
            boolean providesTopic = true;
            if (elem == null) {
                // ignore this element
                log.debug("RuleGenerator.generateRules():      IGNORE NODE. No element specified: node={}", node);
                providesTopic = false;
            } else
            // Generate rules for Events and Event Patterns (and Metric Constraints)
            /*if (elem instanceof BinaryEventPattern bep) {
                log.debug("RuleGenerator.generateRules():      Found a Binary-Event-Pattern element: node={}, elem-name={}", node, elemName);

                // Get event pattern operator
                String camelOp = bep.getOperator().toString();
                String ruleOp = camelToRule(MapType.OPERATOR, ElemType.BEP, camelOp);

                // Get event pattern composing events (just the names)
                String leName = bep.getLeftEvent().getName();
                String reName = bep.getRightEvent().getName();
                log.debug("RuleGenerator.generateRules():      Binary-Event-Pattern: node={}, elem-name={}, operator={}->{}, left-event={}, right-event={}", node, elemName, camelOp, ruleOp, leName, reName);

//XXX: ASK: Do we need 'lowOccurrenceBound', 'upperOccurrenceBound' and 'timer' ??

                // Write rule for BEP
                Context context = new Context();
                context.setVariable("operator", ruleOp);
                context.setVariable("leftEvent", leName);
                context.setVariable("rightEvent", reName);
                _generateRule(_TC, "BEP-" + ruleOp, grouping, elem, context);
            } else if (elem instanceof UnaryEventPattern uep) {
                log.debug("RuleGenerator.generateRules():      Found a Unary-Event-Pattern element: node={}, elem-name={}", node, elemName);

                // Get event pattern operator
                String camelOp = uep.getOperator().toString();
                String ruleOp = camelToRule(MapType.OPERATOR, ElemType.UEP, camelOp);

                // Get event pattern composing event (just the name)
                String eventName = uep.getEvent().getName();
                log.debug("RuleGenerator.generateRules():      Unary-Event-Pattern: node={}, elem-name={}, operator={}->{}, event={}", node, elemName, camelOp, ruleOp, eventName);

//XXX: ASK: Do we need 'occurrenceNum' and 'timer' ??

                // Write rule for UEP
                Context context = new Context();
                context.setVariable("operator", ruleOp);
                context.setVariable("event", eventName);
                _generateRule(_TC, "UEP-" + ruleOp, grouping, elem, context);
            } else if (elem instanceof NonFunctionalEvent nfe) {
                log.debug("RuleGenerator.generateRules():      Found a Non-Functional-Event element: node={}, elem-name={}", node, elemName);

                // Get event is-violation
                boolean isViolation = nfe.isViolation();

                // Get event's metric constraint (just the name)
                MetricConstraint constr = nfe.getMetricConstraint();
                log.debug("RuleGenerator.generateRules():      Non-Functional-Event: node={}, elem-name={}, is-violation={}, metric-constraint={}", node, elemName, isViolation, constr.getName());

                // Write rule for NFE
                Context context = new Context();
                context.setVariable("metricConstraint", constr.getName());
                _generateRule(_TC, "NFE", grouping, elem, context);
            } else*/

            // Generate rules for Constraints
            if (elem instanceof MetricConstraint) {
                log.debug("RuleGenerator.generateRules():      Found a Metric-Constraint element: node={}, elem-name={}", node, elemName);
                MetricConstraint constr = (MetricConstraint) elem;

                // Get metric constraint operator and threshold
                String camelOp = constr.getComparisonOperator().toString();
                String ruleOp = camelToRule(MapType.OPERATOR, ElemType.CONSTR, camelOp);
                double threshold = constr.getThreshold();

                // Get constraint's metric context (just the name)
                MetricContext mc = constr.getMetricContext();
                String mcName = getElemNameNormalized(mc);
                log.debug("RuleGenerator.generateRules():      Metric-Constraint: node={}, elem-name={}, operator={}->{}, threshold={}, metric-context={}",
                        node, elemName, camelOp, ruleOp, threshold, mcName);

                // Require context topic in this level
                _TC.requireGroupingTopicPair(grouping, mcName);

                // Write rule for CONSTR-MET
                Context context = new Context();
                context.setVariable("metricContext", mcName);
                context.setVariable("operator", ruleOp);
                context.setVariable("threshold", threshold);
                _generateRule(_TC, "CONSTR-MET", grouping, elem, context);
            } else if (elem instanceof IfThenConstraint) {
                log.debug("RuleGenerator.generateRules():      Found an If-Then-Constraint element: node={}, elem-name={}", node, elemName);
                IfThenConstraint constr = (IfThenConstraint) elem;

                // Get constraint If, Then, Else child constraints
                String ifConstr = getElemNameNormalized(constr.getIf());
                String thenConstr = getElemNameNormalized(constr.getThen());
                String elseConstr = constr.getElse()!=null ? getElemNameNormalized(constr.getElse()) : null;

                log.debug("RuleGenerator.generateRules():      If-Then-Constraint: node={}, elem-name={}, If={}, Then={}, Else={}",
                        node, elemName, ifConstr, thenConstr, elseConstr);

                // Require context topic in this level
                _TC.requireGroupingTopicPair(grouping, getElemNameNormalized(constr));

                // Write rule for CONSTR-IF-THEN
                Context context = new Context();
                context.setVariable("ifConstraint", ifConstr);
                context.setVariable("thenConstraint", thenConstr);
                context.setVariable("elseConstraint", elseConstr);
                _generateRule(_TC, "CONSTR-IF-THEN", grouping, elem, context);
            } else if (elem instanceof MetricVariableConstraint) {
                // Not used in EMS
                log.debug("RuleGenerator.generateRules():      Found an Metric-Variable-Constraint element and ignoring it: node={}, elem-name={}", node, elemName);
            } else if (elem instanceof LogicalConstraint) {
                log.debug("RuleGenerator.generateRules():      Found a Logical-Constraint element: node={}, elem-name={}", node, elemName);
                LogicalConstraint constr = (LogicalConstraint) elem;

                // Get logical constraint operator and component constraints
                String camelOp = constr.getLogicalOperator().name();
                String ruleOp = camelToRule(MapType.OPERATOR, ElemType.CONSTR, camelOp);
                List<String> componentConstraintsNamesList = constr.getConstraints().stream()
                        .map(this::getElemNameNormalized).collect(Collectors.toList());

                log.debug("RuleGenerator.generateRules():      Logical-Constraint: node={}, elem-name={}, operator={}->{}, component-constraints={}", node, elemName, camelOp, ruleOp, componentConstraintsNamesList);

                // Require context topic in this level
                _TC.requireGroupingTopicPair(grouping, getElemNameNormalized(constr));

                // Write rule for CONSTR-LOG
                Context context = new Context();
                context.setVariable("operator", ruleOp);
                context.setVariable("constraints", componentConstraintsNamesList);
                _generateRule(_TC, "CONSTR-LOG", grouping, elem, context);
            } else

            // Generate rules for Metrics Contexts
            if (elem instanceof CompositeMetricContext cmc) {
                log.debug("RuleGenerator.generateRules():      Found a Composite-Metric-Context element: node={}, elem-name={}", node, elemName);

                // Get composite metric context's metric parameters
                CompositeMetric metric = (CompositeMetric) cmc.getMetric();
                String formula = metric.getFormula();
                List<Metric> components = metric.getComponentMetrics();
                List<String> componentNames = components.stream().map(this::getElemNameNormalized).collect(Collectors.toList());

                boolean isAggregation = MathUtil.containsAggregator(formula);

                // Get composite metric context's window and schedule parameters
                Window win = cmc.getWindow();
                String winClause = _generateWindowClause(win);
                Schedule sched = cmc.getSchedule();
                String schedClause = _generateScheduleClause(sched, isAggregation ? "AGG" : null);

                // Get composite metric context's component or data parameters
                String[] compAndDataName = getComponentAndDataName(cmc);
                String compName = compAndDataName[0];
                String dataName = compAndDataName[1];

                // Get composite metric context's composing metric contexts (just the names)
                List<MetricContext> composingCtxList = cmc.getComposingMetricContexts();
                List<String> composingMetricNamesList = composingCtxList.stream()
                        //XXX:TODO: LOW-PRI: Improve by using formula rewrite in order to include component name too
                        //XXX:TODO: LOW-PRI: (e.g. 'mean([face-detection.latency_instance])' to 'mean( face_detection_latency__instance )' )
                        .map(item -> StringUtils.defaultIfBlank(
                                StringUtils.substringAfterLast(item.getName(), "."),
                                item.getName()))
                        //.map(item -> getElemName(item.getMetric()))
                        .collect(Collectors.toList());
                List<String> composingCtxNamesList = composingCtxList.stream().map(this::getElemNameNormalized).collect(Collectors.toList());

                // Check that component metrics' names (from composite metric) and metric names from component contexts match
                if (checkIfListsAreEqual(componentNames, composingCtxNamesList)) {
                    log.error("RuleGenerator.generateRules():      Component metrics of composite metric '{}' do not match to component contexts of Composite-Metric-Context '{}': component-metrics={}, component-context-metrics={}",
                            getElemNameNormalized(metric), getElemNameNormalized(cmc), componentNames, composingCtxNamesList);
                    throw new IllegalArgumentException(String.format("Component metrics of composite metric '%s' do not match to component contexts of Composite-Metric-Context: %s", metric.getName(), cmc.getName()));
                }

                log.debug("RuleGenerator.generateRules():      Composite-Metric-Context: node={}, elem-name={}, metric={}, formula={}, components={}, win={}, sched={}, component={}, data={}, composing-metrics={}, composing-metric-contexts={}",
                        node, elemName, metric.getName(), formula, componentNames, winClause, schedClause, compName, dataName, composingMetricNamesList, composingCtxNamesList);

                // Require topics in this level
                _TC.requireGroupingTopicPairs(grouping, composingCtxNamesList);

                // Select rule tag, depending on whether an Aggregator function is used in formula
                // (a) COMP-CTX: when no Aggregator function is used in formula, (b) COMP-CTX-AGG: when an Aggregator function is used in formula
                String ruleTag = "COMP-CTX";
                if (isAggregation) ruleTag = "AGG-COMP-CTX";
                log.debug("RuleGenerator.generateRules():      CMC-tag={}", ruleTag);

                // Write rule for CMC or CMC-AGG
                Context context = new Context();
                context.setVariable("formula", formula);
                context.setVariable("metric", getElemNameNormalized(metric));
                context.setVariable("components", composingMetricNamesList);
                context.setVariable("contexts", composingCtxNamesList);
                context.setVariable("windowClause", winClause);
                context.setVariable("scheduleClause", schedClause);
                _generateRule(_TC, ruleTag, grouping, elem, context);
            } else if (elem instanceof RawMetricContext rmc) {
                log.debug("RuleGenerator.generateRules():      Found a Raw-Metric-Context element: node={}, elem-name={}", node, elemName);

                // Get raw metric context's metric parameters
                RawMetric metric = (RawMetric) rmc.getMetric();
                Sensor sensor = rmc.getSensor();
                String sensorName = sensor != null ? getElemNameNormalized(sensor) : null;

                // Get raw metric context's schedule parameters
                Schedule sched = rmc.getSchedule();
                String schedClause = _generateScheduleClause(sched, null);

                // Get raw metric context's component or data parameters
                String[] compAndDataName = getComponentAndDataName(rmc);
                String compName = compAndDataName[0];
                String dataName = compAndDataName[1];

                log.debug("RuleGenerator.generateRules():      Raw-Metric-Context: node={}, elem-name={}, metric={}, sensor={}, sched={}, component={}, data={}",
                        node, elemName, metric.getName(), sensorName, schedClause, compName, dataName);

                // Require topics in this level
                _TC.requireGroupingTopicPair(grouping, getElemNameNormalized(rmc));

                // Write rule for RMC
                Context context = new Context();
                context.setVariable("metric", getElemNameNormalized(metric));
                context.setVariable("sensor", sensorName);
                context.setVariable("scheduleClause", schedClause);
                _generateRule(_TC, "RAW-CTX", grouping, elem, context);
            } else
            if (elem instanceof AsIsMetricContext aimc) {
                log.debug("RuleGenerator.generateRules():      Found an As-Is-Metric-Context element: node={}, elem-name={}", node, elemName);

                // Get as-is metric context's metric parameters
                AsIsMetric metric = (AsIsMetric) aimc.getMetric();
                String dialect = metric.getDialect();
                String definition = metric.getDefinition();
                List<Metric> childMetrics = metric.getComposingMetrics();
                List<String> childMetricNames = childMetrics.stream().map(this::getElemNameNormalized).collect(Collectors.toList());

                // Get composite metric context's component or data parameters
                String[] compAndDataName = getComponentAndDataName(aimc);
                String compName = compAndDataName[0];
                String dataName = compAndDataName[1];

                // Get as-is metric context's composing metric contexts (just the names)
                List<MetricContext> composingCtxList = aimc.getComposingMetricContexts();
                List<String> composingMetricNamesList = composingCtxList.stream()
                        //XXX:TODO: LOW-PRI: Improve by using formula rewrite in order to include component name too
                        //XXX:TODO: LOW-PRI: (e.g. 'mean([face-detection.latency_instance])' to 'mean( face_detection_latency__instance )' )
                        .map(item -> StringUtils.defaultIfBlank(
                                StringUtils.substringAfterLast(item.getName(), "."),
                                item.getName()))
                        //.map(item -> getElemName(item.getMetric()))
                        .collect(Collectors.toList());
                List<String> composingCtxNamesList = composingCtxList.stream().map(this::getElemNameNormalized).collect(Collectors.toList());

                // Check that component metrics' names (from as-is metric) and metric names from component contexts match
                if (checkIfListsAreEqual(childMetricNames, composingCtxNamesList)) {
                    log.error("RuleGenerator.generateRules():      Composing metrics of as-is metric '{}' do not match to composing contexts of As-Is-Metric-Context '{}': composing-metrics={}, composing-context-metrics={}",
                            getElemNameNormalized(metric), getElemNameNormalized(aimc), childMetricNames, composingCtxNamesList);
                    throw new IllegalArgumentException(String.format("Composing metrics of as-is metric '%s' do not match to composing contexts of As-Is-Metric-Context: %s", metric.getName(), aimc.getName()));
                }

                log.debug("RuleGenerator.generateRules():      As-Is-Metric-Context: node={}, elem-name={}, metric={}, dialect={}, definition={}, child-metrics={}, component={}, data={}, composing-metrics={}, composing-metric-contexts={}",
                        node, elemName, metric.getName(), dialect, definition, childMetricNames, compName, dataName, composingMetricNamesList, composingCtxNamesList);

                // Require topics in this level
                _TC.requireGroupingTopicPairs(grouping, composingCtxNamesList);

                // Write rule for AIMC
                Context context = new Context();
                context.setVariable("definition", definition);
                context.setVariable("metric", getElemNameNormalized(metric));
                _generateRule(_TC, "AS-IS-CTX", grouping, elem, context);

            } else

            // Generate rules for Metrics
            /*if ((elem instanceof RawMetric || elem instanceof CompositeMetric) && _TC.getDAG().isTopLevelNode(node)) {
                log.debug("RuleGenerator.generateRules():      Found a Top-Level Metric element: node={}, elem-name={}", node, elemName);
                Metric m = (Metric) elem;

                // Get metric's context
                Set<MetricContext> mc = _TC.getM2MC().get(m);
                List<String> mcList = mc.stream().map(NamedElement::getName).collect(Collectors.toList());
                if (mc.size() != 1) {
                    log.error("RuleGenerator.generateRules():      Top-Level Metric has 0 or >1 contexts: metric={}, component-contexts={}", m.getName(), mcList);
                    throw new IllegalArgumentException(String.format("Top-Level Metric has 0 or >1 contexts: metric=%s, component-contexts=%s", m.getName(), mcList));
                }
                MetricContext cmc = mc.stream().findFirst().get();

                log.debug("RuleGenerator.generateRules():      Top-Level Metric: node={}, elem-name={}, context={}",
                        node, elemName, cmc.getName());

                // Require topics in this level
                _TC.requireGroupingTopicPair(grouping, cmc.getName());

                // Write rule for MET
                Context context = new Context();
                context.setVariable("context", cmc.getName());
                _generateRule(_TC, "TL-MET", grouping, elem, context);

            } else if (elem instanceof CompositeMetric) {
                log.debug("RuleGenerator.generateRules():      Found a Composite-Metric element: node={}, elem-name={}", node, elemName);
                providesTopic = false;
                // Nothing to do here
            } else if (elem instanceof RawMetric) {
                log.debug("RuleGenerator.generateRules():      Found a Raw-Metric element: node={}, elem-name={}", node, elemName);
                providesTopic = false;
                // Nothing to do here
            } else*/

            // Generate rules for Metric Variables
            if (elem instanceof BusyStatusMetricVariable) {
                log.debug("RuleGenerator.generateRules():      Found a BUSY-STATUS metric variable element: node={}, elem-name={}", node, elemName);
                MetricVariable mvar = (BusyStatusMetricVariable) elem;
                Component comp = mvar.getComponent();
                String compName = comp != null ? comp.getName() : null;

                MetricContext busyStatusMetricContext = mvar.getMetricContext();
                String busyStatusMetricContextName = getElemNameNormalized(busyStatusMetricContext);

                log.debug("RuleGenerator.generateRules():      BUSY-STATUS metric variable: node={}, elem-name={}, component={}, metric-context={}",
                        node, elemName, compName, busyStatusMetricContextName);

                // Require topics in this level
                _TC.requireGroupingTopicPair(grouping, busyStatusMetricContextName);

                // Write rule for BUSY-STATUS Metric Variable
                Context context = new Context();
                context.setVariable("context", busyStatusMetricContextName);
                _generateRule(_TC, "BUSY-STATUS-VAR", grouping, elem, context);

            } else
            /*if (elem instanceof MetricVariable mvar) {
                log.debug("RuleGenerator.generateRules():      Found a Metric-Variable element: node={}, elem-name={}", node, elemName);
                boolean isCurrConfig = mvar.isCurrentConfiguration();
                boolean isOnNodeCand = mvar.isOnNodeCandidates();
                Component comp = mvar.getComponent();
                String compName = comp != null ? comp.getName() : null;
                String formula = mvar.getFormula();
                List<Metric> _componentMetrics = mvar.getComponentMetrics();
                List<String> _componentMetricNames = _componentMetrics.stream().map(NamedElement::getName).collect(Collectors.toList());
                boolean _containsMetrics = _componentMetrics.size() > 0;

                log.debug("RuleGenerator.generateRules():      Metric-Variable: node={}, elem-name={}, is-current-config={}, is-on-node-candidates={}, component={}, formula={}, component-metrics={}, contains-metrics={}",
                        node, elemName, isCurrConfig, isOnNodeCand, compName, formula, _componentMetricNames, _containsMetrics);

                // Remove CP model variables from component metrics
                List<Metric> componentMetrics = new ArrayList<>();
                for (Metric m : _componentMetrics) {
                    if (m instanceof MetricVariable) {
                        if (CamelMetadataTool.isFromVariable(m.getObject(MetricVariableImpl.class))) {
                            log.debug("RuleGenerator.generateRules():        - CP model variable found and will be excluded from processing: {}", m.getName());
                            continue;
                        }
                    }
                    componentMetrics.add(m);
                }
                List<String> componentMetricNames = componentMetrics.stream().map(NamedElement::getName).collect(Collectors.toList());
                boolean containsMetrics = (componentMetrics.size() > 0);

                log.debug("RuleGenerator.generateRules():      Metric-Variable after removing CP model variables: node={}, elem-name={}, is-current-config={}, is-on-node-candidates={}, component={}, formula={}, component-metrics={}, contains-metrics={}",
                        node, elemName, isCurrConfig, isOnNodeCand, compName, formula, componentMetricNames, containsMetrics);

                // Select rule tag, depending on whether an Aggregator function is used in formula
                // (a) VAR: when no Aggregator function is used in formula, (b) VAR-AGG: when an Aggregator function is used in formula
                String ruleTag = "VAR";
                if (MathUtil.containsAggregator(formula)) ruleTag = "AGG-VAR";
                log.debug("RuleGenerator.generateRules():      VAR-tag={}", ruleTag);

                if (componentMetrics.size() > 0) {
                    // Write rule for VAR
                    List<MetricContext> contexts = componentMetrics.stream().map(_TC::getMetricContextForMetric).filter(Objects::nonNull).toList();
                    List<String> metricNames = contexts.stream().map(item -> item.getMetric().getName()).collect(Collectors.toList());
                    List<String> contextNames = contexts.stream().map(NamedElement::getName).collect(Collectors.toList());
                    log.debug("RuleGenerator.generateRules():      Metric-Variable: node={}, elem-name={}, component-metrics={}, component-metric-contexts={}",
                            node, elemName, metricNames, contextNames);

                    // Check that component metrics' names (from metric variable) and metric names from component contexts match
                    if (! checkIfListsAreEqual(componentMetricNames, metricNames)) {
                        log.error("RuleGenerator.generateRules():      Component metrics of metric variable '{}' do not match to component contexts' metrics: component-metrics={}, component-context-metrics={}", mvar.getName(), componentMetricNames, metricNames);
                        throw new IllegalArgumentException(String.format("Component metrics of metric variable '%s' do not match to component contexts' metrics", mvar.getName()));
                    }

                    if (contexts.size() > 0) {
                        // Require topics in this level
                        _TC.requireGroupingTopicPairs(grouping, contextNames);

                        // Write rule for VAR
                        Context context = new Context();
                        context.setVariable("formula", formula);
                        context.setVariable("variable", mvar.getName());
                        context.setVariable("components", metricNames);
                        context.setVariable("contexts", contextNames);
                        _generateRule(_TC, ruleTag, grouping, elem, context);
                    } else {
                        // All component metrics for this metric variable do not have related metric contexts (i.e. the metric variable is MVV)
                        // No rules will be generated
                        log.debug("RuleGenerator.generateRules():      Metric-Variable has no metric contexts related to its component metrics. No rules will be generated: node={}, elem-name={}", node, elemName);
                    }
                } else {
                    // No component metrics for this metric variable (i.e. it is a MVV)
                    // No rules will be generated
                    log.debug("RuleGenerator.generateRules():      Metric-Variable has no component metrics. No rules will be generated: node={}, elem-name={}", node, elemName);
                }
            } else*/

            // Generate rules for Templates and Sensors
            /*if (elem instanceof MetricTemplate) {
                log.debug("RuleGenerator.generateRules():      Found a Metric-Template element: node={}, elem-name={}", node, elemName);
            } else*/
            if (elem instanceof Sensor) {
                log.debug("RuleGenerator.generateRules():      Found a Sensor element: node={}, elem-name={}", node, elemName);
            } else

            // Generate rules for Optimisation Requirements and SLO's
            /*if (elem instanceof OptimisationRequirement optr) {
                log.debug("RuleGenerator.generateRules():      Found an Optimisation-Requirement element: node={}, elem-name={}", node, elemName);
                MetricContext mc = optr.getMetricContext();
                MetricVariable mv = optr.getMetricVariable();

                // Write rules for OPT-REQ
                if (mc != null) {
                    Context context = new Context();
                    context.setVariable("context", mc.getName());
                    _generateRule(_TC, "OPT-REQ-CTX", grouping, elem, context);
                }
                if (mv != null) {
                    Context context = new Context();
                    context.setVariable("variable", mv.getName());
                    _generateRule(_TC, "OPT-REQ-VAR", grouping, elem, context);
                }
            } else*/
            if (elem instanceof ServiceLevelObjective slo) {
                log.debug("RuleGenerator.generateRules():      Found a Service-Level-Objective element: node={}, elem-name={}", node, elemName);
                Constraint constr = slo.getConstraint();

                // Write rule for SLO
                Context context = new Context();
                context.setVariable("constraint", getElemNameNormalized(constr));
                _generateRule(_TC, "SLO", grouping, elem, context);
            } else

            {
                log.debug("RuleGenerator.generateRules():      ERROR in NODE. Unsupported element type: node={}, elem-name={}, elem-class={}", node, elemName, elemClass);
                providesTopic = false;
            }

            // Add provided topic (i.e. this node generates the rule(s) that will create the events to be published in topic)
            if (providesTopic) {
                _TC.provideGroupingTopicPair(grouping, getElemNameNormalized(elem));
            }
        });
        log.debug("RuleGenerator.generateRules(): Traversing DAG... done");
    }

    protected String getElemNameNormalized(NamedElement elem) {
        return getElemNameNormalized(elem.getName());
    }

    protected String getElemNameNormalized(String name) {
        return nameNormalization.apply(name);
    }

    protected void _generateRule(TranslationContext _TC, String type, String grouping, NamedElement elem, Context context) {
        String elemName = getElemNameNormalized(elem);

        // Check for sub-feature attribute 'translation_config.epl_value'. If it exists then the rule EPL statement is
        // taken from its value (provided it is not null or blank, and it is a string value).
        // Further rule generation actions are skipped.
        log.trace("RuleGenerator._generateRule():      Checking if element is a feature: {} -- {}", elemName, elem.getClass());
        if (elem instanceof Feature) {
            log.trace("RuleGenerator._generateRule():      Checking element for overriding translation sub-feature: {}", elemName);
            String eplStmt = getEplValueFromSubfeatures((Feature) elem);
            log.trace("RuleGenerator._generateRule():      Element overriding translation sub-feature: {} -- sub-feature: {}", elemName, eplStmt);

            if (StringUtils.isNotBlank(eplStmt)) {
                log.debug("RuleGenerator._generateRule():      Element '{}' has '{}' set. EPL statement: {}", elemName, EPL_VALUE, eplStmt = eplStmt.trim());

                // Store the generated rule in _TC
                _TC.addGroupingRulePair(grouping, elemName, eplStmt);
                log.debug("RuleGenerator._generateRule():      + Added EPL statement at Grouping {}: {}", grouping, eplStmt);
                log.trace("RuleGenerator._generateRule():      Skipping further element rule processing: {}", elemName);
                return;
            }
        }

        // Set selectMode based on 'formula's initial tag
        String fml = Optional.ofNullable(context.getVariable("formula")).orElse("").toString();
        if (StringUtils.startsWithIgnoreCase(fml, EPL_FORMULA_TAG)) {
            fml = fml.substring(EPL_FORMULA_TAG.length());
            context.setVariable("formula", fml);
            context.setVariable("selectMode", "epl");
        }

        // Generate rule EPL statement using the configured templates
        log.debug("RuleGenerator._generateRule():      Generating rules for Graph node: {} {} at Grouping: {}", type, elemName, grouping != null ? grouping : "-");
        String[] groupingLabels = {grouping, "__ANY__"};
        for (String label : groupingLabels) {
            log.debug("RuleGenerator._generateRule():      Getting rule templates for: type={}, grouping={}", type, label);
            for (String ruleTpl : ruleTemplatesRegistry.getTemplatesFor(type, label)) {
                log.debug("RuleGenerator._generateRule():      Rule template for: type={}, grouping={} => {}", type, label, ruleTpl);
                if (ruleTpl != null) {
                    // Use template engine to process the selected rule template
                    context.setVariable("outputStream", elemName);
                    String ruleStr = templateEngine.process(ruleTpl.trim(), context);

                    // Store the generated rule in _TC
                    _TC.addGroupingRulePair(grouping, elemName, ruleStr);
                    log.debug("RuleGenerator._generateRule():      + Added rule at Grouping {}: {}", grouping, ruleStr);
                } else {
                    log.warn("RuleGenerator._generateRule():      - No rule template found for '{}' at Grouping '{}': node={}", type, grouping, elemName);
                }
            }
        }
    }

    private static String getEplValueFromSubfeatures(Feature feature) {
        List<Feature> subFeaturesList = feature.getSubFeatures();
        if (subFeaturesList==null || subFeaturesList.size()==0) return null;
        String result = feature.getSubFeatures().stream()
                .peek(p->log.trace("RuleGenerator.getEplViewFromSubfeatures(): ..... feature--BEFORE-FILTER: {}", p.getName()))
                .filter(f -> TRANSLATION_CONFIG.equals(f.getName()))
                .peek(p->log.trace("RuleGenerator.getEplViewFromSubfeatures(): ..... feature--AFTER-FILTER: {}", p.getName()))
                .map(Feature::getAttributes)
                .filter(Objects::nonNull)
                .filter(a->a.size()>0)
                .peek(a->log.trace("RuleGenerator.getEplViewFromSubfeatures(): .....  attribute--BEFORE-FLATMAP: {}", a))
                .flatMap(Collection::stream)
                .peek(a->log.trace("RuleGenerator.getEplViewFromSubfeatures(): .....  attribute--AFTER-FLATMAP: {}", a))
                .filter(a -> EPL_VALUE.equals(a.getName()))
                .peek(a->log.trace("RuleGenerator.getEplViewFromSubfeatures(): .....  attribute--AFTER-FILTER: {}", a))
                .map(Attribute::getValue)
                .peek(o->log.trace("RuleGenerator.getEplViewFromSubfeatures(): .....  value--BEFORE-FILTERS: {}", o))
                .filter(Objects::nonNull)
                .peek(o->log.trace("RuleGenerator.getEplViewFromSubfeatures(): .....  value--MID-FILTERS: {}", o))
                .filter(o -> o instanceof StringValue)
                .peek(o->log.trace("RuleGenerator.getEplViewFromSubfeatures(): .....  value--AFTER-FILTERS: {}", o))
                .map(o -> (StringValue) o)
                .peek(o->log.trace("RuleGenerator.getEplViewFromSubfeatures(): .....  string-value: {}", o))
                .map(StringValue::getValue)
                .peek(o->log.trace("RuleGenerator.getEplViewFromSubfeatures(): .....  value: {}", o))
                .findFirst().orElse(null);
        log.debug("RuleGenerator.getEplViewFromSubfeatures(): Processing={}, epl-view={}", feature.getName(), result);
        return result;
    }

    protected String _generateWindowClause(Window win) {
        // No window specified
        if (win==null)
            return ".std:lastevent()";
            //return "";

        // Sub-feature attribute 'translation_config.epl_value' value provides EPL statement
        // and overrides Window processing
        String eplStmt = getEplValueFromSubfeatures(win);
        if (StringUtils.isNotBlank(eplStmt)) {
            log.debug("RuleGenerator._generateWindowClause(): Window '{}' has 'epl_value' set. EPL value: {}", win.getName(), eplStmt = eplStmt.trim());
            return eplStmt;
        }

        // Process Window settings for generating EPL statement
        StringBuilder sb = new StringBuilder();

        // Process 'groupwin' window group processings
        if (win.getProcessings()!=null) {
            win.getProcessings().stream()
                    .filter(p -> p.getProcessingType() == WindowProcessingType.GROUP)
                    .filter(this::groupingMustBeBeforeSizeOrTimeView)
                    .forEach(p -> _processGroupWindowProcessing(sb, p));
        }

        // Process 'time/size' window specifications
        _processSizeOrTimeView(sb, win);

        // Process 'non-groupwin' window group processings
        if (win.getProcessings()!=null) {
            win.getProcessings().stream()
                    .filter(p -> p.getProcessingType() == WindowProcessingType.GROUP)
                    .filter(this::groupingMustBeAfterSizeOrTimeView)
                    .forEach(p -> _processGroupWindowProcessing(sb, p));
        }

        // Process window sort and rank processings
        if (win.getProcessings()!=null) {
            win.getProcessings().stream()
                    .filter(p -> p.getProcessingType() == WindowProcessingType.SORT || p.getProcessingType() == WindowProcessingType.RANK)
                    .forEach(p -> {
                        if (p.getProcessingType() == WindowProcessingType.SORT)
                            _processSortWindowProcessing(sb, p);
                        else if (p.getProcessingType() == WindowProcessingType.RANK)
                            _processRankWindowProcessing(sb, p);
                    });
        }

        String eplValue = sb.toString();
        log.debug("RuleGenerator._generateWindowClause(): Window '{}' generated EPL value: {}", win.getName(), eplValue);
        return eplValue;
    }

    private boolean groupingMustBeBeforeSizeOrTimeView(WindowProcessing windowProcessing) {
        String eplView = getEplValueFromSubfeatures(windowProcessing);
        log.debug("RuleGenerator.groupingMustBeBeforeSizeOrTimeView: processing={}, epl-value={}", windowProcessing.getName(), eplView);
        boolean result = StringUtils.isBlank(eplView) || StringUtils.containsIgnoreCase(eplView, "groupwin");
        log.debug("RuleGenerator.groupingMustBeBeforeSizeOrTimeView: processing={}, result={}", windowProcessing.getName(), result);
        return result;
    }

    private boolean groupingMustBeAfterSizeOrTimeView(WindowProcessing windowProcessing) {
        boolean result = !groupingMustBeBeforeSizeOrTimeView(windowProcessing);
        log.debug("RuleGenerator.groupingMustBeAfterSizeOrTimeView(): processing={}, result={}", windowProcessing.getName(), result);
        return result;
    }

    private void _processSizeOrTimeView(StringBuilder sb, Window win) {
        // WindowType: FIXED (Batch) or SLIDING
        boolean isBatchWin = (WindowType.FIXED==win.getWindowType() || WindowType.BATCH==win.getWindowType());
        boolean isSlidingWin = ! isBatchWin;

        // WindowSizeType: MEASUREMENTS_ONLY, TIME_ONLY, FIRST_MATCH, BOTH_MATCH
        WindowSizeType winSizeType = win.getSizeType();
        boolean isFirstMatch = winSizeType==WindowSizeType.FIRST_MATCH;
        boolean isBothMatch = winSizeType==WindowSizeType.BOTH_MATCH;
        boolean isTimeOnly = winSizeType==WindowSizeType.TIME_ONLY;
        boolean isEventsOnly = winSizeType==WindowSizeType.MEASUREMENTS_ONLY;
        boolean isTimeAccum = winSizeType==WindowSizeType.TIME_ACCUM;
        boolean isTimeOrder = winSizeType==WindowSizeType.TIME_ORDER;

        // Window size(s)
        long winTimeSize = win.getTimeSize();
        long winMeasurementSize = win.getMeasurementSize();
        String winTimeUnit = win.getTimeUnit() != null ? win.getTimeUnit() : null;

        // Check for negative window sizes
        if (winTimeSize<0 && ! isEventsOnly) {
            log.warn("RuleGenerator._processSizeOrTimeView(): Time-based or First/Both-match window has NEGATIVE time. Skipping time window: window={}, type={}, window-time-size={}, window-time-unit={}", win.getName(), winSizeType, winTimeSize, winTimeUnit);
            return;
        }
        if (winMeasurementSize<0 && (isEventsOnly || isFirstMatch || isBothMatch)) {
            log.warn("RuleGenerator._processSizeOrTimeView(): Event-based or First/Both-match window has NEGATIVE length. Skipping event window: window={}, type={}, window-measurement-size={}", win.getName(), winSizeType, winTimeSize);
            return;
        }

        // Checks
        if (! isEventsOnly) {
            if (StringUtils.isBlank(winTimeUnit) || winTimeSize <= 0) {
                log.error("RuleGenerator._processSizeOrTimeView(): ERROR: Invalid or missing window-time-size or window-time-unit: window={}, window-time-size={}, window-time-unit={}", win.getName(), winTimeSize, winTimeUnit);
                throw new IllegalArgumentException(String.format("ERROR: Invalid or missing window-time-size or window-time-unit: window=%s, window-time-size=%d, window-time-unit=%s", win.getName(), winTimeSize, winTimeUnit));
            }
            winTimeUnit = camelToRule(MapType.UNIT, ElemType.TIME, winTimeUnit);
        }

        if (isFirstMatch || isBothMatch || isEventsOnly) {
            if (winMeasurementSize <= 0) {
                log.error("RuleGenerator._processSizeOrTimeView(): ERROR: Invalid window-measurement-size: window={}, window-measurement-size={}", win.getName(), winMeasurementSize);
                throw new IllegalArgumentException(String.format("ERROR: Invalid window-measurement-size: window=%s, window-measurement-size=%s", win.getName(), winMeasurementSize));
            }
        }

        if ((isTimeAccum || isTimeOrder) && isBatchWin) {
            log.error("RuleGenerator._processSizeOrTimeView(): ERROR: 'Type-Accum' and 'Type-Order' windows cannot be Batch: window={}, window-type={}, window-size-type={}", win.getName(), win.getWindowType(), win.getSizeType());
            throw new IllegalArgumentException(String.format("ERROR: 'Type-Accum' and 'Type-Order' windows cannot be Batch: window=%s, window-type=%s, window-size-type=%s", win.getName(), win.getWindowType(), win.getSizeType()));
        }

        // Generate the window length or time view
        String s;
        if (isFirstMatch) {
            if (isSlidingWin) {    // Sliding window
                long millis = _winTimeToMillis(winTimeSize, winTimeUnit);
                s = String.format(".win:expr(oldest_timestamp > newest_timestamp - %d and current_count <= %d)", millis, winMeasurementSize);
//                s = String.format(".win:expr(oldest_event.includes(newest_event, %d %s) and current_count <= %d)%s", winTimeSize, winTimeUnit, winMeasurementSize, winViews);
            } else {    // Batch window
                s = String.format(".win:time_length_batch(%d %s, %d)", winTimeSize, winTimeUnit, winMeasurementSize);
            }
        } else if (isBothMatch) {
            long millis = _winTimeToMillis(winTimeSize, winTimeUnit);
            s = String.format(".win:expr%s(oldest_timestamp > newest_timestamp - %d or current_count <= %d)", isBatchWin?"_batch":"", millis, winMeasurementSize);
//            s = String.format(".win:expr%s(oldest_event.includes(newest_event, %d %s) or current_count <= %d)", isBatchWin?"_batch":"", winTimeSize, winTimeUnit, winMeasurementSize);
        } else if (isTimeOnly) {
            s = String.format(".win:time%s(%d %s)", isBatchWin?"_batch":"", winTimeSize, winTimeUnit);
        } else if (isEventsOnly) {
            s = String.format(".win:length%s(%d)", isBatchWin?"_batch":"", winMeasurementSize);
        } else if (isTimeAccum) {
            s = String.format(".win:time_accum(%d)", winMeasurementSize);
        } else if (isTimeOrder) {
            s = String.format(".ext:time_order(timestamp, %d)", winMeasurementSize);
        } else {
            log.error("RuleGenerator._processSizeOrTimeView(): ERROR: Invalid or Unsupported window-size-type: window={}, window-size-type={}", win.getName(), winSizeType);
            throw new IllegalArgumentException(String.format("ERROR: Invalid or Unsupported window-size-type: window=%s, window-size-type=%s", win.getName(), winSizeType));
        }

        sb.append(s);
    }

    private void _processGroupWindowProcessing(StringBuilder sb, WindowProcessing p) {
        _processWindowProcessingCriteria(sb, p, p.getGroupingCriteria(), ".std:groupwin(", null, false);
    }

    private void _processSortWindowProcessing(StringBuilder sb, WindowProcessing p) {
        _processWindowProcessingCriteria(sb, p, p.getRankingCriteria(), ".ext:sort("+Integer.MAX_VALUE+", ", null, true);
    }

    private void _processRankWindowProcessing(StringBuilder sb, WindowProcessing p) {
        _processWindowProcessingCriteria(sb, p, p.getRankingCriteria(), ".ext:rank(", Integer.MAX_VALUE+")", true);
    }

    private void _processWindowProcessingCriteria(StringBuilder sb, WindowProcessing p, List<WindowCriterion> criteriaList, String openView, String closeView, boolean allowTimestampType) {
        // Get view from translation overriding sub-feature
        String view = getEplValueFromSubfeatures(p);
        if (StringUtils.isNotBlank(view)) {
            sb.append(view);
            return;
        }

        // Default view processing
        if (criteriaList!=null && criteriaList.size()>0) {
            if (StringUtils.isBlank(view)) {
                view = openView;
            } else
            if (! view.trim().startsWith(".") && ! view.trim().startsWith("#")) {
                view = view.contains(":") ? "."+view.trim() : "#"+view.trim();
            } else {
                view = view.trim();
            }

            // Add view opening
            sb.append(view);
            if (!view.trim().endsWith("(") && !view.trim().endsWith(",")) sb.append("(");

            final boolean[] first = {true};
            criteriaList.forEach(c->{
                if (first[0]) first[0] = false; else sb.append(", ");
                log.debug("RuleGenerator._processWindowProcessingCriteria(): {} processing criterion: processing={}, criterion={}, type={}, custom={}, metric={}",
                        p.getProcessingType(), p.getName(), c.getName(), c.getType(), c.getCustom(), c.getMetric());
                if (CriterionType.CUSTOM==c.getType()) {
                    if (StringUtils.isNotBlank(c.getCustom())) {
                        sb.append(c.getCustom().trim());
                    } else {
                        log.error("RuleGenerator._processWindowProcessingCriteria(): CUSTOM group processing Criterion type must provide 'custom' property: {}", c.getName());
                        throw new IllegalArgumentException("CUSTOM group processing Criterion type must provide 'custom' property: " + c.getName());
                    }
                } else
                if (CriterionType.TIMESTAMP==c.getType()) {
                    if (allowTimestampType) {
                        sb.append("timestamp");
                    } else {
                        log.error("RuleGenerator._processWindowProcessingCriteria(): TIMESTAMP processing Criterion type MUST NOT be used for grouping: {}", c.getName());
                        throw new IllegalArgumentException("TIMESTAMP processing Criterion type MUST NOT be used for grouping: " + c.getName());
                    }
                } else
                if (CriterionType.INSTANCE==c.getType()) {
                    sb.append("prop(*, 'instance')");
                } else
                if (CriterionType.HOST==c.getType()) {
                    sb.append("prop(*, 'host')");
                } else
                if (CriterionType.ZONE==c.getType()) {
                    sb.append("prop(*, 'zone')");
                } else
                if (CriterionType.REGION==c.getType()) {
                    sb.append("prop(*, 'region')");
                } else
                if (CriterionType.CLOUD==c.getType()) {
                    sb.append("prop(*, 'cloud')");
                } else
                {
                    log.error("RuleGenerator._processWindowProcessingCriteria(): Unsupported Processing Criterion type: {}", c.getType());
                    throw new IllegalArgumentException("Unsupported Processing Criterion type: " + c.getType());
                }
            });

            // Add view close
            if (StringUtils.isNotBlank(closeView)) sb.append(closeView); else sb.append(")");

        } else {
            log.warn("RuleGenerator._processWindowProcessingCriteria: {} window processing with NO grouping criteria. Processing is ignored: {}", p.getProcessingType(), p.getName());
        }
    }

    private long _winTimeToMillis(long time, String unit) {
        return switch (unit.toLowerCase()) {
            case "msec", "millisecond", "milliseconds" -> time;
            case "sec", "second", "seconds" -> TimeUnit.MILLISECONDS.convert(time, TimeUnit.SECONDS);
            case "min", "minute", "minutes" -> TimeUnit.MILLISECONDS.convert(time, TimeUnit.MINUTES);
            case "hour", "hours" -> TimeUnit.MILLISECONDS.convert(time, TimeUnit.HOURS);
            case "day", "days" -> TimeUnit.MILLISECONDS.convert(time, TimeUnit.DAYS);
            case "week", "weeks" -> TimeUnit.MILLISECONDS.convert(7 * time, TimeUnit.DAYS);
            case "month", "months" -> {
                log.warn("RuleGenerator._winTimeToMillis: NOTE: 'Months' time unit equals to 30 days!");
                yield TimeUnit.MILLISECONDS.convert(30 * time, TimeUnit.DAYS);
            }
            case "year", "years" -> {
                log.debug("RuleGenerator._winTimeToMillis: NOTE: 'Years' time unit equals to 365 days!");
                yield  TimeUnit.MILLISECONDS.convert(365 * time, TimeUnit.DAYS);
            }
            default -> throw new RuntimeException("Unsupported time unit: \"" + unit + "\"");
        };
    }

    protected String _generateScheduleClause(Schedule sched, String selector) {
        if (sched == null) return "";

        int schedRepetitions = sched.getRepetitions();
        long schedInterval = sched.getInterval();
        if (schedRepetitions <= 0 && schedInterval <= 0) return "";

        if (schedRepetitions > 0 && schedInterval > 0) {
            log.error("RuleGenerator._generateScheduleClause(): ERROR: Schedule has both 'repetitions' and 'interval' properties non-zero: repetitions={}, interval={}", schedRepetitions, schedInterval);
            throw new IllegalArgumentException(String.format("ERROR: Schedule has both 'repetitions' and 'interval' properties non-zero: repetitions=%s, interval=%s", schedRepetitions, schedInterval));
        }

        long schedPeriod;
        String schedUnit;
        if (schedInterval > 0) {
            schedPeriod = schedInterval;
            schedUnit = sched.getTimeUnit();
            schedUnit = camelToRule(MapType.UNIT, ElemType.TIME, schedUnit);
        } else {
            schedPeriod = schedRepetitions;
            schedUnit = "EVENTS";
        }

        if (StringUtils.isBlank(selector)) selector = "__ANY__";
        String schedTpl = ruleTemplatesRegistry.getTemplatesFor("SCHEDULE", selector).stream().findFirst().orElse(null);
        log.trace("RuleGenerator._generateScheduleClause(): schedule-tpl: {}", schedTpl);
        if (schedTpl != null) {
            // Use template engine to process the selected schedule template
            Context context = new Context();
            context.setVariable("type", sched.getType().toString());
            context.setVariable("period", schedPeriod);
            context.setVariable("unit", schedUnit);
            String schedStr = templateEngine.process(schedTpl.trim(), context);
            log.debug("RuleGenerator._generateScheduleClause(): schedule-clause: {}", schedStr);
            return schedStr;
        } else {
            return String.format("\nOUTPUT LAST EVERY %d %s", schedPeriod, schedUnit);
        }
    }

    // ========================================================================
    // Monitor update methods
    // ========================================================================

    private void _updateMonitors(TranslationContext _TC) {
        // Normalize entries of _TC.MONS (names of sensors, which coincides with monitor metrics)
        Set<String> newSensorNames = _TC.getMONS().stream().map(this::getElemNameNormalized).collect(Collectors.toSet());
        _TC.getMONS().clear();
        _TC.getMONS().addAll(newSensorNames);

        // Normalize entries of _TC.MON (names in monitors, i.e. metric, and sensor name)
        _TC.getMON().forEach(mon -> {
            String newMetric = getElemNameNormalized(mon.getMetric());
            String newSensorName = getElemNameNormalized(mon.getSensor().getName());
            mon.setMetric(newMetric);
            mon.getSensor().setName(newSensorName);
        });
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    protected String camelToRule(MapType mapType, ElemType elemType, String camelStr) {
        if (MapType.OPERATOR == mapType && ElemType.CONSTR == elemType) {
            if ("GREATER_THAN".equalsIgnoreCase(camelStr)) return ">";
            if ("GREATER_EQUAL_THAN".equalsIgnoreCase(camelStr)) return ">=";
            if ("LESS_THAN".equalsIgnoreCase(camelStr)) return "<";
            if ("LESS_EQUAL_THAN".equalsIgnoreCase(camelStr)) return "<=";
            if ("EQUAL".equalsIgnoreCase(camelStr)) return "=";
            if ("NOT_EQUAL".equalsIgnoreCase(camelStr)) return "<>";
            if ("AND".equalsIgnoreCase(camelStr)) return "AND";
            if ("OR".equalsIgnoreCase(camelStr)) return "OR";
            //XXX: EPL does not support XOR event patterns:
            // if ("XOR".equalsIgnoreCase(camelStr)) return "XOR";
            throw new IllegalArgumentException(String.format("Illegal argument in 'camelStr': MapType=%s, ElemType=%s, camel-str=%s", mapType, elemType, camelStr));
        }
        if (MapType.UNIT == mapType && ElemType.TIME == elemType) {
            try {
                String unit = AnalysisUtils.normalizeTimeUnit(camelStr).name();
                if ("MILLIS".equalsIgnoreCase(unit)) unit = "MILLISECONDS";
                return unit;
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("Illegal argument in 'camelStr': MapType=%s, ElemType=%s, camel-str=%s", mapType, elemType, camelStr), e);
            }
        }
        return camelStr.toUpperCase().trim();
    }

    protected boolean checkIfListsAreEqual(List<String> list1, List<String> list2) {
        if (list1 == null && list2 == null) return true;
        else if (list1 != null && list2 == null || list1 == null || list1.size() != list2.size())
            return false;
        //else
        List<String> sorted1 = new ArrayList<>(list1);
        List<String> sorted2 = new ArrayList<>(list2);
        java.util.Collections.sort(sorted1);
        java.util.Collections.sort(sorted2);
        return sorted1.equals(sorted2);
    }

    protected String[] getComponentAndDataName(MetricContext mc) {
        String compName = null;
        String dataName = null;
        if (mc != null) {
            ObjectContext objCtx = mc.getObjectContext();
            if (objCtx != null) {
                Component comp = objCtx.getComponent();
                Data data = objCtx.getData();
                compName = comp != null ? comp.getName() : null;
                dataName = data != null ? data.getName() : null;
            }
        }
        String[] result = new String[2];
        result[0] = compName;
        result[1] = dataName;
        return result;
    }

    // ========================================================================
    // Helper classes and enums
    // ========================================================================

    protected enum MapType { OPERATOR, UNIT }

    protected enum ElemType { BEP, UEP, NFE, FE, CONSTR, CMC, RCM, CM, RM, MT, SENSOR, TIME, EVENT }
}