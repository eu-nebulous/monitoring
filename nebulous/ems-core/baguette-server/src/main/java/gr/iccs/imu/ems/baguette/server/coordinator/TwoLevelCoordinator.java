/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server.coordinator;

import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import gr.iccs.imu.ems.common.selfhealing.SelfHealingManager;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.util.GROUPING;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static gr.iccs.imu.ems.util.GroupingConfiguration.BrokerConnectionConfig;

@Slf4j
public class TwoLevelCoordinator extends NoopCoordinator {
    private GROUPING globalGrouping;
    private GROUPING nodeGrouping;

    @Override
    public boolean isSupported(final TranslationContext _TC) {
        // Check if there are at least 2 levels in architecture
        Set<String> groupings = _TC.getG2R().keySet();
        if (!groupings.contains("GLOBAL")) return false;
        return groupings.size()>1;
    }

    @Override
    public void initialize(final TranslationContext TC, String upperwareGrouping, BaguetteServer server, Runnable callback) {
        if (!isSupported(TC))
            throw new IllegalArgumentException("Passed Translation Context is not supported");

        super.initialize(TC, upperwareGrouping, server, callback);
        List<GROUPING> groupings = TC.getG2R().keySet().stream()
                .map(GROUPING::valueOf)
                .sorted()
                .collect(Collectors.toList());
        log.debug("TwoLevelCoordinator.initialize(): Groupings: {}", groupings);
        this.globalGrouping = groupings.get(0);
        this.nodeGrouping = groupings.get(1);
        log.info("TwoLevelCoordinator.initialize(): Groupings: top-level={}, node-level={}",
                globalGrouping, nodeGrouping);

        // Configure Self-Healing manager
        server.getSelfHealingManager().setMode(SelfHealingManager.MODE.ALL);
    }

    @Override
    public boolean processClientInput(ClientShellCommand csc, String line) {
        if (StringUtils.isBlank(line)) return false;
        log.info("TwoLevelCoordinator: Client: {} @ {} -- Input: {}",
                csc.getId(), csc.getClientIpAddress(), line);
        return true;
    }

    @Override
    public synchronized void register(ClientShellCommand csc) {
        if (!_logInvocation("register", csc, true)) return;

        // prepare configuration
        Map<String,BrokerConnectionConfig> connCfgMap = new LinkedHashMap<>();
        BrokerConnectionConfig groupingConn = getUpperwareBrokerConfig(server);
        connCfgMap.put(server.getUpperwareGrouping(), groupingConn);
        log.trace("TwoLevelCoordinator: GLOBAL broker config.: {}", groupingConn);

        // collect client configurations per grouping
        for (String groupingName : server.getGroupingNames()) {
            groupingConn = getGroupingBrokerConfig(groupingName, csc);
            connCfgMap.put(groupingName, groupingConn);
            log.trace("TwoLevelCoordinator: {} broker config.: {}", groupingName, groupingConn);
        }

        // send grouping configurations to client
        log.info("TwoLevelCoordinator: --------------------------------------------------");
        log.info("TwoLevelCoordinator: Sending grouping configurations to client {}...\n{}", csc.getId(), connCfgMap);
        sendGroupingConfigurations(connCfgMap, csc, server);
        log.info("TwoLevelCoordinator: Sending grouping configurations to client {}... done", csc.getId());
        sleep(500);

        // Set active grouping
        String grouping = nodeGrouping.name();
        log.info("TwoLevelCoordinator: --------------------------------------------------");
        log.info("TwoLevelCoordinator: Setting active grouping of client {}: {}", csc.getId(), grouping);
        csc.setActiveGrouping(grouping);
        log.info("TwoLevelCoordinator: --------------------------------------------------");
    }

    @Override
    public synchronized void unregister(ClientShellCommand csc) {
        if (!_logInvocation("unregister", csc, true)) return;
        log.info("TwoLevelCoordinator: --------------------------------------------------");
        log.info("TwoLevelCoordinator: Client unregistered: {} @ {}", csc.getId(), csc.getClientIpAddress());
    }
}