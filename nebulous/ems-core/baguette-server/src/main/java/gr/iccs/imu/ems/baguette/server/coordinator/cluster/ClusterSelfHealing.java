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
import gr.iccs.imu.ems.common.selfhealing.SelfHealingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ClusterSelfHealing {
    private final SelfHealingManager<NodeRegistryEntry> selfHealingManager;

    // ------------------------------------------------------------------------
    // Server-side self-healing methods
    // ------------------------------------------------------------------------

    public boolean isEnabled() {
        return selfHealingManager.isEnabled();
    }

    List<NodeRegistryEntry> getAggregatorCapableNodesInZone(IClusterZone zone) {
        // Get the normal nodes in the zone that can be Aggregators (i.e. Aggregator and candidates)
        List<NodeRegistryEntry> aggregatorCapableNodes = zone.findAggregatorCapableNodes();
        if (log.isTraceEnabled()) {
            log.trace("getAggregatorCapableNodesInZone: nodes={}", zone.getNodes().stream().map(ClientShellCommand::getNodeRegistryEntry).collect(Collectors.toList()));
            log.trace("getAggregatorCapableNodesInZone: aggregatorCapableNodes={}", aggregatorCapableNodes);
        }
        return aggregatorCapableNodes;
    }

    void updateNodesSelfHealingMonitoring(IClusterZone zone, List<NodeRegistryEntry> aggregatorCapableNodes) {
        if (aggregatorCapableNodes.size()>1) {
            // If zone has >1 aggregator-capable nodes (i.e. Aggregator and Candidates) then stop monitoring them for server-side self-healing
            // Aggregator will monitor them for client-side self-healing
            List<NodeRegistryEntry> nodes = zone.getNodes().stream().map(ClientShellCommand::getNodeRegistryEntry).collect(Collectors.toList());
            log.info("updateNodesSelfHealingMonitoring: Stop self-healing monitor for zone nodes: zone={}, clients={}",
                    zone.getId(), nodes.stream().map(NodeRegistryEntry::getIpAddress).collect(Collectors.toList()));
            selfHealingManager.removeAllNodes(nodes);
        } else if (aggregatorCapableNodes.size()==1) {
            // If zone has exactly 1 aggregator-capable node (i.e. Aggregator) then start monitoring it for server-side self-healing
            // If Aggregator fails then EMS server must recover it
            NodeRegistryEntry lastNode = aggregatorCapableNodes.get(0);
            log.info("updateNodesSelfHealingMonitoring: Start self-healing monitor for the first/last node of zone: zone={}, client={}, address={}", zone.getId(), lastNode.getClientId(), lastNode.getIpAddress());
            selfHealingManager.addNode(lastNode);
        }
    }

    void removeResourceLimitedNodeSelfHealingMonitoring(IClusterZone zone, List<NodeRegistryEntry> aggregatorCapableNodes) {
        // Remove self-healing responsibility of RL nodes from EMS server, if there are aggregator-capable nodes in the zone (since one will be/become Aggregator)
        List<NodeRegistryEntry> clientlessNodes = zone.getNodesWithoutClient();
        log.trace("removeResourceLimitedNodeSelfHealingMonitoring: AC-nodes: {}", aggregatorCapableNodes);
        log.trace("removeResourceLimitedNodeSelfHealingMonitoring: RL-nodes: {}", clientlessNodes);
        if (! clientlessNodes.isEmpty() && ! aggregatorCapableNodes.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("removeResourceLimitedNodeSelfHealingMonitoring: Zone has aggregators-capable node(s) and nodes without client: zone={}, nodes-without-client={}, aggregator-capable-nodes={}",
                        zone.getId(), clientlessNodes.stream().map(NodeRegistryEntry::getIpAddress).collect(Collectors.toList()),
                        aggregatorCapableNodes.stream().map(NodeRegistryEntry::getIpAddress).collect(Collectors.toList()));
            }

            boolean containsNodesWithoutClient = selfHealingManager.containsAny(zone.getNodesWithoutClient());
            log.trace("removeResourceLimitedNodeSelfHealingMonitoring: containsAny={}", containsNodesWithoutClient);
            if (containsNodesWithoutClient) {
                // Remove RL nodes self-healing responsibility from EMS server
                List<String> zoneNodesWithoutClient = zone.getNodesWithoutClient().stream().map(NodeRegistryEntry::getIpAddress).collect(Collectors.toList());
                log.info("removeResourceLimitedNodeSelfHealingMonitoring: Zone has nodes without client. Will remove self-healing responsibility from EMS server: {}", zoneNodesWithoutClient);
                selfHealingManager.removeAllNodes(zone.getNodesWithoutClient());
                log.debug("removeResourceLimitedNodeSelfHealingMonitoring: Removed self-healing responsibility from EMS server, for zone nodes without client: {}", zoneNodesWithoutClient);
            } else {
                log.trace("removeResourceLimitedNodeSelfHealingMonitoring: No nodes without client have been assigned to EMS server: zone={}", zone.getId());
            }
        }
    }

    void addResourceLimitedNodeSelfHealingMonitoring(IClusterZone zone, List<NodeRegistryEntry> aggregatorCapableNodes) {
        // Add self-healing responsibility of RL nodes to EMS server, if there are no aggregator-capable nodes in the zone
        List<NodeRegistryEntry> clientlessNodes = zone.getNodesWithoutClient();
        log.trace("addResourceLimitedNodeSelfHealingMonitoring: AC-nodes: {}", aggregatorCapableNodes);
        log.trace("addResourceLimitedNodeSelfHealingMonitoring: RL-nodes: {}", clientlessNodes);
        if (! clientlessNodes.isEmpty() && aggregatorCapableNodes.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("addResourceLimitedNodeSelfHealingMonitoring: Zone has no aggregator-capable nodes but it has nodes without client: zone={}, nodes-without-client={}, aggregator-capable-nodes={}",
                        zone.getId(), clientlessNodes.stream().map(NodeRegistryEntry::getIpAddress).collect(Collectors.toList()),
                        aggregatorCapableNodes.stream().map(NodeRegistryEntry::getIpAddress).collect(Collectors.toList()));
            }

            // Add RL nodes self-healing responsibility to EMS server
            List<String> zoneNodesWithoutClient = zone.getNodesWithoutClient().stream().map(NodeRegistryEntry::getIpAddress).collect(Collectors.toList());
            log.info("removeNodeFromTopology: Zone has only members without client. Will move self-healing responsibility to EMS server: {}", zoneNodesWithoutClient);
            selfHealingManager.addAllNodes(zone.getNodesWithoutClient());
            log.debug("removeNodeFromTopology: Moved self-healing responsibility to EMS server, for nodes without client: {}", zoneNodesWithoutClient);
        }
    }
}
