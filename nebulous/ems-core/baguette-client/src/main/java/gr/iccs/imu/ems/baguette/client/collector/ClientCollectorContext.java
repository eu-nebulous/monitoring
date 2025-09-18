/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.collector;

import gr.iccs.imu.ems.baguette.client.BaguetteClientProperties;
import gr.iccs.imu.ems.baguette.client.CommandExecutor;
import gr.iccs.imu.ems.baguette.client.Sshc;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.common.client.SshClient;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.util.ClientConfiguration;
import gr.iccs.imu.ems.util.GroupingConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientCollectorContext implements CollectorContext<BaguetteClientProperties> {
    private final CommandExecutor commandExecutor;

    public Map<String, GroupingConfiguration> getGroupings() {
        return commandExecutor.getGroupings();
    }

    @Override
    public List<ClientConfiguration> getNodeConfigurations() {
        return Collections.singletonList(commandExecutor.getClientConfiguration());
    }

    @Override
    public Set<Serializable> getNodesWithoutClient() {
        return commandExecutor.getClientConfiguration()!=null
                ? commandExecutor.getClientConfiguration().getNodesWithoutClient() : null;
    }

    @Override
    public Set<Serializable> getPodInfoSet() {
        return commandExecutor.getClientConfiguration()!=null
                ? commandExecutor.getClientConfiguration().getPodInfo() : null;
    }

    @Override
    public boolean isAggregator() {
        return commandExecutor.isAggregator();
    }

    @Override
    public PUBLISH_RESULT sendEvent(String connectionString, String destinationName, EventMap event, boolean createDestination) {
        return commandExecutor.sendEvent(connectionString, destinationName, event, createDestination);
    }

    @Override
    public SshClient<BaguetteClientProperties> getSshClient() {
        return new Sshc();
    }

    @Override
    public BaguetteClientProperties getSshClientProperties() {
        return new BaguetteClientProperties();
    }
}
