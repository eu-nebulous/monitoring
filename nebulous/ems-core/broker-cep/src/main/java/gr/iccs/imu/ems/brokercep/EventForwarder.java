/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep;

import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import gr.iccs.imu.ems.util.GroupingConfiguration;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventForwarder implements InitializingBean, Runnable {
    @Getter @Setter
    private static EventForwarder instance;

    private final BrokerCepProperties properties;
    private final BrokerCepService brokerCepService;
    private final LinkedBlockingDeque<EventForwardTask> eventForwardingQueue = new LinkedBlockingDeque<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        if (instance==null) instance = this;
        Executors.newFixedThreadPool(1).submit(this);
        log.info("EventForwarder: Starting event publish/forward worker");
    }

    public void addEventForwardTask(@NonNull BrokerCepStatementSubscriber sender, @NonNull GroupingConfiguration.BrokerConnectionConfig brokerConnectionConfig, @NonNull String topic, @NonNull Map<String,Object> eventMap, Runnable success, Runnable failure) {
        boolean isLocalPublish =
                brokerCepService.getBrokerCepProperties().getBrokerUrlForConsumer()
                        .equals(brokerConnectionConfig.getUrl());
        eventForwardingQueue.add(new EventForwardTask(sender, isLocalPublish, brokerConnectionConfig, topic, eventMap, success, failure));
        log.debug("EventForwarder: {} task in the queue", eventForwardingQueue.size());
    }

    public void addEventForwardTask(@NonNull BrokerCepStatementSubscriber sender, String grouping,  String brokerUrl, String certificate, String username, String password, @NonNull String topic, @NonNull Map<String,Object> eventMap, Runnable success, Runnable failure) {
        GroupingConfiguration.BrokerConnectionConfig brokerConnectionConfig =
                new GroupingConfiguration.BrokerConnectionConfig(grouping, brokerUrl, certificate, username, password);
        addEventForwardTask(sender, brokerConnectionConfig, topic, eventMap, success, failure);
    }

    public void addLocalPublishTask(@NonNull BrokerCepStatementSubscriber sender, @NonNull String topic, @NonNull Map<String,Object> eventMap, Runnable success, Runnable failure) {
        String brokerUrl = brokerCepService.getBrokerCepProperties().getBrokerUrlForConsumer();
        String username = brokerCepService.getBrokerUsername();
        String password = brokerCepService.getBrokerPassword();
        GroupingConfiguration.BrokerConnectionConfig brokerConnectionConfig =
                new GroupingConfiguration.BrokerConnectionConfig(null, brokerUrl, null, username, password);
        eventForwardingQueue.add(new EventForwardTask(sender, true, brokerConnectionConfig, topic, eventMap, success, failure));
        log.debug("EventForwarder: {} task in the queue", eventForwardingQueue.size());
    }

    @Override
    public void run() {
        long delay = properties.getEventForwarderLoopDelay();
        if (delay<0L) delay = 0L;

        while (true) {
            try {
                processEventForwardTask(eventForwardingQueue.take());
                waitFor(delay);
            } catch (Throwable t) {
                log.warn("EventForwarder: Exception thrown in task processing loop: ", t);
            }
        }
    }

    private void waitFor(long delayInMillis) {
        if (delayInMillis<=0) return;
        try {
            Thread.sleep(delayInMillis);
        } catch (InterruptedException e) {
            log.warn("EventForwarder: waitFor: Interrupted: ", e);
        }
    }

    private void processEventForwardTask(EventForwardTask task) {
        String senderName = task.getSender().getName();
        String topic = task.getTopic();
        Map<String, Object> eventMap = task.getEventMap();

        // Check if max task processing duration has been exceeded
        long duration = System.currentTimeMillis() - task.getCreation();
        if (properties.getMaxEventForwardDuration()>0 && duration > properties.getMaxEventForwardDuration()) {
            log.error("- Max event publish/forward duration exceeded. Dropping event: subscriber={}, forward-to-groupings={}, topic={}, payload={}",
                    senderName, task.getBrokerConnectionConfig(), topic, eventMap);

            runIfNotNull(task.getFailure());
            return;
        }

        // Process event publish/forward task
        try {
            String brokerUrl = task.getBrokerConnectionConfig().getUrl();
            String username = task.getBrokerConnectionConfig().getUsername();
            String password = task.getBrokerConnectionConfig().getPassword();

            if (task.isLocalPublish()) {
                // Log start of event send to the local broker
                log.trace("- Publishing event to local broker: subscriber={}, local-broker={}, username={}, password={}, topic={}, retry={}, payload={}",
                        senderName, brokerUrl, username, "passwordEncoded", topic, task.getRetries(), eventMap);
            } else {
                log.trace("- Checking forward broker configuration before event send: subscriber={}, local-broker={}, username={}, password={}, topic={}, retry={}, payload={}",
                        senderName, brokerUrl, username, "passwordEncoded", topic, task.getRetries(), eventMap);
                String targetGrouping = task.getBrokerConnectionConfig().getGrouping();
                log.trace("-   Target grouping: {}", targetGrouping);

                // Check if sender forwards have been cleared (indicating that this node became an aggregator)
                boolean configChanged = false;
                boolean forwardsExist = task.getSender().getForwardToGroupings() != null && task.getSender().getForwardToGroupings().size() > 0;
                log.trace("-   Forwards exist: {}", forwardsExist);

                if (forwardsExist) {
                    // Get forward broker configuration from the sender
                    GroupingConfiguration.BrokerConnectionConfig bcc =
                            task.getSender().getForwardToGroupings().stream()
                                    .filter(f -> f.getGrouping().equals(targetGrouping))
                                    .findAny().orElse(null);
                    log.trace("-   Selected BrokerConnectionConfig: {}", bcc);

                    // Log any changes in forward broker config
                    String brokerUrl2 = bcc!=null ? bcc.getUrl() : null;
                    String username2 = bcc!=null ? bcc.getUsername() : null;
                    String password2 = bcc!=null ? bcc.getPassword() : null;

                    if (!StringUtils.equals(brokerUrl, brokerUrl2)) {
                        log.warn("-   Forward broker config changed: sender: {}, broker-url: {} -> {}, event: {}", senderName, brokerUrl, brokerUrl2, task.getEventMap());
                        brokerUrl = brokerUrl2;
                        configChanged = true;
                    }
                    if (!StringUtils.equals(username, username2)) {
                        log.warn("-   Forward broker config changed: sender: {}, username: {} -> {}, event: {}", senderName, username, username2, task.getEventMap());
                        username = username2;
                        configChanged = true;
                    }
                    if (!StringUtils.equals(password, password2)) {
                        log.warn("-   Forward broker config changed: sender: {}, password: ******** -> ********, event: {}", senderName, task.getEventMap());
                        password = password2;
                        configChanged = true;
                    }
                } else {
                    log.warn("-   Forwards removed for topic and grouping. Using local broker: topic={}, grouping={}, sender={}, event={}", task.getTopic(), targetGrouping, senderName, task.getEventMap());

                    brokerUrl = brokerCepService.getBrokerCepProperties().getBrokerUrlForConsumer();
                    username = brokerCepService.getBrokerUsername();
                    password = brokerCepService.getBrokerPassword();
                    configChanged = true;
                }

                // Log start of event send to forward broker
                if (configChanged)
                    log.debug("- Forwarding event to grouping: CONFIG-CHANGED: subscriber={}, forward-to-grouping={}, url={}, username={}, topic={}, retry={}, payload={}",
                            senderName, task.getBrokerConnectionConfig(), brokerUrl, username, topic, task.getRetries(), eventMap);
                else
                    log.debug("- Forwarding event to grouping: subscriber={}, forward-to-grouping={}, url={}, username={}, topic={}, retry={}, payload={}",
                            senderName, task.getBrokerConnectionConfig(), brokerUrl, username, topic, task.getRetries(), eventMap);
            }

            // Update retry info and try sending event
            task.newRetry();
            brokerCepService.publishEvent(brokerUrl, username, password, topic, eventMap);
            task.completed();

            // Log successful event send
            if (task.isLocalPublish()) {
                log.debug("- Event published to local broker: subscriber={}, local-broker={}, username={}, topic={}, payload={}, duration={}ms",
                        senderName, brokerUrl, username, topic, eventMap, task.getTotalDuration());
            } else {
                log.debug("- Event forwarded to grouping: subscriber={}, forwarded-to-grouping={}, url={}, username={}, topic={}, payload={}, duration={}ms",
                        senderName, task.brokerConnectionConfig, brokerUrl, username, topic, eventMap, task.getTotalDuration());
            }

            // Run successful event send callback
            runIfNotNull(task.getSuccess());

        } catch (IllegalArgumentException ex) {
            // Event with errors
            log.error("- Event contains errors. Will not retry to send it: Error while sending event: subscriber={}, forward-to-groupings={}, topic={}, retry={}, duration={}ms, payload={}, exception: ",
                    senderName, task.getBrokerConnectionConfig(), topic, task.getRetries() - 1, task.getTotalDuration(), eventMap, ex);

            runIfNotNull(task.getFailure());

        } catch (Exception ex) {
            // Increase retry count and log failed event send
            task.increaseRetries();
            log.error("- Error while sending event: subscriber={}, forward-to-groupings={}, topic={}, retry={}, duration={}ms, payload={}, exception: ",
                    senderName, task.getBrokerConnectionConfig(), topic, task.getRetries()-1, task.getTotalDuration(), eventMap, ex);

            // Check if retries exceeded limits. If not then put event back in the queue.
            if (properties.getMaxEventForwardRetries()>=0 && task.getRetries() > properties.getMaxEventForwardRetries()) {
                log.error("- Max event publish/forward retries exceeded. Dropping event: subscriber={}, forward-to-groupings={}, topic={}, payload={}",
                        senderName, task.getBrokerConnectionConfig(), topic, eventMap);

                runIfNotNull(task.getFailure());

            } else
            if (properties.getMaxEventForwardDuration()>0 && task.getTotalDuration() > properties.getMaxEventForwardDuration()) {
                log.error("- Max event publish/forward duration exceeded. Dropping event: subscriber={}, forward-to-groupings={}, topic={}, payload={}",
                        senderName, task.getBrokerConnectionConfig(), topic, eventMap);

                runIfNotNull(task.getFailure());

            } else {
                // Retry limits not exceeded. Put event back in the queue
                eventForwardingQueue.add(task);
                log.debug("- Event placed back in queue: subscriber={}, forward-to-groupings={}, topic={}, payload={}",
                        senderName, task.getBrokerConnectionConfig(), topic, eventMap);
            }
        }
    }

    protected void runIfNotNull(Runnable r) {
        if (r==null) return;
        r.run();
    }

    @Getter
    @RequiredArgsConstructor
    @ToString
    protected static class EventForwardTask {
        @NonNull private final BrokerCepStatementSubscriber sender;
        private final boolean localPublish;
        @NonNull private final GroupingConfiguration.BrokerConnectionConfig brokerConnectionConfig;
        @NonNull private final String topic;
        @NonNull private final Map<String,Object> eventMap;
        private final Runnable success;
        private final Runnable failure;
        private final long creation = System.currentTimeMillis();

        private long lastRetryStart;
        private long lastRetryEnd;
        private boolean completed;
        private int retries = 0;

        public void newRetry() {
            if (completed) return;
            lastRetryStart = System.currentTimeMillis();
        }

        public void completed() {
            if (completed) return;
            completed = true;
            lastRetryEnd = System.currentTimeMillis();
        }

        public void increaseRetries() {
            if (completed) return;
            lastRetryEnd = System.currentTimeMillis();
            ++retries;
        }

        public long getLastRetryDuration() {
            return lastRetryEnd - lastRetryStart;
        }

        public long getTotalDuration() {
            return lastRetryEnd - creation;
        }
    }
}
