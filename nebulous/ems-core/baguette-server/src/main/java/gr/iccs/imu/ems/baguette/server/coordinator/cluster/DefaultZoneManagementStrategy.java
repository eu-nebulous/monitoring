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
 * The default Zone Management Strategy used when 'zone-management-strategy-class' property is not set.
 * It groups clients based on domain name, or last byte of IP Address. If neither is available it assigns client
 * in a new zone identified by a random UUID.
 * The first client to join a zone will be instructed to start cluster and become aggregator.
 * Subsequent clients will just join the cluster.
 */
@Slf4j
public class DefaultZoneManagementStrategy implements IZoneManagementStrategy {
    @Override
    public void notPreregisteredNode(ClientShellCommand csc) {
        log.warn("DefaultZoneManagementStrategy: Unexpected node connected: {} @ {}", csc.getId(), csc.getClientIpAddress());
    }

    @Override
    public void alreadyRegisteredNode(ClientShellCommand csc) {
        log.warn("DefaultZoneManagementStrategy: Node connection from an already registered IP address: {} @ {}", csc.getId(), csc.getClientIpAddress());
    }

    @Override
    public synchronized void nodeAdded(ClientShellCommand csc, ClusteringCoordinator coordinator, IClusterZone zone) {
        // Instruct new node to join cluster
        log.info("DefaultZoneManagementStrategy: Node to join cluster: client={}, zone={}", csc.getId(), zone.getId());
        joinToCluster(csc, coordinator, zone);
    }

    private void joinToCluster(ClientShellCommand csc, ClusteringCoordinator coordinator, IClusterZone zone) {
        coordinator.sendClusterKey(csc, zone);
        coordinator.instructClusterJoin(csc, zone, true);

        coordinator.sleep(1000);
        csc.sendCommand("CLUSTER-EXEC broker list");
        //coordinator.sleep(1000);
        //csc.sendCommand("CLUSTER-TEST");
    }

    @Override
    public synchronized void nodeRemoved(ClientShellCommand csc, ClusteringCoordinator coordinator, IClusterZone zone) {
        // Instruct node to leave cluster
        log.info("DefaultZoneManagementStrategy: Node to leave cluster: client={}, zone={}", csc.getId(), zone.getId());
        coordinator.instructClusterLeave(csc, zone);
    }
}
