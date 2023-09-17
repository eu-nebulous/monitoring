/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import gr.iccs.imu.ems.translate.dag.DAG;
import gr.iccs.imu.ems.translate.dag.DAGNode;
import gr.iccs.imu.ems.translate.model.*;
import gr.iccs.imu.ems.util.FunctionDefinition;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@ToString
public class TranslationContext implements Serializable {

    @Getter
    private final String modelName;

    // Decomposition DAG
    @Getter
    @JsonIgnore
    private transient gr.iccs.imu.ems.translate.dag.DAG DAG;

    // Event-to-Action map
    @Getter
    private final Map<String, Set<String>> E2A = new HashMap<>();

    // SLO set
    @Getter
    private final Set<String> SLO = new LinkedHashSet<>();

    // Component-to-Sensor map
    @Getter
    @JsonIgnore
    private final transient Map<Component, Set<Sensor>> C2S = new HashMap<>();        //XXX:TODO-LOW: Convert to strings

    // Data-to-Sensor map
    @Getter
    @JsonIgnore
    private final transient Map<gr.iccs.imu.ems.translate.model.Data, Set<Sensor>> D2S = new HashMap<>();             //XXX:TODO-LOW: Convert to strings

    // Sensor Monitors set
    @Getter
    private final Set<Monitor> MON = new LinkedHashSet<>();                        //XXX:TODO-LOW: Remove ??
    @Getter
    private final Set<String> MONS = new LinkedHashSet<>();

    // Grouping-to-EPL Rule map
    private final Map<String, Map<String, Set<String>>> G2R = new HashMap<>();

    // Grouping-to-Topics map
    private final Map<String, Set<String>> G2T = new HashMap<>();

    // Metric-to-Metric Context map
    @Getter
    @JsonIgnore
    private final transient Map<Metric, Set<MetricContext>> M2MC = new HashMap<>();

    // Composite Metric Variables set
    @Getter
    @JsonIgnore
    private final Set<String> CMVar = new LinkedHashSet<>();
    @Getter
    @JsonIgnore
    private final transient Set<MetricVariable> CMVar_1 = new LinkedHashSet<>();

    // Raw Metric Variables set
    @Getter
    @JsonIgnore
    private final Set<String> RMVar = new LinkedHashSet<>();
    @Getter
    @JsonIgnore
    private final transient Set<MetricVariable> RMVar_1 = new LinkedHashSet<>();

    // Metric Variable Values set (i.e. non-composite metric variable)
    private final Set<String> MVV = new LinkedHashSet<>();
    private final Map<String,String> MvvCP = new HashMap<>();

    // Function set
    @Getter
    private final Set<FunctionDefinition> FUNC = new LinkedHashSet<>();

    // Topics-Connections-per-Grouping
    @JsonIgnore
    private final transient Map<String, String> providedTopics = new HashMap<>();                       // topic-grouping where this topic is provided
    @JsonIgnore
    private final transient Map<String, Set<String>> requiredTopics = new HashMap<>();                  // topic-set of groupings where this topic is required
    protected final Map<String, Map<String, Set<String>>> topicConnections = new HashMap<>();           // grouping-provided topic in grouping-groupings that require provided topic
    protected boolean needsRefresh;

    // Metric Constraints
    private final Set<MetricConstraint> metricConstraints = new LinkedHashSet<>();
    // Logical Constraints
    private final Set<LogicalConstraint> logicalConstraints = new LinkedHashSet<>();
    // If-Then-Else Constraints
    private final Set<IfThenConstraint> ifThenConstraints = new LinkedHashSet<>();

    // Load-annotated Metric
    protected final Set<String> loadAnnotatedMetricsSet = new LinkedHashSet<>();

    // Top-Level metric names
    protected Set<String> topLevelMetricNames = new LinkedHashSet<>();

    // Export files
    @Getter @Setter
    private List<String> exportFiles = new ArrayList<>();

    // Element-to-Full-Name cache, pattern and count
    @JsonIgnore
    protected transient final Map<NamedElement, String> E2N;            //XXX:TODO-LOW: Clear after translation
    @JsonIgnore
    protected transient final AtomicLong elementsCount;
    @Getter @Setter
    protected String fullNamePattern;                                   // all options: {TYPE}, {CAMEL}, {MODEL}, {ELEM}, {HASH}, {COUNT}

