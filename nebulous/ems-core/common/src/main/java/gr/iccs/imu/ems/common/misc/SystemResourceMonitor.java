/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.misc;

import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemResourceMonitor implements Runnable, InitializingBean {
    @Getter @Setter
    private boolean enabled = Boolean.parseBoolean(
            System.getenv().getOrDefault("EMS_SYSMON_ENABLED", "true"));
    @Getter @Setter
    private long period = Math.max(1000L, Long.parseLong(
            System.getenv().getOrDefault("EMS_SYSMON_PERIOD", "30000")));
    @Getter @Setter
    private String commandStr = System.getenv().getOrDefault("EMS_SYSMON_COMMAND", "./bin/sysmon.sh");
    @Getter @Setter
    private String systemResourceMetricsTopic = System.getenv("EMS_SYSMON_TOPIC");
    @Getter @Setter
    private boolean publishAsMetrics = Boolean.parseBoolean(
            Objects.requireNonNullElse(System.getenv("EMS_SYSMON_PUBLISH_AS_METRICS"), "false") );

    private final BrokerCepService brokerCepService;
    private final TaskScheduler scheduler;
    private ScheduledFuture<?> future;
    @Getter
    private Map<String, Object> latestMeasurements;

    private final Map<String,String> topicsCache = new HashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!enabled) log.warn("SystemResourceMonitor is disabled");
        else start();
    }

    public void start() {
        if (!enabled) return;
        if (future!=null) {
            log.warn("SystemResourceMonitor is already running");
            return;
        }
        future = scheduler.scheduleAtFixedRate(this, Duration.ofMillis(period));
        log.info("SystemResourceMonitor started");
    }

    public void stop() {
        if (!enabled) return;
        if (future==null || future.isCancelled()) {
            log.warn("SystemResourceMonitor is already stopped");
            return;
        }
        future.cancel(true);
        future = null;
        topicsCache.clear();
        log.info("SystemResourceMonitor stopped");
    }

    public boolean runImmediatelyBlocking(long timeoutMillis) {
        if (!enabled) return false;
        try {
            ScheduledFuture<?> f = scheduler.schedule(this, Instant.now());
            if (timeoutMillis < 0)
                f.get();
            else
                f.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return f.isDone() && !f.isCancelled();
        } catch (ExecutionException | InterruptedException | TimeoutException | CancellationException e) {
            log.warn("SystemResourceMonitor: EXCEPTION: ", e);
            return false;
        }
    }

    public void run() {
        if (!enabled) return;
        StringBuilder result = new StringBuilder();
        try {
            if (StringUtils.isBlank(commandStr)) {
                log.debug("SystemResourceMonitor: Nothing to do. System metrics command is blank: {}", commandStr);
                return;
            }
            log.debug("SystemResourceMonitor: Getting system metrics with command: {}", commandStr);
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(commandStr);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                result.append(inputLine).append("\n");
            }
            in.close();
            log.debug("SystemResourceMonitor: Script output:\n{}", result);

            updateLatestEvent(result.toString());
            if (publishAsMetrics)
                processOutputAsMetrics();
            else
                processOutput();

        } catch (IOException e) {
            log.warn("SystemResourceMonitor: EXCEPTION: ", e);
        }
    }

    private void updateLatestEvent(String result) {
        log.debug("SystemResourceMonitor: updateLatestEvent: BEGIN:\n{}", result);
        EventMap event = new EventMap();
        for (String line : result.split("\n")) {
            String[] part = line.split(":", 2);
            String metricName = part[0].trim().toLowerCase();
            double metricValue= Double.parseDouble(part[1].trim());
            event.put(metricName, metricValue);
        }
        this.latestMeasurements = Collections.unmodifiableMap(event);
        log.debug("SystemResourceMonitor: updateLatestEvent: Metrics: {}", event);
    }

    @SneakyThrows
    private void processOutput() {
        log.debug("SystemResourceMonitor: processOutput: BEGIN:\n{}", latestMeasurements);
        if (StringUtils.isBlank(systemResourceMetricsTopic)) {
            log.debug("SystemResourceMonitor: processOutput: END: No metrics topic has been set. Will not publish metrics event");
            return;
        }

        log.debug("SystemResourceMonitor: processOutput: Metrics: {}", latestMeasurements);

        log.trace("SystemResourceMonitor: processOutput: Will publish metrics event to topic: {}", systemResourceMetricsTopic);
        brokerCepService.publishEvent(null, systemResourceMetricsTopic, latestMeasurements);
        log.debug("SystemResourceMonitor: processOutput: END: Metrics event published to topic: {}", systemResourceMetricsTopic);
    }

    @SneakyThrows
    private void processOutputAsMetrics() {
        log.debug("SystemResourceMonitor: processOutputAsMetrics: BEGIN: {}", latestMeasurements);
        if (StringUtils.isBlank(systemResourceMetricsTopic)) {
            log.debug("SystemResourceMonitor: processOutputAsMetrics: END: No metrics topic has been set. Will not publish metrics event");
            return;
        }

        log.debug("SystemResourceMonitor: processOutputAsMetrics: Metrics: {}", latestMeasurements);

        latestMeasurements.forEach((metricName, metricValue) -> {
            String topic = topicsCache.computeIfAbsent(metricName, s -> systemResourceMetricsTopic + s.trim().toUpperCase());
            try {
                if (StringUtils.isBlank(metricName) || metricValue == null || StringUtils.isBlank(metricValue.toString()))
                    return;
                log.trace("SystemResourceMonitor: processOutputAsMetrics: Will publish {} metric event to topic: {}", metricName, topic);
                brokerCepService.publishEvent(null, topic, new EventMap(Double.parseDouble(metricValue.toString())));
                log.trace("SystemResourceMonitor: processOutputAsMetrics: {} metric event published to topic: {}", metricName, topic);
            } catch (Exception e) {
                log.warn("SystemResourceMonitor: processOutputAsMetrics: EXCEPTION while publishing {} metric event to topic: {}", metricName, topic);
            }
        });

        log.debug("SystemResourceMonitor: processOutputAsMetrics: END: Latest Metrics: {}", latestMeasurements);
    }
}
