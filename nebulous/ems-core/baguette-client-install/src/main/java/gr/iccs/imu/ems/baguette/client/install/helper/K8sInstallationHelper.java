/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.helper;

import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsSet;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Baguette Client installation helper for Kubernetes
 */
@Slf4j
@Service
public class K8sInstallationHelper extends AbstractInstallationHelper {
    private static K8sInstallationHelper instance;

    public static AbstractInstallationHelper getInstance() {
        return instance;
    }

    @Override
    public void afterPropertiesSet() {
        log.debug("K8sInstallationHelper.afterPropertiesSet(): configuration: {}", properties);
        instance = this;
    }

    @Override
    public ClientInstallationTask createClientInstallationTask(NodeRegistryEntry entry, TranslationContext translationContext) throws IOException {
        return createClientTask(ClientInstallationTask.TASK_TYPE.INSTALL, entry, translationContext);
    }

    @Override
    public ClientInstallationTask createClientReinstallTask(NodeRegistryEntry entry, TranslationContext translationContext) throws IOException {
        return createClientTask(ClientInstallationTask.TASK_TYPE.REINSTALL, entry, translationContext);
    }

    @Override
    public ClientInstallationTask createClientUninstallTask(NodeRegistryEntry entry, TranslationContext translationContext) throws Exception {
        return createClientTask(ClientInstallationTask.TASK_TYPE.UNINSTALL, entry, translationContext);
    }

    private ClientInstallationTask createClientTask(@NonNull ClientInstallationTask.TASK_TYPE taskType,
                                                    NodeRegistryEntry entry,
                                                    TranslationContext translationContext)
    {
        Map<String, String> nodeMap = initializeNodeMap(entry);

        String baseUrl = nodeMap.get("BASE_URL");
        String clientId = nodeMap.get("CLIENT_ID");
        String ipSetting = nodeMap.get("IP_SETTING");
        String requestId = nodeMap.get("requestId");

        // Extract node identification and type information
        String nodeId = nodeMap.get("id");
        String nodeAddress = nodeMap.get("address");
        String nodeType = nodeMap.get("type");
        String nodeName = nodeMap.get("name");
        String nodeProvider = nodeMap.get("provider");

        if (StringUtils.isBlank(nodeType)) nodeType = "K8S";

        // Create Installation Task for VM node
        ClientInstallationTask task = ClientInstallationTask.builder()
                .id(clientId)
                .taskType(taskType)
                .nodeId(nodeId)
                .requestId(requestId)
                .name(nodeName)
                .address(nodeAddress)
                .type(nodeType)
                .provider(nodeProvider)
                .nodeRegistryEntry(entry)
                .translationContext(translationContext)
                .build();
        log.debug("K8sInstallationHelper.createClientTask(): Created client task: {}", task);
        return task;
    }

    private Map<String, String> initializeNodeMap(NodeRegistryEntry entry) {
        return entry.getPreregistration();
    }

    @Override
    public List<InstructionsSet> prepareInstallationInstructionsForWin(NodeRegistryEntry entry) {
        return null;
    }

    @Override
    public List<InstructionsSet> prepareInstallationInstructionsForLinux(NodeRegistryEntry entry) throws IOException {
        return null;
    }

    @Override
    public List<InstructionsSet> prepareUninstallInstructionsForWin(NodeRegistryEntry entry) {
        return null;
    }

    @Override
    public List<InstructionsSet> prepareUninstallInstructionsForLinux(NodeRegistryEntry entry) throws IOException {
        return null;
    }
}