    @Getter
    protected final Map<String, Object> additionalResults = new LinkedHashMap<>();

    @JsonIgnore
    private final transient Gson gson = new Gson();                     // Used when cloning
    /*@JsonIgnore                                                       // Alternative: clone with Jackson instead of Gson
    private final transient ObjectMapper objectMapper = new ObjectMapper();*/

    // ====================================================================================================================================================
    // Constructors

    public TranslationContext(String modelName) {
        this(true, modelName);
    }

    public TranslationContext(boolean initializeDag, String modelName) {
        // Initialize fields
        this.modelName = modelName;
        this.DAG = initializeDag ? new DAG(this::getFullName) : new DAG();

        // Element-to-Full-Name staff
        this.E2N = new HashMap<>();
        this.elementsCount = new AtomicLong(0);
        this.fullNamePattern = "{ELEM}";
    }

    /*public TranslationContext(TranslationContext _TC, boolean initializeDag) {
        this(initializeDag, _TC.modelName);

        // Comment out 'this(...)' constructor and uncomment the following lines
        //this.DAG = deepCopy( _TC.DAG, DAG.class );    // DAG used during translation. Not for serialization
        //this.E2N = new HashMap<>();
        //this.elementsCount = new AtomicLong(0);
        //this.fullNamePattern = "{ELEM}";
        //
        //this.M2MC.putAll( cloneMapSet(_TC.M2MC) );    // Temporary translation cache. Not for serialization

        this.E2A.putAll( cloneMapSet(_TC.E2A) );
        this.SLO.addAll(_TC.SLO);
        //this.C2S.putAll( cloneMapSet(_TC.C2S) );
        //this.D2S.putAll( cloneMapSet(_TC.D2S) );
        this.MON.addAll( cloneSet(_TC.MON) );
        this.MONS.addAll(_TC.MONS);
        this.G2R.putAll( cloneMapMapSet(_TC.G2R) );
        this.G2T.putAll( cloneMapSet(_TC.G2T) );
        this.CMVar.addAll(_TC.CMVar);
        this.CMVar_1.addAll( cloneSet(_TC.CMVar_1) );
        this.RMVar.addAll(_TC.CMVar);
        this.RMVar_1.addAll( cloneSet(_TC.CMVar_1) );
        this.MVV.addAll(_TC.MVV);
        this.MvvCP.putAll(_TC.MvvCP);
        this.FUNC.addAll( cloneSet(_TC.FUNC) );
        this.providedTopics.putAll(_TC.providedTopics);
        this.requiredTopics.putAll( cloneMapSet(_TC.requiredTopics) );
        this.topicConnections.putAll( cloneMapMapSet(_TC.topicConnections) );
        this.needsRefresh = _TC.needsRefresh;
        this.metricConstraints.addAll( cloneSet(_TC.metricConstraints) );
        this.logicalConstraints.addAll( cloneSet(_TC.logicalConstraints) );
        this.ifThenConstraints.addAll( cloneSet(_TC.ifThenConstraints) );
        this.loadAnnotatedMetricsSet.addAll(_TC.loadAnnotatedMetricsSet);
        this.topLevelMetricNames.addAll(_TC.topLevelMetricNames);
        this.exportFiles.addAll(_TC.exportFiles);
        this.fullNamePattern = cloneObject(_TC.fullNamePattern);
    }

    // ====================================================================================================================================================
    // Cloning methods

    public TranslationContext clone() {
        return new TranslationContext(this, false);
    }

    @SneakyThrows
    protected <T> T deepCopy(T object, Class<T> type) {
        return gson.fromJson(gson.toJson(object, type), type);
        *//*return objectMapper.readValue(
                objectMapper.writeValueAsString(object), type);*//*
    }

    protected <T> T cloneObject(T obj) {
        if (obj==null) return null;
        if (obj instanceof String x) return (T) new String(x);

        if (obj instanceof PullSensor x) return (T) deepCopy(x, PullSensor.class);
        if (obj instanceof PushSensor x) return (T) deepCopy(x, PushSensor.class);
        if (obj instanceof Sensor x) return (T) deepCopy(x, Sensor.class);
        if (obj instanceof Component x) return (T) deepCopy(x, Component.class);
        if (obj instanceof Data x) return (T) deepCopy(x, Data.class);
        if (obj instanceof Monitor x) return (T) deepCopy(x, Monitor.class);

        if (obj instanceof MetricVariable x) return (T) deepCopy(x, MetricVariable.class);
        if (obj instanceof Metric x) return (T) deepCopy(x, Metric.class);
        if (obj instanceof MetricContext x) return (T) deepCopy(x, MetricContext.class);
        if (obj instanceof FunctionDefinition x) return (T) deepCopy(x, FunctionDefinition.class);

        if (obj instanceof MetricConstraint x) return (T) deepCopy(x, MetricConstraint.class);
        if (obj instanceof LogicalConstraint x) return (T) deepCopy(x, LogicalConstraint.class);
        if (obj instanceof IfThenConstraint x) return (T) deepCopy(x, IfThenConstraint.class);
        if (obj instanceof Constraint x) return (T) deepCopy(x, Constraint.class);

        throw new IllegalArgumentException("Unsupported type: "+obj.getClass().getName());
    }

    protected <T> Set<T> cloneSet(Set<T> set) {
        return set.stream()
                .map(this::cloneObject)
                .collect(Collectors.toSet());
    }

    protected <S,T> Map<S,Set<T>> cloneMapSet(Map<S, Set<T>> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> cloneObject(e.getKey()),
                        e -> cloneSet(e.getValue())
                ));
    }

    protected <S,T,U> Map<S, Map<T, Set<U>>> cloneMapMapSet(Map<S, Map<T, Set<U>>> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> cloneObject(e.getKey()),
                        e -> cloneMapSet(e.getValue())
                ));
    }*/

