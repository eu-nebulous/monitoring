/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.collector;

import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.common.client.SshClientProperties;
import gr.iccs.imu.ems.common.misc.EventConstant;
import gr.iccs.imu.ems.common.recovery.RecoveryConstant;
import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.EventBus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * Abstract collector:
 * Collects measurements from http server endpoint
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractEndpointCollector<T> implements InitializingBean, Runnable, EventBus.EventConsumer<String,Object,Object> {
    private final static String EVENT_COLLECTION_START = "EVENT_COLLECTION_START";
    private final static String EVENT_COLLECTION_END = "EVENT_COLLECTION_END";
    private final static String EVENT_COLLECTION_ERROR = "EVENT_COLLECTION_ERROR";
    private final static String EVENT_CONN_OK = "EVENT_CONN_OK";
    private final static String EVENT_CONN_ERROR = "EVENT_CONN_ERROR";
    private final static String EVENT_NODE_OK = "EVENT_NODE_OK";
    private final static String EVENT_NODE_FAILED = "EVENT_NODE_FAILED";

    private final static String BASE_COLLECTION_START = "_COLLECTION_START";
    private final static String BASE_COLLECTION_END = "_COLLECTION_END";
    private final static String BASE_COLLECTION_ERROR = "_COLLECTION_ERROR";
    private final static String BASE_CONN_OK = "_CONN_OK";
    private final static String BASE_CONN_ERROR = "_CONN_ERROR";
    private final static String BASE_NODE_OK = "_NODE_OK";
    private final static String BASE_NODE_FAILED = "_NODE_FAILED";

    protected final String collectorId;
    protected final AbstractEndpointCollectorProperties properties;
    protected final CollectorContext<? extends SshClientProperties> collectorContext;
    protected final TaskScheduler taskScheduler;
    protected final EventBus<String,Object,Object> eventBus;
    protected final Map<Class<? extends AbstractEndpointCollector<T>>, Map<String, String>> nodeToNodeEventsMap = new HashMap<>();

    protected boolean started;
    protected boolean autoStartRunner = true;
    protected ScheduledFuture<?> runner;
    protected Set<String> allowedTopics;
    protected Map<String, Set<String>> topicMap;

    protected Map<String, Integer> errorsMap = new HashMap<>();
    protected Map<String,ScheduledFuture<?>> ignoredNodes = new HashMap<>();

    protected enum COLLECTION_RESULT { IGNORED, OK, ERROR }

    @Override
    public void afterPropertiesSet() {
        log.debug("Collectors::{}: properties: {}", collectorId, properties);
        processAllowedTopics(properties.getAllowedTopics());

        registerInternalEvents(collectorId);
    }

    public void processAllowedTopics(Collection<String> allowedTopicsSpec) {
        Set<String> topicsSet = allowedTopicsSpec == null
                ? null : properties.getAllowedTopics().stream()
                        .map(s -> s.split(":")[0])
                        .collect(Collectors.toSet());
        Map<String, Set<String>> topicsMap = properties.getAllowedTopics() == null
                ? null : properties.getAllowedTopics().stream()
                .map(s -> s.split(":", 2))
                .collect(Collectors.groupingBy(a -> a[0],
                        Collectors.mapping(a -> a.length > 1 ? a[1] : "", Collectors.toSet())));

        log.info("Collectors::{}: New Allowed Topics: {} -- Topics Map: {}", collectorId, topicsSet, topicsMap);
        synchronized (this) {
            this.allowedTopics = topicsSet;
            this.topicMap = topicsMap;
        }
    }

    public synchronized void start() {
        // Check
        if (properties==null || StringUtils.isBlank(properties.getUrlOfNodesWithoutClient())) {
            log.warn("Collectors::{}: Config error: 'UrlOfNodesWithoutClient' not set", collectorId);
            return;
        }

        // check if already running
        if (started) {
            log.warn("Collectors::{}: Already started", collectorId);
            return;
        }

        // check parameters
        if (!properties.isEnable()) {
            log.warn("Collectors::{}: Collector not enabled", collectorId);
            return;
        }
        if (properties.getDelay()<0) properties.setDelay(0);

        log.debug("Collectors::{}: configuration: {}", collectorId, properties);

        // Subscribe for SELF-HEALING plugin GIVE_UP events
        eventBus.subscribe(RecoveryConstant.SELF_HEALING_RECOVERY_COMPLETED, this);
        eventBus.subscribe(RecoveryConstant.SELF_HEALING_RECOVERY_GIVE_UP, this);
        eventBus.subscribe(EventConstant.EVENT_CLIENT_CONFIG_UPDATED, this);

        // Schedule collection execution
        errorsMap.clear();
        ignoredNodes.clear();
        if (autoStartRunner)
            runner = taskScheduler.scheduleWithFixedDelay(this, Duration.ofMillis(properties.getDelay()));
        started = true;

        log.info("Collectors::{}: Started", collectorId);
    }

    public synchronized void stop() {
        if (!started) {
            log.warn("Collectors::{}: Not started", collectorId);
            return;
        }

        // Unsubscribe from SELF-HEALING plugin GIVE_UP events
        eventBus.unsubscribe(EventConstant.EVENT_CLIENT_CONFIG_UPDATED, this);
        eventBus.unsubscribe(RecoveryConstant.SELF_HEALING_RECOVERY_COMPLETED, this);
        eventBus.unsubscribe(RecoveryConstant.SELF_HEALING_RECOVERY_GIVE_UP, this);

        // Cancel collection execution
        started = false;
        if (runner!=null && ! runner.isDone()) {
            runner.cancel(true);
            runner = null;
        }
        ignoredNodes.values().stream().filter(Objects::nonNull).forEach(task -> task.cancel(true));
        log.info("Collectors::{}: Stopped", collectorId);
    }

    @Override
    public void onMessage(String topic, Object message, Object sender) {
        log.trace("Collectors::{}: onMessage: BEGIN: topic={}, message={}, sender={}", collectorId, topic, message, sender);

        String nodeAddress = (message!=null) ? message.toString() : null;
        log.trace("Collectors::{}: nodeAddress={}", collectorId, nodeAddress);

        if (RecoveryConstant.SELF_HEALING_RECOVERY_COMPLETED.equals(topic)) {
            log.info("Collectors::{}: Resuming collection from Node: {}", collectorId, nodeAddress);
            ignoredNodes.remove(nodeAddress);
        } else
        if (RecoveryConstant.SELF_HEALING_RECOVERY_GIVE_UP.equals(topic)) {
            log.warn("Collectors::{}: Giving up collection from Node: {}", collectorId, nodeAddress);
            ignoredNodes.put(nodeAddress, null);
        } else
        if (EventConstant.EVENT_CLIENT_CONFIG_UPDATED.equals(topic)) {
            log.info("Collectors::{}: Client configuration updated. Purging nodes without recovery task from ignore list: Old ignore list nodes: {}", collectorId, ignoredNodes.keySet());
            List<String> nodesToPurge = ignoredNodes.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toList());
            nodesToPurge.forEach(node -> {
                ignoredNodes.remove(node);
                log.info("Collectors::{}: Client configuration updated. Node purged from ignore list: {}", collectorId, node);
            });
        } else
            log.warn("Collectors::{}: onMessage: Event from unexpected topic received. Ignoring it: {}", collectorId, topic);
    }

    public void run() {
        if (!started) return;

        log.trace("Collectors::{}: run(): BEGIN", collectorId);
        if (log.isTraceEnabled()) {
            log.trace("Collectors::{}: run(): errors-map={}", collectorId, errorsMap);
            log.trace("Collectors::{}: run(): ignored-nodes={}", collectorId, ignoredNodes.keySet());
        }

        // collect data from local node
        if (! properties.isSkipLocal()) {
            log.debug("Collectors::{}: Collecting metrics from local node...", collectorId);
            collectAndPublishData("");
        } else {
            log.debug("Collectors::{}: Collection from local node is disabled", collectorId);
        }

        // if Aggregator, collect data from nodes without client
        log.trace("Collectors::{}: Nodes without clients in Zone: {}", collectorId, collectorContext.getNodesWithoutClient());
        log.trace("Collectors::{}: Is Aggregator: {}", collectorId, collectorContext.isAggregator());
        if (collectorContext.isAggregator()) {
            if (collectorContext.getNodesWithoutClient()!=null && ! collectorContext.getNodesWithoutClient().isEmpty()) {
                log.debug("Collectors::{}: Collecting metrics from remote nodes (without EMS client): {}", collectorId,
                        collectorContext.getNodesWithoutClient());
                for (Object nodeAddress : collectorContext.getNodesWithoutClient()) {
                    // collect data from remote node
                    collectAndPublishData(nodeAddress.toString());
                }
            } else
                log.debug("Collectors::{}: No remote nodes (without EMS client)", collectorId);
        }

        log.trace("Collectors::{}: run(): END", collectorId);
    }

    protected void registerInternalEvents(@NonNull String prefix) {
        registerInternalEvents(
                prefix + BASE_COLLECTION_START,
                prefix + BASE_COLLECTION_END,
                prefix + BASE_COLLECTION_ERROR,
                prefix + BASE_CONN_OK,
                prefix + BASE_CONN_ERROR,
                prefix + BASE_NODE_OK,
                prefix + BASE_NODE_FAILED);
    }

    @SuppressWarnings("unchecked")
    protected Class<? extends AbstractEndpointCollector<T>> getCollectorClass() {
        return (Class<? extends AbstractEndpointCollector<T>>) getClass();
    }

    protected void registerInternalEvents(@NonNull String collectionStartEvent,
                                          @NonNull String collectionEndEvent,
                                          @NonNull String collectionErrorEvent,
                                          @NonNull String connectionOkEvent,
                                          @NonNull String connectionErrorEvent,
                                          @NonNull String nodeOkEvent,
                                          @NonNull String nodeFailedEvent) {
        Map<String, String> collectorEvents = new LinkedHashMap<>();
        collectorEvents.put(EVENT_COLLECTION_START, collectionStartEvent);
        collectorEvents.put(EVENT_COLLECTION_END, collectionEndEvent);
        collectorEvents.put(EVENT_COLLECTION_ERROR, collectionErrorEvent);
        collectorEvents.put(EVENT_CONN_OK, connectionOkEvent);
        collectorEvents.put(EVENT_CONN_ERROR, connectionErrorEvent);
        collectorEvents.put(EVENT_NODE_OK, nodeOkEvent);
        collectorEvents.put(EVENT_NODE_FAILED, nodeFailedEvent);
        log.debug("Collectors::{}: registerInternalEvents: BEFORE REGISTRATION: collector-class={}, events={}", collectorId, getClass(), collectorEvents);

        Class<? extends AbstractEndpointCollector<T>> clazz = getCollectorClass();
        nodeToNodeEventsMap.put(clazz, collectorEvents);
        log.debug("Collectors::{}: registerInternalEvents: AFTER REGISTRATION: collector-class={}, events={}", collectorId, clazz, collectorEvents);
    }

    private Map<String, String> getInternalEvents() {
        log.debug("Collectors::{}: getInternalEvents: BEGIN: collector-class={}", collectorId, getClass());
        Class<? extends AbstractEndpointCollector<T>> clazz = getCollectorClass();
        Map<String, String> collectorEvents = nodeToNodeEventsMap.get(clazz);
        log.debug("Collectors::{}: getInternalEvents: END: collector-class={}, events={}", collectorId, clazz, collectorEvents);
        return collectorEvents;
    }

    private COLLECTION_RESULT collectAndPublishData(@NonNull String nodeAddress) {
        if (ignoredNodes.containsKey(nodeAddress)) {
            log.debug("Collectors::{}:   Node is in ignore list: {}", collectorId, nodeAddress);
            return COLLECTION_RESULT.IGNORED;
        }

        Map<String,String> nodeEvents = getInternalEvents();
        try {
            sendToEventBus(nodeEvents.get(EVENT_COLLECTION_START), nodeAddress);
            _collectAndPublishData(nodeAddress);
            sendToEventBus(nodeEvents.get(EVENT_COLLECTION_END), nodeAddress);

            //if (Optional.ofNullable(errorsMap.put(nodeAddress, 0)).orElse(0)>0) sendEvent(ABSTRACT_ENDPOINT_CONN_OK, nodeAddress);
            sendToEventBus(nodeEvents.get(EVENT_CONN_OK), nodeAddress);
            sendToEventBus(nodeEvents.get(EVENT_NODE_OK), nodeAddress);
            errorsMap.put(nodeAddress, 0);
            return COLLECTION_RESULT.OK;
        } catch (Throwable t) {
            int errors = errorsMap.compute(nodeAddress, (k, v) -> Optional.ofNullable(v).orElse(0) + 1);
            int errorLimit = properties.getErrorLimit();
            log.warn("Collectors::{}:     Exception while collecting metrics from node: {}, #errors={}, exception: {}",
                    collectorId, nodeAddress, errors, getExceptionMessages(t));
            log.debug("Collectors::{}: Exception while collecting metrics from node: {}, #errors={}\n", collectorId, nodeAddress, errors, t);

            sendToEventBus(nodeEvents.get(EVENT_COLLECTION_ERROR), nodeAddress, "errors="+errors);
            sendToEventBus(nodeEvents.get(EVENT_CONN_ERROR), nodeAddress, "errors="+errors);

            if (errorLimit<=0 || errors >= errorLimit) {
                log.warn("Collectors::{}: Too many consecutive errors occurred while attempting to collect metrics from node: {}, num-of-errors={}", collectorId, nodeAddress, errors);
                log.warn("Collectors::{}: Pausing collection from Node: {}", collectorId, nodeAddress);
                ignoredNodes.put(nodeAddress, null);
                sendToEventBus(nodeEvents.get(EVENT_NODE_FAILED), nodeAddress);
            }
            return COLLECTION_RESULT.ERROR;
        }
    }

    private String getExceptionMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t!=null) {
            sb.append(" -> ").append(t.getClass().getName()).append(": ").append(t.getMessage());
            t = t.getCause();
        }
        return sb.substring(4);
    }

    private void sendToEventBus(String topic, String nodeAddress, String...extra) {
        Map<String,String> message = new HashMap<>();
        message.put("address", nodeAddress);
        for (String e : extra) {
            String[] s = e.split("[:=]", 2);
            if (s.length==2 && StringUtils.isNotBlank(s[0]))
                message.put(s[0].trim(), s[1]);
        }
        eventBus.send(topic, message, getClass().getName());
    }

    protected abstract ResponseEntity<T> getData(String url);
    protected abstract void processData(T data, String nodeAddress, ProcessingStats stats);

    private void _collectAndPublishData(String nodeAddress) {
        String url;
        if (StringUtils.isBlank(nodeAddress)) {
            // Local node data collection URL
            url = properties.getUrl();
            if (StringUtils.isBlank(url))
                url = String.format(properties.getUrlOfNodesWithoutClient(), "127.0.0.1");
        } else {
            // Remote node data collection URL
            url = String.format(properties.getUrlOfNodesWithoutClient(), nodeAddress);
        }
        log.debug("Collectors::{}:   Collecting data from url: {}", collectorId, url);

        log.debug("Collectors::{}: Collecting data: {}...", collectorId, url);
        long startTm = System.currentTimeMillis();
        ResponseEntity<T> response = getData(url);
        long callEndTm = System.currentTimeMillis();
        log.trace("Collectors::{}: ...response: {}", collectorId, response);

        if (response==null) {
            log.warn("Collectors::{}: Collecting data...No response: {}", collectorId, null);
        } else
        if (response.getStatusCode()==HttpStatus.OK) {
            T data = response.getBody();
            ProcessingStats stats = new ProcessingStats();

            log.trace("Collectors::{}: Processing data started: data: {}", collectorId, data);
            processData(data, nodeAddress, stats);
            log.trace("Collectors::{}: Processing data completed: stats: {}", collectorId, stats);

            long endTm = System.currentTimeMillis();
            log.debug("Collectors::{}: Collecting data...ok", collectorId);
            //log.info("Collectors::{}:     Metrics: extracted={}, published={}, failed={}", collectorId,
            //        stats.countSuccess + stats.countErrors, stats.countSuccess, stats.countErrors);
            if (log.isDebugEnabled())
                log.debug("Collectors::{}:     Publish statistics: {}", collectorId, stats);
            log.debug("Collectors::{}:     Durations: rest-call={}, extract+publish={}, total={}", collectorId,
                    callEndTm-startTm, endTm-callEndTm, endTm-startTm);
        } else {
            log.warn("Collectors::{}: Collecting data...failed: Http Status: {}", collectorId, response.getStatusCode());
        }
    }

    protected List<CollectorContext.PUBLISH_RESULT> publishMetricEvent(String metricName, double metricValue, long timestamp, String nodeAddress) {
        EventMap event = new EventMap(metricValue, 1, timestamp);
        return publishMetricEvent(metricName, event, nodeAddress);
    }

    protected List<CollectorContext.PUBLISH_RESULT> publishMetricEvent(String metricName, EventMap event, String nodeAddress) {
        boolean createTopic = properties.isCreateTopic();
        try {
            boolean createDestination = (createTopic || allowedTopics!=null && allowedTopics.contains(metricName));
            List<CollectorContext.PUBLISH_RESULT> results = new ArrayList<>();
            boolean sendToOriginal = true;
            Set<String> topicsSet;
            if (topicMap!=null && (topicsSet = topicMap.get(metricName))!=null && ! topicsSet.isEmpty()) {
                for (String targetTopic : topicsSet) {
                    if (StringUtils.isNotBlank(targetTopic)) {
                        results.add( sendEvent(targetTopic, metricName, new EventMap(event), nodeAddress, createDestination) );
                        sendToOriginal = false;
                    }
                }
            }
            if (sendToOriginal) {
                results.add(sendEvent(metricName, metricName, event, nodeAddress, createDestination));
            }
            return results;
        } catch (Exception e) {
            log.warn("Collectors::{}:    Publishing metric failed: ", collectorId, e);
            return Collections.singletonList( CollectorContext.PUBLISH_RESULT.ERROR );
        }
    }

    private CollectorContext.PUBLISH_RESULT sendEvent(String metricName, String originalTopic, EventMap event, String nodeAddress, boolean createDestination) {
        event.setEventProperty(EmsConstant.EVENT_PROPERTY_SOURCE_ADDRESS, nodeAddress);
        event.getEventProperties().put(EmsConstant.EVENT_PROPERTY_EFFECTIVE_DESTINATION, metricName);
        event.getEventProperties().put(EmsConstant.EVENT_PROPERTY_ORIGINAL_DESTINATION, originalTopic);
        log.debug("Collectors::{}:    Publishing metric: {}: {}", collectorId, metricName, event.getMetricValue());
        CollectorContext.PUBLISH_RESULT result = collectorContext.sendEvent(null, metricName, event, createDestination);
        log.trace("Collectors::{}:    Publishing metric: {}: {} -> result: {}", collectorId, metricName, event.getMetricValue(), result);
        return result;
    }

    protected void updateStats(List<CollectorContext.PUBLISH_RESULT> publishResults, ProcessingStats stats) {
        publishResults.forEach(r -> updateStats(r, stats));
    }

    protected void updateStats(CollectorContext.PUBLISH_RESULT publishResult, ProcessingStats stats) {
        if (publishResult==CollectorContext.PUBLISH_RESULT.SENT) stats.countSuccess++;
        else if (publishResult==CollectorContext.PUBLISH_RESULT.SKIPPED) stats.countSkipped++;
        else if (publishResult==CollectorContext.PUBLISH_RESULT.ERROR) stats.countErrors++;
    }

    protected static class ProcessingStats {
        public int countSuccess;
        public int countErrors;
        public int countSkipped;

        public int getCountTotal() {
            return countSuccess+countSkipped+countErrors;
        }

        public String toString() {
            return "extracted: %d, published: %d, skipped: %d, failed: %d"
                    .formatted(getCountTotal(), countSuccess, countSkipped, countErrors);
        }
    }
}
