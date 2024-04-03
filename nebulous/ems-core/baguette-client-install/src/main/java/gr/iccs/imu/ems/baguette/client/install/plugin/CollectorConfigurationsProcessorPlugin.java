/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.InstallationContextProcessorPlugin;
import gr.iccs.imu.ems.util.ConfigWriteService;
import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Installation context processor plugin for generating 'collector-configurations' setting
 * used in baguette-client[.yml/.properties] configuration file.
 * It sets the 'COLLECTOR_CONFIGURATIONS' variable in pre-registration context.
 */
@Slf4j
@Data
@Service
public class CollectorConfigurationsProcessorPlugin implements InstallationContextProcessorPlugin {
    @Override
    public void processBeforeInstallation(ClientInstallationTask task, long taskCounter) {
        log.debug("CollectorConfigurationsProcessorPlugin: Task #{}: processBeforeInstallation: BEGIN", taskCounter);
        log.trace("CollectorConfigurationsProcessorPlugin: Task #{}: processBeforeInstallation: BEGIN: task={}", taskCounter, task);

        Map<String, List<Object>> collectorConfigs = new LinkedHashMap<>();
        String collectorConfigsStr = null;

        task.getTranslationContext().getMON().forEach(monitor -> {
            log.trace("CollectorConfigurationsProcessorPlugin: Task #{}: Processing monitor: {}", taskCounter, monitor);

            // Get sensor configuration
            Map<String,Object> sensorConfig = monitor.getSensor().getConfiguration();

            // Process Destination aliases, if specified in configuration
            if (sensorConfig!=null) {
                if (monitor.getSensor().isPullSensor()) {
                    if (sensorConfig.get("type") instanceof String type && StringUtils.isNotBlank(type)) {
                        collectorConfigs
                                .computeIfAbsent(type, key->new LinkedList<>())
                                .add(monitor.getSensor());
                    }
                }
            }
        });

        try {
            log.debug("CollectorConfigurationsProcessorPlugin: Task #{}: Pull-Sensor collector configurations: \n{}", taskCounter, collectorConfigs);
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            collectorConfigsStr = mapper
                    .writeValueAsString(collectorConfigs);
        } catch (JsonProcessingException e) {
            log.error("CollectorConfigurationsProcessorPlugin: Task #{}: EXCEPTION while processing sensor configs. Skipping them.\n",
                    taskCounter, e);
        }
        if (StringUtils.isBlank(collectorConfigsStr))
            collectorConfigsStr = "{ }";
        log.debug("CollectorConfigurationsProcessorPlugin: Task #{}: Pull-Sensor collector configurations string: \n{}", taskCounter, collectorConfigsStr);

        task.getNodeRegistryEntry().getPreregistration().put(EmsConstant.COLLECTOR_CONFIGURATIONS_VAR, collectorConfigsStr);
        log.debug("CollectorConfigurationsProcessorPlugin: Task #{}: Collector configurations: \n{}", taskCounter, collectorConfigsStr);

        // Store collector configurations in config service
        try {
            ConfigWriteService.getInstance()
                    .getOrCreateConfigFile(
                            EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FILE,
                            EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FORMAT)
                    .put(EmsConstant.COLLECTOR_CONFIGURATIONS_VAR, collectorConfigsStr);
        } catch (Exception e) {
            log.error("CollectorConfigurationsProcessorPlugin: Task #{}: Failed to store Collector Configurations in config. file: {}, Exception: ",
                    taskCounter, EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FILE, e);
        }

        log.debug("CollectorConfigurationsProcessorPlugin: Task #{}: processBeforeInstallation: END", taskCounter);
    }

    @Override
    public void processAfterInstallation(ClientInstallationTask task, long taskCounter, boolean success) {
        log.debug("CollectorConfigurationsProcessorPlugin: Task #{}: processAfterInstallation: success={}", taskCounter, success);
        log.trace("CollectorConfigurationsProcessorPlugin: Task #{}: processAfterInstallation: success={}, task={}", taskCounter, success, task);
    }

    @Override
    public void start() {
        log.debug("CollectorConfigurationsProcessorPlugin: Started");
    }

    @Override
    public void stop() {
        log.debug("CollectorConfigurationsProcessorPlugin: Stopped");
    }
}