    // ====================================================================================================================================================
    // Copy/Getter methods

    public Map<String, Set<String>> getG2T() {
        HashMap<String, Set<String>> newMap = new HashMap<>();
        G2T.forEach((key, value) -> newMap.put(key, new HashSet<>(value)));
        return newMap;
    }

    public Map<String, Map<String, Set<String>>> getG2R() {
        Map<String, Map<String, Set<String>>> newGroupingsMap = new HashMap<>();    // groupings
        G2R.forEach((key, value) -> {
            Map<String, Set<String>> newTopicsMap = new HashMap<>();            // topics per grouping
            newGroupingsMap.put(key, newTopicsMap);
            value.forEach((key1, value1) -> {
                Set<String> newRuleSet = new HashSet<>();                    // rules per topic per grouping
                newTopicsMap.put(key1, newRuleSet);
                newRuleSet.addAll(value1);
            });
        });
        return newGroupingsMap;
    }

    public MetricContext getMetricContextForMetric(Metric m) {
        if (M2MC==null) return null;
        Set<MetricContext> set = M2MC.get(m);
        return set == null ? null : set.iterator().next();
    }

    public Set<MetricConstraint> getMetricConstraints() {
        return new HashSet<>(metricConstraints);
    }

    public Set<LogicalConstraint> getLogicalConstraints() {
        return new HashSet<>(logicalConstraints);
    }

    public HashSet<MetricVariable> getCompositeMetricVariables() {
        return new HashSet<>(CMVar_1);
    }

    public HashSet<String> getCompositeMetricVariableNames() {
        return new HashSet<>(CMVar);
    }

    public HashSet<MetricVariable> getRawMetricVariables() {
        return new HashSet<>(RMVar_1);
    }

    public HashSet<String> getRawMetricVariableNames() {
        return new HashSet<>(RMVar);
    }

    public boolean isMVV(String name) {
        for (String mvv : MVV)
            if (mvv.equals(name)) return true;
        return false;
    }

    public Set<String> getMVV() {
        return new HashSet<>(MVV);
    }

    public Map<String,String> getMvvCP() {
        return new HashMap<>(MvvCP);
    }

    // ====================================================================================================================================================
    // Map- and Set-related helper methods

    @SuppressWarnings("unchecked")
    protected void _addPair(Map map, Object key, Object value) {
        Set valueSet = (Set) map.get(key);
        if (valueSet == null) {
            valueSet = new HashSet<>();
            map.put(key, valueSet);
        }
        if (value instanceof List) valueSet.addAll((List) value);
        else valueSet.add(value);
    }

    public void addEventActionPair(Event event, Action action) {
        _addPair(E2A, E2N.get(event), E2N.get(action));
    }

