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
import gr.iccs.imu.ems.translate.model.Monitor;
import gr.iccs.imu.ems.util.ConfigWriteService;
import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Installation context processor plugin for generating 'allowed-topics' setting
 * used in baguette-client[.yml/.properties] configuration file.
 * It sets the 'COLLECTOR_ALLOWED_TOPICS' variable in pre-registration context.
 */
@Slf4j
@Data
@Service
public class AllowedTopicsProcessorPlugin implements InstallationContextProcessorPlugin {
    @Override
    public void processBeforeInstallation(ClientInstallationTask task, long taskCounter) {
        log.debug("AllowedTopicsProcessorPlugin: Task #{}: processBeforeInstallation: BEGIN", taskCounter);
        log.trace("AllowedTopicsProcessorPlugin: Task #{}: processBeforeInstallation: BEGIN: task={}", taskCounter, task);

        StringBuilder sbAllowedTopics = new StringBuilder();
        Set<String> addedTopicsSet = new HashSet<>();
        Map<String, List<Object>> collectorConfigs = new LinkedHashMap<>();

        boolean first = true;
        for (Monitor monitor : task.getTranslationContext().getMON()) {
            try {
                log.trace("AllowedTopicsProcessorPlugin: Task #{}: Processing monitor: {}", taskCounter, monitor);

                String metricName = monitor.getMetric();
                if (!addedTopicsSet.contains(metricName)) {
                    if (first) first = false;
                    else sbAllowedTopics.append(", ");

                    sbAllowedTopics.append(metricName);
                    addedTopicsSet.add(metricName);
                }

                // Get sensor configuration
                Map<String,Object> sensorConfig = monitor.getSensor().getConfiguration();

                // Process Destination aliases, if specified in configuration
                if (sensorConfig!=null) {
                    String k = sensorConfig.keySet().stream()
                            .filter(key -> StrUtil.compareNormalized(key, EmsConstant.COLLECTOR_DESTINATION_ALIASES))
                            .findAny().orElse(null);
                    String aliases = (k!=null && sensorConfig.get(k) instanceof String) ? sensorConfig.get(k).toString() : null;

                    if (StringUtils.isNotBlank(aliases)) {
                        for (String alias : aliases.trim().split(EmsConstant.COLLECTOR_DESTINATION_ALIASES_DELIMITERS)) {
                            if (!(alias=alias.trim()).isEmpty()) {
                                if (!alias.equals(metricName)) {
                                    sbAllowedTopics.append(", ");
                                    sbAllowedTopics.append(alias).append(":").append(metricName);
                                }
                            }
                        }
                    }

                    if (monitor.getSensor().isPullSensor()) {
                        if (sensorConfig.get("type") instanceof String type && StringUtils.isNotBlank(type)) {
                            collectorConfigs
                                    .computeIfAbsent(type, key->new LinkedList<>())
                                    .add(monitor.getSensor());
                        }
                    }
                }

                log.trace("AllowedTopicsProcessorPlugin: Task #{}: MONITOR: metric={}, allowed-topics={}",
                        taskCounter, metricName, sbAllowedTopics);

            } catch (Exception e) {
                log.error("AllowedTopicsProcessorPlugin: Task #{}: EXCEPTION while processing monitor. Skipping it: {}\n",
                        taskCounter, monitor, e);
            }
        }

        String allowedTopics = sbAllowedTopics.toString();
        log.debug("AllowedTopicsProcessorPlugin: Task #{}: Allowed-Topics configuration for collectors: \n{}", taskCounter, allowedTopics);

        String collectorConfigsStr = null;
        try {
            if (! collectorConfigs.isEmpty()) {
                log.debug("AllowedTopicsProcessorPlugin: Task #{}: Pull-Sensor collector configurations: \n{}", taskCounter, collectorConfigs);
                ObjectMapper mapper = new ObjectMapper();
                mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                /*mapper.setFilterProvider(new SimpleFilterProvider().addFilter("customerFilter",
                        SimpleBeanPropertyFilter.serializeAllExcept("@objectClass")));*/
                collectorConfigsStr = mapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(collectorConfigs);
            }
        } catch (JsonProcessingException e) {
            log.error("AllowedTopicsProcessorPlugin: Task #{}: EXCEPTION while processing sensor configs. Skipping them.\n",
                    taskCounter, e);
        }
        if (StringUtils.isBlank(collectorConfigsStr))
            collectorConfigsStr = "{ }";
        log.debug("AllowedTopicsProcessorPlugin: Task #{}: Pull-Sensor collector configurations String: \n{}", taskCounter, collectorConfigsStr);

        task.getNodeRegistryEntry().getPreregistration().put(EmsConstant.COLLECTOR_ALLOWED_TOPICS_VAR, allowedTopics);
        task.getNodeRegistryEntry().getPreregistration().put(EmsConstant.COLLECTOR_CONFIGURATIONS_VAR, collectorConfigsStr);

        // Store collector configurations in config service
        try {
            ConfigWriteService.getInstance()
                    .getOrCreateConfigFile(
                            EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FILE,
                            EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FORMAT)
                    .put(EmsConstant.COLLECTOR_CONFIGURATIONS_VAR, collectorConfigsStr);
        } catch (Exception e) {
            log.error("BaguetteServer.startServer(): Failed to store connection info in ems-client-config-map: {}, Exception: ",
                    EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FILE, e);
        }

        log.debug("AllowedTopicsProcessorPlugin: Task #{}: processBeforeInstallation: END", taskCounter);
    }

    @Override
    public void processAfterInstallation(ClientInstallationTask task, long taskCounter, boolean success) {
        log.debug("AllowedTopicsProcessorPlugin: Task #{}: processAfterInstallation: success={}", taskCounter, success);
        log.trace("AllowedTopicsProcessorPlugin: Task #{}: processAfterInstallation: success={}, task={}", taskCounter, success, task);
    }

    @Override
    public void start() {
        log.debug("AllowedTopicsProcessorPlugin: start()");
    }

    @Override
    public void stop() {
        log.debug("AllowedTopicsProcessorPlugin: stop()");
    }
}
