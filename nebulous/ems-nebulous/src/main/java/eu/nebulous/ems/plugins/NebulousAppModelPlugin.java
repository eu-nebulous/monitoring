/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.plugins;

import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.plugin.AllowedTopicsProcessorPlugin;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.control.controller.ControlServiceRequestInfo;
import gr.iccs.imu.ems.control.plugin.AppModelPlugin;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.util.ConfigWriteService;
import gr.iccs.imu.ems.util.EmsConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class NebulousAppModelPlugin implements AppModelPlugin {
    private final AllowedTopicsProcessorPlugin allowedTopicsProcessorPlugin;
    private final ConfigWriteService configWriteService;

    @Override
    public void preProcessingNewAppModel(String appModelId, ControlServiceRequestInfo requestInfo) {
        log.debug("NebulousAppModelPlugin: Nothing to do. Args: appModelId={}, requestInfo={}", appModelId, requestInfo);
    }

    @Override
    public void postProcessingNewAppModel(String appModelId, ControlServiceRequestInfo requestInfo, TranslationContext translationContext) {
        log.debug("NebulousAppModelPlugin: BEGIN: appModelId={}, requestInfo={}", appModelId, requestInfo);

        // Get collector allowed topics
        NodeRegistryEntry entry = new NodeRegistryEntry(null, null, null);
        ClientInstallationTask task = ClientInstallationTask.builder()
                .nodeRegistryEntry(entry)
                .translationContext(translationContext)
                .build();
        allowedTopicsProcessorPlugin.processBeforeInstallation(task, -1);
        String allowedTopics = task.getNodeRegistryEntry().getPreregistration().get(EmsConstant.COLLECTOR_ALLOWED_TOPICS_VAR);
        log.debug("NebulousAppModelPlugin: collector-allowed-topics: {}", allowedTopics);

        if (StringUtils.isBlank(allowedTopics)) {
            log.debug("NebulousAppModelPlugin: END: No value for 'collector-allowed-topics' setting: appModelId={}, requestInfo={}", appModelId, requestInfo);
            return;
        }

        // Append collector-allowed-topics in ems-client-configmap file
        try {
            configWriteService
                    .getOrCreateConfigFile(
                            EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FILE,
                            EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FORMAT)
                    .put(EmsConstant.COLLECTOR_ALLOWED_TOPICS_VAR, allowedTopics);
            log.debug("NebulousAppModelPlugin: END: Updated ems-client-configmap file: {}", EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FILE);
        } catch (IOException e) {
            log.error("NebulousAppModelPlugin: EXCEPTION while updating ems-client-configmap file, during post-processing of new App Model: appModelId={}, requestInfo={}\nException: ",
                    appModelId, requestInfo, e);
        }
    }
}
