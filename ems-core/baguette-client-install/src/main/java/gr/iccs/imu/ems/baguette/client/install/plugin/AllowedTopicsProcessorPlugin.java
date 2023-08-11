/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.plugin;

import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.InstallationContextProcessorPlugin;
import gr.iccs.imu.ems.translate.model.Monitor;
import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Installation context processor plugin for generating 'allowed-topics' setting
 * used in baguette-client[.yml/.properties] config. file.
 * It set the 'COLLECTOR_ALLOWED_TOPICS' variable in pre-registration context.
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

                // Get sensor configuration (as a list of KeyValuePair's)
                Map<String,String> sensorConfig = null;
                if (monitor.getSensor().isPullSensor()) {
                    // Pull Sensor
                    sensorConfig = monitor.getSensor().pullSensor().getConfiguration();
                } else {
                    // Push Sensor
                    sensorConfig = monitor.getSensor().pushSensor().getAdditionalProperties();
                }

                // Process Destination aliases, if specified in configuration
                if (sensorConfig!=null) {
                    String k = sensorConfig.keySet().stream()
                            .filter(key -> StrUtil.compareNormalized(key, EmsConstant.COLLECTOR_DESTINATION_ALIASES))
                            .findAny().orElse(null);
                    String aliases = (k!=null) ? sensorConfig.get(k) : null;

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

        task.getNodeRegistryEntry().getPreregistration().put(EmsConstant.COLLECTOR_ALLOWED_TOPICS_VAR, allowedTopics);
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
