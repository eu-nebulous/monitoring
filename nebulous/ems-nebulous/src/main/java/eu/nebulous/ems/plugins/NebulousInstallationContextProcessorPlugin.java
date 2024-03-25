/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.plugins;

import eu.nebulous.ems.service.ExternalBrokerConnectionInfoService;
import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.InstallationContextProcessorPlugin;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class NebulousInstallationContextProcessorPlugin implements InstallationContextProcessorPlugin {
    @Override
    public void processBeforeInstallation(ClientInstallationTask task, long taskCounter) {
        ExternalBrokerConnectionInfoService connectionInfoService = ExternalBrokerConnectionInfoService.getInstance();

        if (! connectionInfoService.isEnabled()) {
            log.warn("NebulousInstallationContextProcessorPlugin: External Broker service is disabled");
            return;
        }
        if (StringUtils.isBlank(connectionInfoService.getBrokerAddress()) || connectionInfoService.getBrokerPort()<0) {
            log.warn("NebulousInstallationContextProcessorPlugin: Missing external broker connection info");
            return;
        }

        Map<String, String> valusMap = task.getNodeRegistryEntry().getPreregistration();
        valusMap.put("EXTERNAL_BROKER_ADDRESS", connectionInfoService.getBrokerAddress());
        valusMap.put("EXTERNAL_BROKER_PORT", Integer.toString(connectionInfoService.getBrokerPort()));
        valusMap.put("EXTERNAL_BROKER_USERNAME", connectionInfoService.getBrokerUsername());
        valusMap.put("EXTERNAL_BROKER_PASSWORD", connectionInfoService.getBrokerPassword());
    }

    @Override
    public void processAfterInstallation(ClientInstallationTask task, long taskCounter, boolean success) {
    }
}