    public void addEventActionPairs(Event event, List<Action> actions) {
        _addPair(E2A, E2N.get(event), actions.stream().map(E2N::get).collect(Collectors.toList()));
    }

    public void addSLO(ServiceLevelObjective slo) {
        if (E2N.get(slo)!=null) SLO.add(E2N.get(slo));
        else SLO.add(slo.getName());
    }

    public void addComponentSensorPair(ObjectContext objContext, Sensor sensor) {
        if (objContext != null) {
            Component comp = objContext.getComponent();
            Data data = objContext.getData();
            if (comp != null) _addPair(C2S, comp, sensor);
            if (data != null) _addPair(D2S, data, sensor);
        } else {
            _addPair(C2S, null, sensor);
        }
    }

    public void addMonitorsForSensor(String sensorName, Set<Monitor> monitors) {
        if (monitors != null) {
            if (!MONS.contains(sensorName)) {
                MON.addAll(monitors);
                MONS.add(sensorName);
            }
        }
    }

    public boolean containsMonitorsForSensor(String sensorName) {
        return MONS.contains(sensorName);
    }

    public Set<Monitor> getMonitors() {
        return Collections.unmodifiableSet(MON);
    }

    public void addGroupingTopicPair(String grouping, String topic) {
        _addPair(G2T, grouping, topic);
    }

    public void addGroupingTopicPairs(String grouping, List<String> topics) {
        _addPair(G2T, grouping, topics);
    }

    public void addGroupingRulePair(String grouping, String topic, String rule) {
        Map<String, Set<String>> topics = G2R.computeIfAbsent(grouping, k -> new HashMap<>());
        Set<String> rules = topics.computeIfAbsent(topic, k -> new HashSet<>());
        rules.add(rule);
    }

    public void addGroupingRulePairs(String grouping, String topic, List<String> rules) {
        rules.forEach(rule -> addGroupingRulePair(grouping, topic, rule));
    }

    public void addMetricMetricContextPair(Metric m, MetricContext mc) {
        _addPair(M2MC, m, mc);
    }

    public void addMetricMetricContextPairs(Metric m, List<MetricContext> mcs) {
        _addPair(M2MC, m, mcs);
    }

    public void addCompositeMetricVariable(MetricVariable mv) {
        CMVar.add(mv.getName());
        CMVar_1.add(mv);
    }

    public void addCompositeMetricVariables(List<MetricVariable> mvs) {
        mvs.forEach(this::addCompositeMetricVariable);
    }

    public void addRawMetricVariable(MetricVariable mv) {
        RMVar.add(mv.getName());
        RMVar_1.add(mv);
    }

    public void addRawMetricVariables(List<MetricVariable> mvs) {
        mvs.forEach(this::addRawMetricVariable);
    }

    public void addMVV(@NonNull String mvv) {
        MVV.add(mvv);
    }

    public void addMVV(MetricVariable mvv) {
        MVV.add(mvv.getName());
    }

    public void addMVVs(List<MetricVariable> mvvs) {
        mvvs.forEach(this::addMVV);
    }

    public void addMvvCp(String matchingVarName, String varName) {
        MvvCP.put(matchingVarName, varName);
    }

    public void addFunction(Function f) {
        FunctionDefinition fdef = new FunctionDefinition().setName(f.getName()).setExpression(f.getExpression()).setArguments(f.getArguments());
        FUNC.add(fdef);
    }

    public void addMetricConstraint(UnaryConstraint uc) {
        // Get comparison operator
        ComparisonOperatorType op = uc.getComparisonOperator();
        if (op==null)
            throw new IllegalArgumentException("Metric Constraint '"+uc.getName()+"' has no operator specified");

        // Get metric context/variable name
        String metricName = null;
        if (uc instanceof MetricConstraint mc) {
            MetricContext context = mc.getMetricContext();
            if (context!=null) metricName = context.getName();
            if (StringUtils.isBlank(metricName))
                throw new IllegalArgumentException("Metric Constraint '"+mc.getName()+"' has no valid metric context");
        } else
        if (uc instanceof MetricVariableConstraint mvc) {
            MetricVariable mv = mvc.getMetricVariable();
            if (mv!=null) metricName = mv.getName();
            if (StringUtils.isBlank(metricName))
                throw new IllegalArgumentException("Metric Variable Constraint '"+uc.getName()+"' has no valid metric variable");
        } else
            throw new IllegalArgumentException("Invalid Unary Constraint '"+uc.getName()+"' specified. Only metric constraints and metric variable constraints are allowed.");

        // Add threshold information
        metricConstraints.add(
                MetricConstraint.builder()
                        .name(uc.getName())
                        .comparisonOperator(op)
                        .threshold(uc.getThreshold())
                        .build()
        );
    }

