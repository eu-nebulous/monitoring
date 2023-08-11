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
import lombok.extern.slf4j.Slf4j;

/**
 * A smarter than default Zone Management Strategy.
 * It groups clients based on domain name, or last byte of IP Address. If neither is available it assigns client
 * in a new zone identified by a random UUID.
 * When a zone contains only one client, no cluster initialization is instructed.
 * When a zone contains exactly two clients, they are both initialized as cluster nodes.
 * If only one client is left in a zone, it is instructed to leave cluster.
 */
@Slf4j
public class AtLeastTwoZoneManagementStrategy implements IZoneManagementStrategy {
    @Override
    public void notPreregisteredNode(ClientShellCommand csc) {
        log.warn("AtLeastTwoZoneManagementStrategy: Unexpected node connected: {} @ {}", csc.getId(), csc.getClientIpAddress());
    }

    @Override
    public void alreadyRegisteredNode(ClientShellCommand csc) {
        log.warn("AtLeastTwoZoneManagementStrategy: Node connection from an already registered IP address: {} @ {}", csc.getId(), csc.getClientIpAddress());
    }

    @Override
    public synchronized void nodeAdded(ClientShellCommand csc, ClusteringCoordinator coordinator, IClusterZone zone) {
        if (zone.getNodes().size() < 2)
            return;

        if (zone.getNodes().size()==2) {
            // Instruct first node to join cluster first (in fact to initialize it)
            ClientShellCommand firstNode = zone.getNodes().get(0);
            log.info("AtLeastTwoZoneManagementStrategy: First node to join cluster: client={}, zone={}", firstNode.getId(), zone.getId());
            joinToCluster(firstNode, coordinator, zone);
        }

        // Instruct new node to join cluster
        log.info("AtLeastTwoZoneManagementStrategy: Node to join cluster: client={}, zone={}", csc.getId(), zone.getId());
        joinToCluster(csc, coordinator, zone);

        // Instruct aggregator election if at least 2 nodes are present in the zone
        if (zone.getNodes().size()==2) {
            log.info("AtLeastTwoZoneManagementStrategy: Elect aggregator: zone={}", zone.getId());
            coordinator.sleep(5000);
            coordinator.electAggregator(zone);
        }
    }

    private void joinToCluster(ClientShellCommand csc, ClusteringCoordinator coordinator, IClusterZone zone) {
        coordinator.sendClusterKey(csc, zone);
        coordinator.instructClusterJoin(csc, zone, false);

        coordinator.sleep(1000);
        csc.sendCommand("CLUSTER-EXEC broker list");
        //coordinator.sleep(1000);
        //csc.sendCommand("CLUSTER-TEST");
    }

    @Override
    public synchronized void nodeRemoved(ClientShellCommand csc, ClusteringCoordinator coordinator, IClusterZone zone) {
        // Instruct node to leave cluster
        log.info("AtLeastTwoZoneManagementStrategy: Node to leave cluster: client={}, zone={}", csc.getId(), zone.getId());
        coordinator.instructClusterLeave(csc, zone);

        if (zone.getNodes().size()==1) {
            // Instruct last node to leave cluster (and terminate cluster)
            ClientShellCommand lastNode = zone.getNodes().get(0);
            log.info("AtLeastTwoZoneManagementStrategy: Last node to leave cluster: client={}, zone={}", lastNode.getId(), zone.getId());
            coordinator.instructClusterLeave(lastNode, zone);
        }
    }
}
