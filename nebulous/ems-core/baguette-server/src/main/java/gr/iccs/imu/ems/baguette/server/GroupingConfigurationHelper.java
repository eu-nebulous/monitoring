/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server;

import gr.iccs.imu.ems.util.GroupingConfiguration;

import java.util.Map;

import static gr.iccs.imu.ems.util.GroupingConfiguration.BrokerConnectionConfig;

/**
 * Baguette Client Configuration creation helper
 */
public class GroupingConfigurationHelper {
    public static GroupingConfiguration newGroupingConfiguration(String groupingName, Map<String,BrokerConnectionConfig> connectionConfigs, BaguetteServer server) {
        return GroupingConfiguration.builder()
                .name( groupingName )
                .properties(null)
                .brokerConnections(connectionConfigs)
                .eventTypeNames( server.getTopicsForGrouping(groupingName) )
                .rules( server.getRulesForGrouping(groupingName) )
                .connections( server.getTopicConnectionsForGrouping(groupingName) )
                .constants( server.getConstants() )
                .functionDefinitions( server.getFunctionDefinitions() )
                .brokerUsername( server.getBrokerUsername() )
                .brokerPassword( server.getBrokerPassword() )
                .build();
    }
}