    public void addLogicalConstraint(LogicalConstraint logicalConstraint, List<DAGNode> nodeList) {
        String name = logicalConstraint.getName();

        // Check there is a logical operator
        LogicalOperatorType op = logicalConstraint.getLogicalOperator();
        if (op==null)
            throw new IllegalArgumentException("Logical Constraint '"+name+"' has no operator specified");

        // Check there are child constraints
        List<String> childConstraintNames = logicalConstraint.getConstraints()
                .stream().map(NamedElement::getName).toList();
        if (childConstraintNames.size()==0)
            throw new IllegalArgumentException("Logical Constraint '"+name+"' has no child constraints");

        // Add logical constraint information
        logicalConstraints.add(logicalConstraint);
    }

    public void addIfThenConstraint(@NonNull IfThenConstraint ifThenConstraint) {
        String name = ifThenConstraint.getName();

        // Check child constraints
        Constraint ifConstraint = ifThenConstraint.getIf();
        Constraint thenConstraint = ifThenConstraint.getThen();
        Constraint elseConstraint = ifThenConstraint.getElse();
        if (ifConstraint==null || thenConstraint==null)
            throw new IllegalArgumentException("If-Then-Else Constraint '"+name+"' has no IF or no THEN constraint");
        String ifConstraintName = ifConstraint.getName();
        String thenConstraintName = thenConstraint.getName();
        if (StringUtils.isBlank(ifConstraintName) || StringUtils.isBlank(thenConstraintName))
            throw new IllegalArgumentException("IF or THEN constraint in If-Then-Else constraint'"+name+"' has no name");
        String elseConstraintName = elseConstraint != null ? elseConstraint.getName() : null;
        if (elseConstraint!=null && StringUtils.isBlank(elseConstraintName))
            throw new IllegalArgumentException("ELSE constraint in If-Then-Else constraint'"+name+"' has no name");

        // Add if-then-else constraint information
        ifThenConstraints.add(ifThenConstraint);
    }

    // ====================================================================================================================================================
    // Topic-Connections-per-Grouping-related helper methods
    // Auto-fill of Topic connections between Groupings.... (use provide/require methods below)

    public void provideGroupingTopicPair(String grouping, String topic) {
        if (isMVV(topic)) return;
        addGroupingTopicPair(grouping, topic);
        String providerGrouping = providedTopics.get(grouping);
        if (providerGrouping != null && !providerGrouping.equals(grouping)) {
            throw new IllegalArgumentException("Topic " + topic + " is provided more than once: grouping-1=" + grouping + ", grouping-2=" + providedTopics.get(grouping));
        }
        providedTopics.put(topic, grouping);
        needsRefresh = true;
    }

    public void requireGroupingTopicPair(String grouping, String topic) {
        log.debug("requireGroupingTopicPair: grouping={}, topic={}", grouping, topic);
        if (isMVV(topic)) return;
        log.trace("requireGroupingTopicPair: Not an MVV. Good: grouping={}, topic={}", grouping, topic);
        log.trace("requireGroupingTopicPair: requiredTopics BEFORE: {}", requiredTopics);
        addGroupingTopicPair(grouping, topic);
        Set<String> groupings = requiredTopics.computeIfAbsent(topic, k -> new HashSet<>());
        groupings.add(grouping);
        needsRefresh = true;
        log.trace("requireGroupingTopicPair: requiredTopics AFTER: {}", requiredTopics);
    }

    public void requireGroupingTopicPairs(String grouping, List<String> topics) {
        topics.forEach(t -> requireGroupingTopicPair(grouping, t));
    }

