/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.plugin.BeaconPlugin;
import gr.iccs.imu.ems.control.properties.TopicBeaconProperties;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class TopicBeacon implements InitializingBean {
    @Getter
    private final TopicBeaconProperties properties;

    private final ControlServiceCoordinator coordinator;
    private final BrokerCepService brokerCepService;
    private final TaskScheduler scheduler;
    private final BeaconContext beaconContext = new BeaconContext(this);
    private final List<BeaconPlugin> beaconPlugins;

    private Gson gson;
    private String previousModelId = "";
    private final AtomicLong modelVersion = new AtomicLong(0);

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!properties.isEnabled()) {
            log.warn("Topic Beacon is disabled");
            return;
        }

        // initialize a Gson instance
        gson = new GsonBuilder().disableHtmlEscaping().create();

        // configure and start scheduler
        Date startTime = new Date(System.currentTimeMillis() + properties.getInitialDelay());
        log.debug("Topic Beacon settings: init-delay={}, delay={}, heartbeat-topics={}, threshold-topics={}, instance-topics={}",
                properties.getInitialDelay(), properties.getDelay(), properties.getHeartbeatTopics(), properties.getThresholdTopics(),
                properties.getInstanceTopics());

        Runnable transmitInfoTask = () -> {
            try {
                transmitInfo();
            } catch (Exception e) {
                log.error("Topic Beacon: Exception while sending info: ", e);
            }
        };
        if (properties.isUseDelay()) {
            scheduler.scheduleWithFixedDelay(transmitInfoTask, startTime.toInstant(), Duration.ofMillis(properties.getDelay()));
            log.info("Topic Beacon started: init-delay={}ms, delay={}ms", properties.getInitialDelay(), properties.getDelay());
        } else {
            scheduler.scheduleAtFixedRate(transmitInfoTask, startTime.toInstant(), Duration.ofMillis(properties.getRate()));
            log.info("Topic Beacon started: init-delay={}ms, rate={}ms", properties.getInitialDelay(), properties.getRate());
        }
    }

    private <T>Set<T> emptyIfNull(Set<T> s) {
        if (s==null) return Collections.emptySet();
        return s;
    }

    public long getModelVersion() {
        return modelVersion.get();
    }

    public String toJson(Object o) {
        return gson.toJson(o);
    }

    public void transmitInfo() throws JMSException {
        log.debug("Topic Beacon: Start transmitting info: {}", new Date());
        updateModelVersion();

        // Call standard transmit methods
        transmitHeartbeat();
        transmitThresholdInfo();
        transmitInstanceInfo();

        // Call Beacon plugins
        beaconPlugins.stream().filter(Objects::nonNull).forEach(plugin -> {
            try {
                log.debug("Topic Beacon: Calling Beacon plugin: {}", plugin.getClass().getName());
                plugin.transmit(beaconContext);
            } catch (Throwable t) {
                log.error("Topic Beacon: EXCEPTION in Beacon plugin: {}\n", plugin.getClass().getName(), t);
            }
        });

        log.debug("Topic Beacon: Completed transmitting info: {}", new Date());
    }

    public void transmitHeartbeat() throws JMSException {
        if (emptyIfNull(properties.getHeartbeatTopics()).isEmpty()) return;

        String message = "TOPIC BEACON HEARTBEAT "+new Date();
        log.debug("Topic Beacon: Transmitting Heartbeat info: message={}, topics={}", message, properties.getHeartbeatTopics());
        sendMessageToTopics(message, properties.getHeartbeatTopics());
    }

    public void transmitThresholdInfo() {
        if (emptyIfNull(properties.getThresholdTopics()).isEmpty()) return;

        if (coordinator.getTranslationContextOfAppModel(coordinator.getCurrentAppModelId())==null)
            return;
        coordinator.getTranslationContextOfAppModel(coordinator.getCurrentAppModelId())
                .getMetricConstraints()
                .forEach(c -> {
                    String message = gson.toJson(c);
                    log.debug("Topic Beacon: Transmitting Metric Constraint threshold info: message={}, topics={}",message, properties.getThresholdTopics());
                    try {
                        sendEventToTopics(message, properties.getThresholdTopics());
                    } catch (JMSException e) {
                        log.error("Topic Beacon: EXCEPTION while transmitting Metric Constraint threshold info: message={}, topics={}, exception: ",
                                message, properties.getThresholdTopics(), e);
                    }
                });
    }

    public void transmitInstanceInfo() throws JMSException {
        if (emptyIfNull(properties.getInstanceTopics()).isEmpty()) return;

        if (coordinator.getBaguetteServer().isServerRunning()) {
            log.debug("Topic Beacon: Transmitting Instance info: topics={}", properties.getInstanceTopics());
            List<NodeRegistryEntry> nodesList = new ArrayList<>(coordinator.getBaguetteServer().getNodeRegistry().getNodes());
            log.debug("Topic Beacon: Transmitting Instance info: nodes={}", nodesList);
            for (NodeRegistryEntry node : nodesList) {
                String nodeName = node.getPreregistration().getOrDefault("name", "");
                String nodeIp = node.getIpAddress();
                //String nodeIp = node.getPreregistration().getOrDefault("ip","");
                String message = gson.toJson(node);
                log.debug("Topic Beacon: Transmitting Instance info for: instance={}, ip-address={}, message={}, topics={}",
                        nodeName, nodeIp, message, properties.getInstanceTopics());
                sendEventToTopics(message, properties.getInstanceTopics());
            }
        }
    }

    // ------------------------------------------------------------------------

    private void sendEventToTopics(String message, Set<String> topics) throws JMSException {
        EventMap event = new EventMap(-1);
        event.put("message", message);
        sendMessageToTopics(event, topics);
    }

    private void sendMessageToTopics(Serializable event, Set<String> topics) throws JMSException {
        for (String topicName : topics) {
            log.trace("Topic Beacon: Sending event to topic: event={}, topic={}", event, topicName);
            brokerCepService.publishSerializable(
                    brokerCepService.getBrokerCepProperties().getBrokerUrlForClients(),
                    brokerCepService.getBrokerUsername(),
                    brokerCepService.getBrokerPassword(),
                    topicName,
                    event,
                    false);
            log.debug("Topic Beacon: Event sent to topic: event={}, topic={}", event, topicName);
        }
    }

    private synchronized boolean updateModelVersion() {
        String modelId = coordinator.getCurrentAppModelId();
        boolean versionChanged = ! StringUtils.defaultIfBlank(modelId, "").equals(previousModelId);
        log.trace("Topic Beacon: updateModelVersion: previousModelId='{}', modelId='{}', version={}, version-changed={}",
                previousModelId, modelId, modelVersion.get(), versionChanged);
        if (versionChanged) {
            long newVersion = modelVersion.incrementAndGet();
            log.info("Topic Beacon: updateModelVersion: Model changed: {} -> {}, version: {}", previousModelId, modelId, newVersion);
            previousModelId = modelId;
        }
        return versionChanged;
    }

    @RequiredArgsConstructor
    public static class BeaconContext {
        @Getter
        private final TopicBeacon topicBeacon;

        public TopicBeaconProperties getProperties() {
            return topicBeacon.properties;
        }

        public String getCurrentAppModelId() {
            return topicBeacon.coordinator.getCurrentAppModelId();
        }

        public TranslationContext getTranslationContextOfAppModel(String modelId) {
            return topicBeacon.coordinator.getTranslationContextOfAppModel(modelId);
        }

        public long getModelVersion() {
            return topicBeacon.modelVersion.get();
        }

        public String toJson(Object payload) {
            return topicBeacon.toJson(payload);
        }

        public void sendEventToTopics(String event, Set<String> topics) throws JMSException {
            topicBeacon.sendEventToTopics(event, topics);
        }

        public void sendMessageToTopics(Serializable event, Set<String> topics) throws JMSException {
            topicBeacon.sendMessageToTopics(event, topics);
        }
    }
}
