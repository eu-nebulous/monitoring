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
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import gr.iccs.imu.ems.translate.Grouping;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.dag.DAGNode;
import gr.iccs.imu.ems.translate.model.*;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static eu.nebulous.ems.translate.analyze.AnalysisUtils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricModelAnalyzer {
    private final NebulousEmsTranslatorProperties properties;
    private final FunctionsHelper functionsHelper;
    private final ConstraintsHelper constraintsHelper;
    private final MetricsHelper metricsHelper;
    private final NodeUpdatingHelper nodeUpdatingHelper;
    private final SensorsHelper sensorsHelper;

    // ================================================================================================================
    // Model analysis methods

    public void analyzeModel(@NonNull TranslationContext _TC, @NonNull Object metricModel, String modelName) throws Exception {
        log.debug("MetricModelAnalyzer.analyzeModel(): BEGIN: metric-model: {}", metricModel);

        // ----- Initialize components ----------------------------------
        functionsHelper.reset();
        constraintsHelper.reset();
        metricsHelper.reset();
        nodeUpdatingHelper.reset();
        sensorsHelper.reset();

        // ----- Initialize jsonpath context ----------------------------------
        Configuration jsonpathConfig = Configuration.defaultConfiguration();
        ParseContext parseContext = JsonPath.using(jsonpathConfig);
        DocumentContext ctx = parseContext.parse(metricModel);

        // ----- Model processing ---------------------------------------------
        log.debug("MetricModelAnalyzer.analyzeModel(): Analyzing metric model: {}", metricModel);
        Map<String, Object> topLevelModelElements = ctx.read("$", Map.class);

        // ----- Define additional translation structures and cache them in _TC -----
        _TC.setExtensionContext(new AdditionalTranslationContextData());

        // set full-name pattern in _TC, for full-name generation
        if (StringUtils.isNotBlank(properties.getFullNamePattern())) {
            log.debug("MetricModelAnalyzer.analyzeModel(): Set full name pattern to: {}", properties.getFullNamePattern());
            _TC.setFullNamePattern(properties.getFullNamePattern());
        }

        // ----- Process function specifications -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Process function specs");
        if (topLevelModelElements.containsKey("functions")) {
            List<Object> functionSpecsList = ctx.read("$.functions.*", List.class);
            functionSpecsList.stream().filter(s -> s instanceof Map).forEach(s -> {
                functionsHelper.processFunction(_TC, s);
            });
        }

        // ----- Check if component and scope specifications exist -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Checking for component and scope specs");
        Map specs = ctx.read("$.spec", Map.class);
        if (log.isDebugEnabled()) {
            log.debug("MetricModelAnalyzer.analyzeModel():  spec.components: {}", specs.get("components"));
            log.debug("MetricModelAnalyzer.analyzeModel():  spec.scopes: {}", specs.get("scopes"));
        }
        boolean hasComponents = ! isNullOrEmpty(specs.get("components"));
        boolean hasScopes = ! isNullOrEmpty(specs.get("scopes"));
        log.debug("Has component specs: {}", hasComponents);
        log.debug("Has scope specs    : {}", hasScopes);
        if (! hasComponents && ! hasScopes)
            throw createException("Metric model contains no component and no scope specifications");
        if (hasScopes && !hasComponents)
            throw createException("Metric model contains no components but has scope specifications");

        // ----- Get component and scope names -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Check name uniqueness");
        List<String> componentNamesList = ctx.read("$.spec.components.*.name", List.class);
        List<String> scopeNamesList = hasScopes
                ? ctx.read("$.spec.scopes.*.name", List.class) : Collections.emptyList();
        log.debug("Component names: {}", componentNamesList);
        log.debug("    Scope names: {}", scopeNamesList);

        // Check name uniqueness
        checkNameUniqueness(componentNamesList, scopeNamesList);
        Set<String> componentNames = new LinkedHashSet<>(componentNamesList);
        Set<String> scopeNames = new LinkedHashSet<>(scopeNamesList);

        // Check name uniqueness
        checkAllNamesUniqueness(ctx, componentNames, scopeNames);

        // ----- Set container name and make mutable all 'spec' elements (and sub-elements) -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Set element containers");
        Map<String, Object> modelRoot = asMap(ctx.read("$"));
        Object specNode = modelRoot.get("spec");
        specNode = addContainerNameAndMakeMutable(specNode, null);
        modelRoot.put("spec", specNode);

        // ----- Create object contexts for components -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Create object contexts");
        createObjectContexts(_TC, componentNames);

        // ----- Build flat lists of metrics, constraints and SLOs, and check their specs -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Build element lists");
        buildElementLists(_TC, modelRoot);
        log.debug("    All Metrics: {}", $$(_TC).allMetrics);
        log.debug("All Constraints: {}", $$(_TC).allConstraints);
        log.debug("       All SLOs: {}", $$(_TC).allSLOs);

        // ----- Build scope-to-components map -----
        $$(_TC).scopesComponents = hasScopes
                ? nodeUpdatingHelper.createScopesToComponentsMap(ctx, componentNames) : Collections.emptyMap();

        // ----- Build of constants lists -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Build constants list");
        buildConstantsList(_TC, modelRoot);

        // ----- Process SLOs -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Process SLOs");
        processSLOs(_TC);

        // ----- Process orphan metrics (i.e. not used in constraints) -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Process orphan metrics");
        metricsHelper.processOrphanMetrics(_TC);

        // ----- Infer groupings (levels) -----
        log.debug("MetricModelAnalyzer.analyzeModel(): Infer and set element groupings");
        inferGroupings(_TC);

        // ----- Build components to SLOs maps (including those in the scopes they participate) -----
        if (properties.isUpdateNodesWithRequiringComponents()) {
            log.debug("MetricModelAnalyzer.analyzeModel(): Updating DAG nodes with requiring component names");

            // Build component to SLO maps
            nodeUpdatingHelper.buildComponentsToSLOsMap(_TC, ctx, componentNames);
            nodeUpdatingHelper.buildSLOToComponentsMap(_TC);

            // Update DAG nodes with components requiring them (busy-status and orphans are required by all components)
            nodeUpdatingHelper.updateDAGNodesWithComponents(_TC, componentNames);
        }

        // ----- Remove Orphan metrics common parent variable -----
        if (! properties.isUseCommonOrphansParent()) {
            log.debug("MetricModelAnalyzer.analyzeModel(): Removing common orphan metrics parent");
            nodeUpdatingHelper.removeCommonOrphanMetricsParent(_TC);
        }

        // ----------------------------------------------------------

        log.debug("MetricModelAnalyzer.analyzeModel(): END: metric-model: {}", metricModel);
    }

    // ------------------------------------------------------------------------
    //  Additional translation data structures and shorthand retrieval method
    // ------------------------------------------------------------------------

    private AdditionalTranslationContextData $$(TranslationContext _TC) {
        return _TC.$(AdditionalTranslationContextData.class);
    }

    @Data
    @Accessors(fluent = true)
    static class AdditionalTranslationContextData {
        final Map<String, Object> parentSpecs = new LinkedHashMap<>();              // i.e. all component and scope specs
        final Map<String, ObjectContext> objectContexts = new LinkedHashMap<>();    // i.e. object contexts for all components
        final Map<NamesKey, Object> allSLOs = new LinkedHashMap<>();
        final Map<NamesKey, Object> allConstraints = new LinkedHashMap<>();
        final Map<NamesKey, Object> allMetrics = new LinkedHashMap<>();
        final Map<NamesKey, MetricContext> metricsUsed = new LinkedHashMap<>();
        final Map<NamesKey, Constraint> constraintsUsed = new LinkedHashMap<>();
        final Map<NamesKey, Object> constants = new LinkedHashMap<>();
        final Set<String> functionNames = new HashSet<>();
        Map<String, Set<NamesKey>> componentsToSLOsMap;
        Map<NamesKey, Set<String>> slosToComponentsMap;
        public Map<String, Set<String>> scopesComponents;
    }

    // ------------------------------------------------------------------------
    //  Analysis helper methods
    // ------------------------------------------------------------------------

    private static void checkNameUniqueness(List<String> componentNamesList, List<String> scopeNamesList) {
        List<String> all = new ArrayList<>(componentNamesList);
        all.addAll(scopeNamesList);
        log.trace("      ALL names: {}", all);
        List<String> duplicateNames = all.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
        if (! duplicateNames.isEmpty()) {
            log.error("Duplicate names: {}", duplicateNames);
            throw createException("Naming conflicts exist for: "+duplicateNames);
        }
    }

    private void checkAllNamesUniqueness(DocumentContext ctx, Set<String> componentNames, Set<String> scopeNames) {
        /*List<String> allNames = Stream.of(
                $$(_TC).allMetrics.keySet().stream().map(NamesKey::name),
                $$(_TC).allSLOs.keySet().stream().map(NamesKey::name),
                $$(_TC).constants.keySet().stream().map(NamesKey::name),
                componentNames.stream(),
                scopeNames.stream()
        ).flatMap(s -> s).toList();
        log.trace("      ALL model names: {}", allNames);*/

        // Add component and scope names
        List<String> allNames = new ArrayList<>();
        allNames.addAll(componentNames);
        allNames.addAll(scopeNames);

        // Add metric names
        filterSpecsList(asList(ctx.read("$.spec.*.*.metrics.*"))).forEach(spec -> {
            log.trace("check-name-uniqueness: {}", spec);
            String name = getSpecName(spec);
            if (StringUtils.isNotBlank(name))
                allNames.add(name);
        });

        // Add SLO names (but not constraint names that use the corresponding SLO names)
        filterSpecsList(asList(ctx.read("$.spec.*.*.requirements.*"))).forEach(spec -> {
            log.trace("check-name-uniqueness: {}", spec);
            String name = getSpecName(spec);
            if (StringUtils.isNotBlank(name))
                allNames.add(name);
        });

        // Add function names
        filterSpecsList(asList(ctx.read("$[?(@.functions!=null)].functions.*.*"))).forEach(spec -> {
            log.trace("check-name-uniqueness: {}", spec);
            String name = getSpecName(spec);
            if (StringUtils.isNotBlank(name))
                allNames.add(name);
        });

        // Check for duplicates
        log.debug("      ALL model names: {}", allNames);
        List<String> duplicateNames = allNames.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
        if (! duplicateNames.isEmpty()) {
            log.error("Duplicate names found: {}", duplicateNames);
            throw createException("Naming conflicts found for: "+duplicateNames);
        }
    }

    private Object addContainerNameAndMakeMutable(Object o, String parentName) {
        if (o instanceof Map m) {
            Map<String, Object> newM = new LinkedHashMap<>();
            newM.put(CONTAINER_NAME_KEY, parentName);
            String myName0 = getSpecName(o);
            String myName = parentName != null ? parentName : myName0;
            m.forEach((k,v) -> newM.put(k.toString(), addContainerNameAndMakeMutable(v, myName)));
            return newM;
        } else
        if (o instanceof List l) {
            List<Object> newL = new LinkedList<>();
            l.forEach(v -> newL.add(addContainerNameAndMakeMutable(v, parentName)));
            return newL;
        }
        return o;
    }

    private void createObjectContexts(TranslationContext _TC, Set<String> componentNames) {
        Map<String, ObjectContext> objectContexts = componentNames.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        name -> ObjectContext.builder()
                                .component(Component.builder().name(name).build())
                                .build()
                ));
        $$(_TC).objectContexts.putAll(objectContexts);
    }

    private void buildElementLists(TranslationContext _TC, Map<String, Object> modelRoot) {
        filterSpecsList(asList(JsonPath.read(modelRoot, "$.spec.*.*"))).forEach(spec -> {
            log.debug("buildElementLists: {}", spec);
            String parentName = getSpecName(spec);
            if (StringUtils.isBlank(parentName)) throw createException("Component or Scope with no name: " + spec);
            $$(_TC).parentSpecs.computeIfAbsent(parentName, (key)->spec);

            // Requirements (SLOs) flat list building
            List<Object> slos = JsonPath.read(spec,
                    "$[?(@.requirements!=null && @.requirements.*[?(@.type=='slo')])].requirements.*");
            slos = filterSpecsList(slos);
            slos.forEach(sloSpec -> {
                log.debug("buildElementLists: SLO (requirements): {}", sloSpec);
                String sloName = getSpecName(sloSpec);
                if (StringUtils.isBlank(sloName)) throw createException("SLO spec with no name: " + sloSpec);
                Object constraintObject = asMap(sloSpec).get("constraint");
                /*if (!isMap(constraintObject) || asMap(constraintObject).isEmpty())
                    throw createException("SLO spec with no constraint: " + sloSpec);*/
                if (isMap(constraintObject) && ! asMap(constraintObject).isEmpty()) {
                    $$(_TC).allSLOs.put(createNamesKey(parentName, sloName), sloSpec);
                } else {
                    log.warn("Ignoring SLO with no or empty constraint: {}", sloSpec);
                }
            });

            // Constraints flat list building
            slos.forEach(sloSpec -> {
                log.debug("buildElementLists: SLO (constraints): {}", sloSpec);
                String sloName = getSpecName(sloSpec);
                if (StringUtils.isBlank(sloName)) throw createException("SLO spec with no name: " + sloSpec);

                NamesKey sloNamesKey = createNamesKey(parentName, sloName);
                Object constraintObject = asMap(sloSpec).get("constraint");
                if (isMap(constraintObject)) {
                    Map<String, Object> constraintSpec = asMap(constraintObject);
                    NamesKey constraintNamesKey = createNamesKey(sloNamesKey, sloName);
                    $$(_TC).allConstraints.put(constraintNamesKey, constraintSpec);
                } else {
                    log.warn("Ignored SLO with no or empty constraint: {}", sloName);
                }
            });

            // Metrics flat list building
            List<Object> metrics = JsonPath.read(spec, "$[?(@.metrics!=null)].metrics.*");
            filterSpecsList(metrics).forEach(metricSpec -> {
                log.debug("buildElementLists: Metric: {}", metricSpec);
                String metricName = getSpecName(metricSpec);
                if (StringUtils.isBlank(metricName)) throw createException("Metric spec with no name: " + metricSpec);
                NamesKey namesKey = createNamesKey(parentName, metricName);
                $$(_TC).allMetrics.put(namesKey, metricSpec);
            });
        });
    }

    private List<Object> filterSpecsList(List<Object> list) {
        return list.stream()
                //.peek(x->log.warn("---------> MM: {} -- {}", x!=null?x.getClass().getName():null, x))
                .filter(Objects::nonNull)
                .filter(o->o instanceof Map)
                //.peek(x->log.warn("           !!: {} -- {}", x.getClass().getName(), x))
                .toList();
    }

    private void buildConstantsList(TranslationContext _TC, Map<String, Object> modelRoot) {
        filterSpecsList(asList(JsonPath.read(modelRoot, "$.spec.*.*.metrics.*[?(@.type=='constant')]"))).forEach(spec -> {
            metricsHelper.processConstant(_TC, asMap(spec), getContainerName(spec));
        });
    }

    // ------------------------------------------------------------------------
    //  Process SLOs -- This is the main decomposition method (uses helpers)
    // ------------------------------------------------------------------------

    private void processSLOs(TranslationContext _TC) {
        // ----- Decompose SLOs with metric constraints to their metric hierarchies -----
        Map<NamesKey, Object> metricSLOs = $$(_TC).allSLOs.entrySet().stream()
                //.peek(x -> log.warn("-------->  {}", getSpecField(asMap(x.getValue()).get("constraint"), "type") ))
                .filter(x -> "metric".equals( getSpecField(asMap(x.getValue()).get("constraint"), "type") ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        constraintsHelper.decomposeConstraints(_TC, metricSLOs);

        // ----- Decompose SLOs with non-metric constraints -----
        Map<NamesKey, Object> nonMetricSLOs = new LinkedHashMap<>($$(_TC).allSLOs);
        nonMetricSLOs.keySet().removeAll( metricSLOs.keySet() );
        constraintsHelper.decomposeConstraints(_TC, nonMetricSLOs);
    }

    // ------------------------------------------------------------------------
    //  Grouping inference methods
    // ------------------------------------------------------------------------

    private void inferGroupings(TranslationContext _TC) {
        Grouping topLevelGrouping = Grouping.GLOBAL;
        Grouping leafGrouping = properties.getLeafNodeGrouping();

        _TC.getDAG().traverseDAG(node -> {
            NamedElement elem = node.getElement();
            if (elem!=null) {
                inferElementGrouping(_TC, topLevelGrouping, leafGrouping, node);
            }
        });
    }

    private static void inferElementGrouping(TranslationContext _TC, Grouping topLevelGrouping, Grouping leafGrouping, DAGNode node) {
        // Check if grouping has already been set
        if (node.getGrouping()!=null) return;

        // Get node model element
        NamedElement elem = node.getElement();
        if (elem==null) return;                     // Root node?

        // Infer element grouping
        Object groupingObj;
        if (elem instanceof ServiceLevelObjective || elem instanceof Constraint) {
            groupingObj = topLevelGrouping;
        } else if (elem instanceof Sensor || elem instanceof RawMetricContext) {
            groupingObj = leafGrouping;
        } else {
            // Infer parents' groupings
            Set<DAGNode> parents = _TC.getDAG().getParentNodes(node);
            parents.forEach(p -> inferElementGrouping(_TC, topLevelGrouping, leafGrouping, p));

            // Get the lowest parents grouping
            List<Grouping> parentGroupings = parents.stream()
                    .filter(p -> p.getElement()!=null)
                    .map(DAGNode::getGrouping)
                    .collect(Collectors.toSet()).stream()
                    .filter(Objects::nonNull)
                    .sorted().toList();
            if (! parentGroupings.isEmpty())
                groupingObj = parentGroupings.get(0);
            else
                groupingObj = topLevelGrouping;

            // Get grouping in element specification (if provided)
            if (elem.getObject() instanceof Map m) {
                Object gObj = m.get("grouping");
                if (gObj == null)
                    gObj = m.get("level");
                if (gObj != null) {
                    Grouping specGrouping = Grouping.valueOf( gObj.toString().trim().toUpperCase() );
                    if (specGrouping.ordinal() >= leafGrouping.ordinal()) {
                        if (specGrouping.ordinal() < Grouping.valueOf(groupingObj.toString().toUpperCase()).ordinal()) {
                            groupingObj = specGrouping;
                        }
                    } else {
                        groupingObj = leafGrouping;
                    }
                }
            }
        }
        node.setGrouping(Grouping.valueOf(groupingObj.toString().toUpperCase()));
    }

}