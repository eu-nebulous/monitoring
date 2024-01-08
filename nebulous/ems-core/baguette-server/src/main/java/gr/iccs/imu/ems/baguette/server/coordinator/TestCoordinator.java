/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server.coordinator;

import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

import static gr.iccs.imu.ems.util.GroupingConfiguration.BrokerConnectionConfig;

@Slf4j
public class TestCoordinator extends NoopCoordinator {
    @Override
    public synchronized void register(ClientShellCommand c) {
        if (!_logInvocation("register", c, true)) return;
        _do_register(c);
    }

    protected synchronized void _do_register(ClientShellCommand c) {
        // prepare configuration
        Map<String, BrokerConnectionConfig> connCfgMap = new LinkedHashMap<>();
        BrokerConnectionConfig groupingConn = getUpperwareBrokerConfig(server);
        connCfgMap.put(server.getUpperwareGrouping(), groupingConn);
        log.trace("ClusteringCoordinator: GLOBAL broker config.: {}", groupingConn);

        connCfgMap.put("PER_CLOUD", groupingConn = getGroupingBrokerConfig("PER_CLOUD", c));
        log.trace("TestCoordinator.test(): {} broker config.: {}", "PER_CLOUD", groupingConn);

        // prepare Broker-CEP configuration
        log.info("TestCoordinator.test(): --------------------------------------------------");
        log.info("TestCoordinator.test(): Sending grouping configurations...");
        sendGroupingConfigurations(connCfgMap, c, server);
        log.info("TestCoordinator.test(): Sending grouping configurations... done");

        // Set active grouping and send an event
        String grouping = "PER_INSTANCE";
        try {
            Thread.sleep(500);
        } catch (Exception ex) {
        }
        log.info("TestCoordinator.test(): --------------------------------------------------");
        log.info("TestCoordinator.test(): Setting active grouping: {}", grouping);
        c.setActiveGrouping(grouping);

        try {
            Thread.sleep(5000);
        } catch (Exception ex) {
        }
        log.info("TestCoordinator.test(): --------------------------------------------------");
    }
}