    public Map<String, Map<String, Set<String>>> getTopicConnections() {
        if (needsRefresh) {
            log.debug("TranslationContext.getTopicConnections(): Topic connections need refresh");
            topicConnections.clear();

            log.debug("TranslationContext.getTopicConnections(): required-topics={}, provided-topics={}", requiredTopics, providedTopics);

            // for every required topic...
            for (Map.Entry<String, Set<String>> pair : requiredTopics.entrySet()) {
                // get consumer topics for current required topic
                String requiredTopic = pair.getKey();
                Set<String> consumerGroupings = pair.getValue();
                // get provider grouping of current required topic
                String providerGrouping = providedTopics.get(requiredTopic);
                if (providerGrouping == null)
                    throw new IllegalArgumentException("Topic " + requiredTopic + " is not provided in any grouping");
                // remove provider grouping from consumer groupings
                consumerGroupings.remove(providerGrouping);
                // store required topic in 'topicConnections'
                if (consumerGroupings.size() > 0) {
                    // ...get provider grouping topics from topicConnections
                    Map<String, Set<String>> groupingTopics = topicConnections.computeIfAbsent(providerGrouping, k -> new HashMap<>());
                    // ...store consumer groupings for current required topic in provider grouping
                    if (groupingTopics.containsKey(requiredTopic))
                        throw new IllegalArgumentException("INTERNAL ERROR: Required Topic " + requiredTopic + " is already set in provider grouping " + providerGrouping + " in '_TC.topicConnections'");
                    groupingTopics.put(requiredTopic, consumerGroupings);
                }
            }

            needsRefresh = false;
            log.debug("TranslationContext.getTopicConnections(): Topic connections refreshed: {}", topicConnections);
        } else {
            log.debug("TranslationContext.getTopicConnections(): No need to refresh Topic connections. Returning from cache: {}", topicConnections);
        }
        return topicConnections;
    }

    public Map<String, Set<String>> getTopicConnectionsForGrouping(String grouping) {
        return getTopicConnections().get(grouping);
    }

    // ====================================================================================================================================================
    // Element full name generation methods

    public String getFullName(NamedElement elem) {
        log.trace("  getFullName: BEGIN: {}", elem);
        if (elem == null) return null;
        log.trace("  getFullName: NULL check OK: name={}", elem.getName());

        // return cached full-name for element
        String fullName = E2N.get(elem);
        log.trace("  getFullName: Cached Name: {}", fullName);
        if (fullName != null) return fullName;
        log.trace("  getFullName: NO Cached Name:...");

        // else generate full-name for element (and cache it)
        String elemName = elem.getName();
        log.trace("  getFullName:   elem-name={}", elemName);
        String elemType = _getElementType(elem);
        log.trace("  getFullName:   elem-type={}", elemType);
        log.trace("  getFullName:   elem-eContainer={}", elem.getContainer());
        String modelName = elem.getContainer()!=null
                ? elem.getContainer().getName() : null;
        log.trace("  getFullName:   model-name={}", modelName);
        log.trace("  getFullName:   elem-eContainer-eContainer={}",
                elem.getContainer()!=null ? elem.getContainer().getContainer() : null);
        String camelName = elem.getContainer()!=null && elem.getContainer().getContainer()!=null
                ? elem.getContainer().getContainer().getName() : null;
        log.trace("  getFullName:   camel-name={}", camelName);

        fullName = fullNamePattern
                .replace("{TYPE}", elemType)
                .replace("{CAMEL}", Objects.requireNonNullElse(camelName, "C"))
                .replace("{MODEL}", Objects.requireNonNullElse(modelName, "M"))
                .replace("{ELEM}", elemName)
                .replace("{HASH}", Integer.toString(elemName.hashCode()))
                .replace("{COUNT}", Long.toString(elementsCount.getAndIncrement()))
        ;
        log.trace("  getFullName:   New Full name={}", fullName);

        E2N.put(elem, fullName);
        log.trace("  getFullName: END: Cached new FULL name: {}", fullName);

        return fullName;
    }

    public void addElementToNamePair(@NonNull NamedElement elem, @NonNull String fullName) {
        E2N.put(elem, fullName);
    }

