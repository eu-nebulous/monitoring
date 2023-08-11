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

import java.util.Map;

public interface IZoneManagementStrategy {
    default boolean allowAlreadyPreregisteredNode(Map<String,Object> nodeInfo) { return true; }
    default boolean allowAlreadyPreregisteredNode(NodeRegistryEntry entry) { return true; }
    default boolean allowAlreadyRegisteredNode(ClientShellCommand csc) { return true; }
    default boolean allowAlreadyRegisteredNode(NodeRegistryEntry entry) { return true; }
    default boolean allowNotPreregisteredNode(ClientShellCommand csc) { return true; }
    default boolean allowNotPreregisteredNode(NodeRegistryEntry entry) { return true; }
    default void notPreregisteredNode(ClientShellCommand csc) { }
    default void notPreregisteredNode(NodeRegistryEntry entry) { }
    default void alreadyRegisteredNode(ClientShellCommand csc) { }
    default void alreadyRegisteredNode(NodeRegistryEntry entry) { }

    default void nodeAdded(ClientShellCommand csc, ClusteringCoordinator coordinator, IClusterZone zoneInfo) { }
    default void nodeRemoved(ClientShellCommand csc, ClusteringCoordinator coordinator, IClusterZone zoneInfo) { }
}
