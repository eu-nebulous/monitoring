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
import gr.iccs.imu.ems.translate.model.Interval;
import gr.iccs.imu.ems.translate.model.Monitor;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Installation context processor plugin for generating Netdata configuration for collecting metrics from prometheus exporters
 */
@Slf4j
@Data
@Service
public class PrometheusProcessorPlugin implements InstallationContextProcessorPlugin {
    public final static String SENSOR_TYPE_KEY = "pull.sensor.type";
    public final static String SENSOR_TYPE_VALUE = "prometheus";
    public final static String NETDATA_PROMETHEUS_JOB_NAME = "pull.prometheus.job.name";
    public final static String NETDATA_PROMETHEUS_ENDPOINT = "pull.prometheus.endpoint";
    public final static String NETDATA_PROMETHEUS_AUTODETECTION = "pull.prometheus.autodetection";
    public final static String NETDATA_PROMETHEUS_PRIORITY = "pull.prometheus.priority";
    public final static String NETDATA_PROMETHEUS_CONFIGURATION_VAR = "NETDATA_PROMETHEUS_CONF";
    public final static long DEFAULT_PRIORITY = 70000;

    @Override
    public void processBeforeInstallation(ClientInstallationTask task, long taskCounter) {
        log.debug("PrometheusProcessorPlugin: Task #{}: processBeforeInstallation: BEGIN", taskCounter);
        log.trace("PrometheusProcessorPlugin: Task #{}: processBeforeInstallation: BEGIN: task={}", taskCounter, task);

        StringBuilder prometheusConf = new StringBuilder("# Generated on: ").append(new Date()).append("\n\n");
        int headerLength = prometheusConf.length();

        long minCollectionInterval = Long.MAX_VALUE;
        long minAutodetectionInterval = Long.MAX_VALUE;
        long minPriority = DEFAULT_PRIORITY;
        boolean found = false;

        prometheusConf.append("\njobs:\n");
        for (Monitor monitor : task.getTranslationContext().getMON()) {
            try {
                log.trace("PrometheusProcessorPlugin: Task #{}: Processing monitor: {}", taskCounter, monitor);
                String componentName = monitor.getComponent();
                String metricName = monitor.getMetric();

                log.trace("PrometheusProcessorPlugin: Task #{}: MONITOR: component={}, metric={}", taskCounter, componentName, metricName);
                if (monitor.getSensor().isPullSensor()) {
                    if (monitor.getSensor().pullSensor().getConfiguration()!=null) {
                        Map<String, String> config = StrUtil.deepFlattenMap(
                                monitor.getSensor().getConfiguration());
                        log.trace("PrometheusProcessorPlugin: Task #{}: MONITOR with PULL SENSOR: config: {}", taskCounter, config);

                        // Get Prometheus related settings
                        String sensorType = StrUtil.getWithNormalized(config, SENSOR_TYPE_KEY, SENSOR_TYPE_VALUE);
                        String prometheusJobName = StrUtil.getWithNormalized(config, NETDATA_PROMETHEUS_JOB_NAME);
                        String prometheusEndpoint = StrUtil.getWithNormalized(config, NETDATA_PROMETHEUS_ENDPOINT);
                        log.trace("PrometheusProcessorPlugin: Task #{}: Prometheus Job settings: type={}, name={}, endpoint={}",
                                taskCounter, sensorType, prometheusJobName, prometheusEndpoint);
                        if (SENSOR_TYPE_VALUE.equals(sensorType)) {
                            if (StringUtils.isNotBlank(prometheusJobName) && StringUtils.isNotBlank(prometheusEndpoint)) {
                                prometheusConf.append("  - name: '").append(prometheusJobName).append("'\n");
                                prometheusConf.append("    url: '").append(prometheusEndpoint).append("'\n");
                                log.trace("PrometheusProcessorPlugin: Task #{}: Extracted Prometheus config: metricName={}, endpoint={}",
                                        taskCounter, prometheusJobName, prometheusEndpoint);
                                found = true;

                                // Get monitor interval
                                Interval interval = monitor.getSensor().pullSensor().getInterval();
                                if (interval != null) {
                                    int period = interval.getPeriod();
                                    TimeUnit unit = TimeUnit.SECONDS;
                                    if (interval.getUnit() != null) {
                                        unit = TimeUnit.valueOf( interval.getUnit().name() );
                                    }
                                    long periodInSeconds = TimeUnit.SECONDS.convert(period, unit);
                                    if (periodInSeconds > 0)
                                        minCollectionInterval = Math.min(minCollectionInterval, periodInSeconds);
                                }

                                // Get autodetection interval
                                String autodetectionStr = StrUtil.getWithNormalized(config, NETDATA_PROMETHEUS_AUTODETECTION);
                                int autodetectionInSeconds = StrUtil.strToInt(autodetectionStr, 0, i -> i >= 0, false, null);
                                if (autodetectionInSeconds > 0)
                                    minAutodetectionInterval = Math.min(minAutodetectionInterval, autodetectionInSeconds);

                                // Get priority
                                String priorityStr = StrUtil.getWithNormalized(config, NETDATA_PROMETHEUS_PRIORITY);
                                int priority = StrUtil.strToInt(priorityStr, (int)DEFAULT_PRIORITY, i -> i >= 0, false, null);
                                if (priority >= 0)
                                    minPriority = Math.min(minPriority, priority);
                            }
                        } else {
                            log.debug("PrometheusProcessorPlugin: Task #{}: Sensor type is not Prometheus: {}", taskCounter, sensorType);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("PrometheusProcessorPlugin: Task #{}: EXCEPTION while processing monitor. Skipping it: {}\n", taskCounter, monitor, e);
            }
        }
        log.debug("PrometheusProcessorPlugin: Task #{}: Netdata Prometheus configuration: \n{}", taskCounter, prometheusConf);
        log.debug("PrometheusProcessorPlugin: Task #{}: Netdata Prometheus: found={}, collection-interval={}, autodetection={}, priority={}",
                taskCounter, found, minCollectionInterval, minAutodetectionInterval, minPriority);

        if (!found) {
            task.getNodeRegistryEntry().getPreregistration().put(NETDATA_PROMETHEUS_CONFIGURATION_VAR, "");
            log.debug("PrometheusProcessorPlugin: Task #{}: processBeforeInstallation: END: no prometheus.conf update", taskCounter);
        } else
        {
            if (minCollectionInterval < Long.MAX_VALUE)
                prometheusConf.insert(headerLength, "update_every: " + minCollectionInterval + "\n");
            if (minAutodetectionInterval < Long.MAX_VALUE)
                prometheusConf.insert(headerLength, "autodetection_retry: " + minAutodetectionInterval + "\n");
            if (minPriority != DEFAULT_PRIORITY)
                prometheusConf.insert(headerLength, "priority: " + minPriority + "\n");

            task.getNodeRegistryEntry().getPreregistration().put(NETDATA_PROMETHEUS_CONFIGURATION_VAR, prometheusConf.toString());
            log.debug("PrometheusProcessorPlugin: Task #{}: processBeforeInstallation: END", taskCounter);
        }
    }

    @Override
    public void processAfterInstallation(ClientInstallationTask task, long taskCounter, boolean success) {
        log.debug("PrometheusProcessorPlugin: Task #{}: processAfterInstallation: success={}", taskCounter, success);
        log.trace("PrometheusProcessorPlugin: Task #{}: processAfterInstallation: success={}, task={}", taskCounter, success, task);
    }

    @Override
    public void start() {
        log.debug("PrometheusProcessorPlugin: start()");
    }

    @Override
    public void stop() {
        log.debug("PrometheusProcessorPlugin: stop()");
    }
}