    protected String _getElementType(NamedElement e) {
        if (e==null) {
            log.error("Null element passed");
        }
        else if (e instanceof ScalabilityRule) return "RUL";
        else if (e instanceof Event) return "EVT";
        else if (e instanceof Constraint) return "CON";
        else if (e instanceof MetricVariable) return "VAR";
        else if (e instanceof MetricContext) return "CTX";
        else if (e instanceof Metric) return "MET";
        else if (e instanceof MetricTemplate) return "TMP";
        else if (e instanceof OptimisationRequirement) return "OPT";
        else if (e instanceof ServiceLevelObjective) return "SLO";
        else if (e instanceof Requirement) return "REQ";
        else if (e instanceof ObjectContext) return "OBJ";
        else if (e instanceof Sensor) return "SNR";
        else if (e instanceof Function) return "FUN";       //XXX:TODO:  Or FunctionDefinition ??
        else if (e instanceof Schedule) return "CTX";
        else if (e instanceof Window) return "CTX";
        else if (e instanceof ScalingAction) return "ACT";
        else {
            //throw new ModelAnalysisException( String.format("Unknown element type: %s  class=%s", e.getName(), e.getClass().getName()) );
            log.error("Unknown element type: {}  class={}", e.getName(), e.getClass().getName());
        }
        return "XXX";
    }

    // ====================================================================================================================================================
    // Function-Definition-related helper methods

    public Set<FunctionDefinition> getFunctionDefinitions() {
        return new HashSet<>(FUNC);
    }

    // ====================================================================================================================================================
    // Load-Metrics-related helper methods

    public void addLoadAnnotatedMetric(@NonNull String metricName) {
        loadAnnotatedMetricsSet.add(metricName);
    }

    public void addLoadAnnotatedMetrics(@NonNull Set<String> metricNames) {
        loadAnnotatedMetricsSet.addAll(metricNames);
    }

    public Set<String> getLoadAnnotatedMetricsSet() {
        return new HashSet<>(loadAnnotatedMetricsSet);
    }

    // ====================================================================================================================================================
    // Top-Level Metric names helper methods

    public void addTopLevelMetricNames(@NonNull Set<String> set) {
        topLevelMetricNames.addAll(set);
    }

    public void populateTopLevelMetricNames() {
        Set<String> set = DAG.getTopLevelNodes().stream()
                .map(DAGNode::getElementName)
                .collect(Collectors.toSet());
        topLevelMetricNames.clear();
        topLevelMetricNames.addAll(set);
    }

    public Set<String> getTopLevelMetricNames(boolean forcePopulate) {
        if (forcePopulate) populateTopLevelMetricNames();
        return getTopLevelMetricNames();
    }

    public Set<String> getTopLevelMetricNames() {
        if (topLevelMetricNames==null || topLevelMetricNames.isEmpty()) populateTopLevelMetricNames();
        return topLevelMetricNames!=null ? new HashSet<>(topLevelMetricNames) : Collections.emptySet();
    }

    // ====================================================================================================================================================
    // Additional results helper methods

    public <T> T getAdditionalResultsAs(String key, Class<T> clazz) {
        if (getAdditionalResults()==null) return null;
        Object result = getAdditionalResults().get(key);
        if (result==null) return null;
        return clazz.cast(result);
    }

    // ====================================================================================================================================================

    /*public void prepareForSerialization() {
        setDagForSerialization(TranslationContext.convertToSerializableDag(getDAG()));
    }

    public void updateAfterSerialization() {
        if (DAG!=null) {
            DAG.clearDAG();
        } else {
            DAG = new DAG(this::getFullName);
        }
        convertToDAG(this.dagForSerialization, this.DAG);
    }

    public static Dag convertToSerializableDag(DAG dag) {
        return new Dag(
                dag.getAllDAGNodes(),
                dag.getAllDAGEdges().stream()
                        .map(edge->new Edge(edge.getId(), edge.getSource().getId(), edge.getTarget().getId()))
                        .collect(Collectors.toSet())
        );
    }

    public static void convertToDAG(Dag sourceDag, DAG targetDAG) {
        final Map<Long, DAGNode> vertices = new HashMap<>();
        sourceDag.getNodes().forEach(node -> {
            targetDAG.addDAGNode(node);
            vertices.put(node.getId(), node);
        });
        sourceDag.getEdges().forEach(edge -> {
            DAGNode src = vertices.get(edge.getSourceId());
            DAGNode trg = vertices.get(edge.getTargetId());
            targetDAG.addDAGEdge(src, trg);
        });
    }

    @lombok.Data
    public static class Edge implements Serializable {
        private final long id;
        private final long sourceId;
        private final long targetId;
    }

    @lombok.Data
    public static class Dag implements Serializable {
        private final Set<DAGNode> nodes;
        private final Set<Edge> edges;
    }*/
}
