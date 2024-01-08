/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util;

import gr.iccs.imu.ems.baguette.client.install.api.INodeRegistration;
import gr.iccs.imu.ems.baguette.server.NodeRegistry;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.control.controller.ManagementCoordinator;
import gr.iccs.imu.ems.control.controller.NodeRegistrationCoordinator;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRegistrationHelper implements InitializingBean, INodeRegistration {
    private final NodeRegistrationCoordinator nodeRegistrationCoordinator;
    private final ManagementCoordinator managementCoordinator;
    private final NodeRegistry nodeRegistry;
    private final TopicBeacon topicBeacon;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("NodeRegistrationHelper: Initialized");
    }

    @Override
    public String registerNode(String baseUrl, Map<String, Object> nodeInfo, TranslationContext translationContext) throws Exception {
        log.debug("NodeRegistrationHelper: Invoking registerNode: baseUrl={}, nodeInfo={}, TC={}",
                baseUrl, nodeInfo, translationContext);
        return nodeRegistrationCoordinator.registerNode(baseUrl, nodeInfo, translationContext);
    }

    @Override
    public String reinstallNode(String ipAddress, TranslationContext translationContext) throws Exception {
        log.debug("NodeRegistrationHelper: Invoking reinstallNode: baseUrl={}, TC={}",
                ipAddress, translationContext);
        return nodeRegistrationCoordinator.reinstallNode(ipAddress, translationContext);
    }

    @Override
    public String unregisterNode(String nodeAddress, TranslationContext translationContext) throws Exception {
        log.debug("NodeRegistrationHelper: Invoking unregisterNode: node-address={}, TC={}",
                nodeAddress, translationContext);
        return nodeRegistrationCoordinator.unregisterNode(nodeAddress, translationContext);
    }

    @Override
    public NodeRegistryEntry requestNodeDetails(String nodeAddress) throws Exception {
        log.debug("NodeRegistrationHelper: Invoking nodeDetails: node-address={}", nodeAddress);
        return nodeRegistry.getNodeByAddress(nodeAddress);
    }

    @Override
    public void requestInfo() throws Exception {
        log.info("NodeRegistrationHelper: Invoking transmitInfo");
        managementCoordinator.clientStats("*");
        topicBeacon.transmitInfo();
    }
}
