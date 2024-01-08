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
import gr.iccs.imu.ems.util.ClientConfiguration;
import lombok.NonNull;

import java.io.File;
import java.util.List;
import java.util.Set;

public interface IClusterZone {
    String getId();
    void addNode(@NonNull ClientShellCommand csc);
    void removeNode(@NonNull ClientShellCommand csc);
    Set<String> getNodeAddresses();
    List<ClientShellCommand> getNodes();
    ClientShellCommand getNodeByAddress(String address);

    List<NodeRegistryEntry> findAggregatorCapableNodes();

    void addNodeWithoutClient(@NonNull NodeRegistryEntry entry);
    void removeNodeWithoutClient(@NonNull NodeRegistryEntry entry);
    Set<String> getNodeWithoutClientAddresses();
    List<NodeRegistryEntry> getNodesWithoutClient();
    NodeRegistryEntry getNodeWithoutClientByAddress(String address);

    ClientConfiguration getClientConfiguration();
    ClientConfiguration sendClientConfigurationToZoneClients();

    File getClusterKeystoreFile();
    String getClusterKeystoreType();
    String getClusterKeystorePassword();
    String getClusterKeystoreBase64();
}
