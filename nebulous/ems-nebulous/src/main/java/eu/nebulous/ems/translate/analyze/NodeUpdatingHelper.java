/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.dag.DAGNode;
import gr.iccs.imu.ems.translate.model.BusyStatusMetricVariable;
import gr.iccs.imu.ems.translate.model.MetricVariable;
import gr.iccs.imu.ems.translate.model.NamedElement;
import gr.iccs.imu.ems.translate.model.ServiceLevelObjective;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static eu.nebulous.ems.translate.analyze.AnalysisUtils.createException;
import static eu.nebulous.ems.translate.analyze.AnalysisUtils.getContainerName;

// ------------------------------------------------------------------------
//  DAG node updating with requiring components, and SLO per component
//  grouping methods
// ------------------------------------------------------------------------

@Slf4j
@Service
@RequiredArgsConstructor
class NodeUpdatingHelper extends AbstractHelper {
    private final NebulousEmsTranslatorProperties properties;

    void buildComponentsToSLOsMap(TranslationContext _TC, DocumentContext ctx, Set<String> componentNames) {
        // Group SLOs per component or scope
        ConcurrentMap<String, Set<NamesKey>> componentOrScopesToSLOsMapping = $$(_TC).allSLOs.entrySet().stream()
                .collect(Collectors.groupingByConcurrent(
                        entry -> getContainerName(entry.getValue()),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));
        log.trace("MetricModelAnalyzer.analyzeModel(): componentOrScopesToSLOsMapping: {}", componentOrScopesToSLOsMapping);

        // Build component-to-scope mapping
        Map<String, Set<String>> componentsToScopesMap = createComponentsToScopesMapping(ctx, componentNames);
        log.trace("MetricModelAnalyzer.analyzeModel(): componentsToScopesMap: {}", componentsToScopesMap);

        // Build integrated components SLO sets
        Map<String, Set<NamesKey>> componentsToSLOsMap = componentNames.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        componentName -> {
                            Set<String> componentScopes = new LinkedHashSet<>(componentsToScopesMap.get(componentName));
                            componentScopes.add(componentName);
                            return componentScopes.stream()
                                    .map(componentOrScopesToSLOsMapping::get)
                                    .map(set -> set!=null ? set : new LinkedHashSet<NamesKey>())
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toSet());
                        }
                ));
        log.trace("MetricModelAnalyzer.analyzeModel(): componentsToSLOsMap: {}", componentsToSLOsMap);

        $$(_TC).componentsToSLOsMap = componentsToSLOsMap;
    }

    Map<String, Set<String>> createComponentsToScopesMapping(DocumentContext ctx, Set<String> componentNames) {
        Map<String, Set<String>> componentToScopeMap = new LinkedHashMap<>();
        ctx.read("$.spec.scopes.*", List.class).stream().filter(Objects::nonNull).forEach(scope -> {
            // Get scope name and scope components
            String scopeName = JsonPath.read(scope, "$.name").toString();
            Object oComponents = ((Map)scope).get("components");

            // Process scope components spec
            Set<String> includedComponents = componentNames;
            if (oComponents instanceof String s)
                includedComponents = Arrays.stream(s.split(","))
                        .map(String::trim).filter(str->!str.isBlank())
                        .collect(Collectors.toSet());
            if (oComponents instanceof List l)
                includedComponents = ((List<Object>) l).stream().filter(Objects::nonNull)
                        .filter(i->i instanceof String).map(i -> i.toString().trim())
                        .filter(str -> !str.isBlank()).collect(Collectors.toSet());
            if (includedComponents.isEmpty())
                includedComponents = componentNames;
            Set<String> notFound = includedComponents.stream()
                    .filter(i -> !componentNames.contains(i)).collect(Collectors.toSet());
            if (!notFound.isEmpty())
                throw createException("Scope component(s) "+notFound+" have not been specified in scope: "+scopeName);

            // Update results map
            includedComponents.forEach(componentName -> componentToScopeMap
                    .computeIfAbsent(componentName, name -> new LinkedHashSet<>())
                    .add(scopeName));
        });
        log.trace("Components-to-Scopes map: {}", componentToScopeMap);
        return componentToScopeMap;
    }

    void buildSLOToComponentsMap(TranslationContext _TC) {
        ConcurrentMap<NamesKey, Set<String>> slosToComponentsMap = $$(_TC).componentsToSLOsMap.entrySet().stream()
                .map(entry -> entry.getValue().stream()
                        .map(x -> new AbstractMap.SimpleEntry<>(entry.getKey(), x))
                        .toList())
                .flatMap(Collection::stream)
                .collect(Collectors.groupingByConcurrent(
                        AbstractMap.SimpleEntry::getValue,
                        Collectors.mapping(AbstractMap.SimpleEntry::getKey, Collectors.toSet())
                ));
        log.trace("MetricModelAnalyzer.analyzeModel(): slosToComponentsMap: {}", slosToComponentsMap);

        $$(_TC).slosToComponentsMap = slosToComponentsMap;
    }

    void updateDAGNodesWithComponents(TranslationContext _TC, Set<String> componentNames) {
        _TC.getDAG().getTopLevelNodes().forEach(tlNode -> {
            if (tlNode.getElement() instanceof ServiceLevelObjective slo) {
                Set<String> components = $$(_TC).slosToComponentsMap.get(NamesKey.create(slo.getName()));
                log.trace("Updating DAG node components: slo-name={}, slo-components={}", slo.getName(),  components);
                tlNode.getProperties().put("components", components);
                updateComponents(_TC, tlNode);
            } else if (tlNode.getElement() instanceof BusyStatusMetricVariable bsMv) {
                if (log.isTraceEnabled())
                    log.trace("Updating DAG node components: BS-name={}, BS-components={}, BS-metric={}",
                            bsMv.getName(), componentNames,
                            _TC.getDAG().getNodeChildren(tlNode).stream()
                                    .map(DAGNode::getElement).map(NamedElement::getName).toList());
                tlNode.getProperties().put("components", componentNames);
                _TC.getDAG().getNodeChildren(tlNode).forEach(bsMetricNode -> {
                    bsMetricNode.getProperties().put("components", componentNames);
                    updateComponents(_TC, bsMetricNode);
                });
            } else if (tlNode.getElement() instanceof MetricVariable mv &&
                    StringUtils.equalsIgnoreCase(tlNode.getName(), properties.getOrphanMetricsParentName()))
            {
                if (log.isTraceEnabled())
                    log.trace("Updating DAG node components: Orphan metrics: {}",
                            _TC.getDAG().getNodeChildren(tlNode).stream()
                                    .map(DAGNode::getElement).map(NamedElement::getName).toList());
                _TC.getDAG().getNodeChildren(tlNode).forEach(bsMetricNode -> {
                    bsMetricNode.getProperties().put("components", componentNames);
                    updateComponents(_TC, bsMetricNode);
                });
            } else {
                log.error("ERROR: Updating DAG node components: Unexpected top-level DAG node encountered: {}",
                        tlNode.getElement()!=null ? tlNode.getElement().getName()+" "+tlNode.getElement().getClass() : null);
                throw createException("Unexpected top-level DAG node encountered: " + tlNode);
            }
        });

        // Traverse DAG and log node components
        log.debug("DAG node components: traversing DAG...");
        _TC.getDAG().traverseDAG(node -> log.debug("-----> {} :: {}", node.getName(), node.getProperties().get("components")));
    }

    private void updateComponents(TranslationContext _TC, DAGNode parentNode) {
        Set<String> parentComponents = (Set<String>) parentNode.getProperties().get("components");
        if (parentComponents==null)
            throw createException("Parent 'components' property has not been set: "+parentNode);
        _TC.getDAG().getNodeChildren(parentNode).forEach(node -> {
            Set<String> componentsSet = (Set<String>) node.getProperties()
                    .computeIfAbsent("components", k -> new LinkedHashSet<>());
            log.trace("Updating DAG node components: ... node-name={}, node-components BEFORE={}", node.getName(),  componentsSet);
            int s1 = componentsSet.size();
            componentsSet.addAll(parentComponents);
            log.trace("Updating DAG node components: ... node-name={}, node-components  AFTER={}", node.getName(),  componentsSet);
            if (s1!=componentsSet.size())
                updateComponents(_TC, node);
            else
                log.trace("Updating DAG node components: ... No further decomposition required: {}", node.getName());
        });
    }

    void removeCommonOrphanMetricsParent(TranslationContext _TC) {
        DAGNode orphanMetricsNode = _TC.getDAG().getTopLevelNodes().stream()
                .filter(tlNode -> tlNode.getElement() instanceof MetricVariable
                        && StringUtils.equalsIgnoreCase(tlNode.getName(), properties.getOrphanMetricsParentName()))
                .findAny().orElse(null);
        log.trace("MetricModelAnalyzer.analyzeModel(): orphanMetricsNode: {}", orphanMetricsNode);

        if (orphanMetricsNode != null) {
            DAGNode root = _TC.getDAG().getRootNode();
            _TC.getDAG().getNodeChildren(orphanMetricsNode).forEach(node -> {
                log.trace("MetricModelAnalyzer.analyzeModel(): Making orphan metric top-level: {}", node);
                _TC.getDAG().addDAGEdge(root, node);
                _TC.getDAG().removeEdge(orphanMetricsNode, node);
            });
        }
    }
}