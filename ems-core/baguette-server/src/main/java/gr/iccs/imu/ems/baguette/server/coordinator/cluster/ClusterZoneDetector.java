/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server.coordinator.cluster;

import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Detects the Cluster/Zone the given node must be added,
 * using node's pre-registration info and a set of configured rules
 */
@Slf4j
public class ClusterZoneDetector implements IClusterZoneDetector {
    private final static List<String> DEFAULT_ZONE_DETECTION_RULES = Arrays.asList(
            "'${zone:-}'",
            "'${zone-id:-}'",
            "'${region:-}'",
            "'${region-id:-}'",
            "'${cloud:-}'",
            "'${cloud-id:-}'",
            "'${provider:-}'",
            "'${provider-id:-}'",
            "T(java.time.OffsetDateTime).now().toString()",
//            "'Cluster_'+T(java.lang.System).currentTimeMillis()",
//            "'Cluster_'+T(java.util.UUID).randomUUID()",
            ""
    );
    private final static RULE_TYPE DEFAULT_RULES_TYPE = RULE_TYPE.SPEL;
    private final static List<String> DEFAULT_ZONES = Collections.singletonList("DEFAULT_CLUSTER");
    private final static ASSIGNMENT_TO_DEFAULT_CLUSTERS DEFAULT_ASSIGNMENT_TO_DEFAULT_CLUSTERS = ASSIGNMENT_TO_DEFAULT_CLUSTERS.RANDOM;

    enum RULE_TYPE { SPEL, MAP }
    enum ASSIGNMENT_TO_DEFAULT_CLUSTERS { RANDOM, SEQUENTIAL }

    private RULE_TYPE clusterDetectionRulesType = DEFAULT_RULES_TYPE;
    private List<String> clusterDetectionRules = DEFAULT_ZONE_DETECTION_RULES;
    private List<String> defaultClusters = DEFAULT_ZONES;
    private ASSIGNMENT_TO_DEFAULT_CLUSTERS assignmentToDefaultClusters = DEFAULT_ASSIGNMENT_TO_DEFAULT_CLUSTERS;

    private SpelExpressionParser parser = new SpelExpressionParser();
    private AtomicInteger currentDefaultCluster = new AtomicInteger(0);

    @Override
    public void setProperties(Map<String, String> zoneConfig) {
        log.debug("ClusterZoneDetector: setProperties: BEGIN: config: {}", zoneConfig);

        // Get rules type (Map keys or SpEL expressions)
        RULE_TYPE rulesType = RULE_TYPE.valueOf(
                zoneConfig.getOrDefault("cluster-detector-rules-type", DEFAULT_RULES_TYPE.toString()).toUpperCase());

        // Get rules texts and separator
        String separator = zoneConfig.getOrDefault("cluster-detector-rules-separator", ",");
        String rulesStr = zoneConfig.getOrDefault("cluster-detector-rules", null);
        if (StringUtils.isNotBlank(rulesStr)) {
            List<String> rulesList = Arrays.stream(rulesStr.split(separator))
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .map(String::trim)
                    .collect(Collectors.toList());
            clusterDetectionRules = (rulesList.size()>0) ? rulesList : DEFAULT_ZONE_DETECTION_RULES;
            clusterDetectionRulesType = (rulesList.size()>0) ? rulesType : DEFAULT_RULES_TYPE;
        }

        // Get the default cluster(s)
        List<String> defaultsList = Arrays.stream(zoneConfig.getOrDefault("default-clusters", "").split(","))
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());
        defaultClusters = (defaultsList.size()>0) ? defaultsList : DEFAULT_ZONES;

        // Get assignment method to default clusters
        assignmentToDefaultClusters = ASSIGNMENT_TO_DEFAULT_CLUSTERS.valueOf(
                zoneConfig.getOrDefault("assignment-to-default-clusters", DEFAULT_ASSIGNMENT_TO_DEFAULT_CLUSTERS.toString().toUpperCase()));

        log.debug("ClusterZoneDetector: setProperties: clusterDetectionRulesType: {}", clusterDetectionRulesType);
        log.debug("ClusterZoneDetector: setProperties: clusterDetectionRules: {}", clusterDetectionRules);
        log.debug("ClusterZoneDetector: setProperties: defaultClusters: {}", defaultClusters);
        log.debug("ClusterZoneDetector: setProperties: assignmentToDefaultClusters: {}", assignmentToDefaultClusters);
    }

    @Override
    public String getZoneIdFor(ClientShellCommand csc) {
        log.trace("ClusterZoneDetector: getZoneIdFor: BEGIN: CSC: {}", csc);
        return csc.getClientZone()==null || StringUtils.isBlank(csc.getClientZone().getId())
                ? getZoneIdFor(csc.getNodeRegistryEntry())
                : csc.getClientZone().getId();
    }

    @Override
    public String getZoneIdFor(NodeRegistryEntry entry) {
        log.trace("ClusterZoneDetector: getZoneIdFor: BEGIN: NRE: {}", entry);
        final Map<String, String> info = entry.getPreregistration();

        // Select and initialize the right valueMapper based on rules type
        log.trace("ClusterZoneDetector: getZoneIdFor: PREREGISTRATION-INFO: {}", info);
        Function<String,String> valueMapper;
        switch (clusterDetectionRulesType) {
            case SPEL:
                StandardEvaluationContext context = new StandardEvaluationContext(info);
                context.addPropertyAccessor(new MapAccessor());
                valueMapper = expression -> {
                    log.trace("ClusterZoneDetector: getZoneIdFor: Expression: {}", expression);
                    expression = StringSubstitutor.replace(expression, info);
                    expression = StringSubstitutor.replaceSystemProperties(expression);
                    log.trace("ClusterZoneDetector: getZoneIdFor: SpEL expr.: {}", expression);
                    String result = parser.parseRaw(expression).getValue(context, String.class);
                    log.trace("ClusterZoneDetector: getZoneIdFor:     Result: {}", result);
                    return StringUtils.isBlank(result) ? null : result.trim();
                };
                break;
            case MAP:
                valueMapper = info::get;
                break;
            default:
                throw new IllegalArgumentException("Unsupported RULE_TYPE: "+ clusterDetectionRulesType);
        }

        // Process rules one-by-one, using valueMapper, until one rule yields a non-blank value
        String zoneId = clusterDetectionRules.stream()
                .filter(StringUtils::isNotBlank)
                .peek(s -> log.trace("ClusterZoneDetector: getZoneIdFor: RULE: {}", s))
                .map(valueMapper)
                .peek(s -> log.trace("ClusterZoneDetector: getZoneIdFor: RESULT: {}", s))
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
        log.debug("ClusterZoneDetector: getZoneIdFor: Intermediate: zoneId: {}", zoneId);

        // If all rules yielded blank values then a default cluster id will be selected, using the assignment method
        if (StringUtils.isBlank(zoneId)) {
            switch (assignmentToDefaultClusters) {
                case RANDOM:
                    zoneId = defaultClusters.get((int) (Math.random() * defaultClusters.size()));
                    break;
                case SEQUENTIAL:
                    zoneId = defaultClusters.get(currentDefaultCluster.getAndUpdate(operand -> (operand + 1) % defaultClusters.size()));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported ASSIGNMENT_TO_DEFAULT_CLUSTERS: "+assignmentToDefaultClusters);
            }
        }
        log.debug("ClusterZoneDetector: getZoneIdFor: END: zoneId: {}", zoneId);
        return zoneId;
    }
}
