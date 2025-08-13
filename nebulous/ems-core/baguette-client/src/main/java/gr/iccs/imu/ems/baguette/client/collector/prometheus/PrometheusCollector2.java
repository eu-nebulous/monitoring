/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.collector.prometheus;

import gr.iccs.imu.ems.baguette.client.IClientCollector;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.common.collector.AbstractEndpointCollector;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.collector.prometheus.IPrometheusCollector;
import gr.iccs.imu.ems.common.collector.prometheus.OpenMetricsParser;
import gr.iccs.imu.ems.common.collector.prometheus.PrometheusCollectorProperties;
import gr.iccs.imu.ems.common.k8s.K8sClient;
import gr.iccs.imu.ems.util.EventBus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * Collects measurements from a Prometheus exporter endpoint
 */
@Slf4j
@Component
public class PrometheusCollector2 extends AbstractEndpointCollector<String> implements IClientCollector, IPrometheusCollector {
    public final static String DEFAULT_PROMETHEUS_PORT = "9090";
    public final static String DEFAULT_PROMETHEUS_PATH = "/metrics";
    public final static String DEFAULT_DELAY = "0";
    public final static String DEFAULT_INTERVAL = "60";
    public final static String DEFAULT_INTERVAL_UNIT = "SECONDS";

    private final PrometheusCollectorProperties properties;
    private List<Map<String, Serializable>> configurations = List.of();
    private final List<ScheduledFuture<?>> scrapingTasks = new LinkedList<>();
    private RestClient restClient;
    private OpenMetricsParser openMetricsParser;
    private final LinkedBlockingQueue<EventMap> eventsQueue = new LinkedBlockingQueue<>();
    private Thread eventPublishThread;
    private boolean keepRunning;

    @SuppressWarnings("unchecked")
    public PrometheusCollector2(PrometheusCollectorProperties properties, CollectorContext collectorContext, TaskScheduler taskScheduler, EventBus<String,Object,Object> eventBus) {
        super("PrometheusCollector2", properties, collectorContext, taskScheduler, eventBus);
        this.properties = properties;
        this.properties.setEnable(true);    //XXX: TODO: Temporary fix... 'enable' property is not set while initializing 'PrometheusCollectorProperties'
        this.autoStartRunner = false;   // Don't start the default runner
    }

    @Override
    public void afterPropertiesSet() {
        log.debug("Collectors::{}: properties: {}", collectorId, properties);
        super.afterPropertiesSet();
    }

    @Override
    public void start() {
        // check if already running
        if (started) {
            log.warn("Collectors::{}: Already started", collectorId);
            return;
        }
        super.start();
        initRestClientAndParser();
        startEventPublishTask();
        applyNewConfigurations();
    }

    @Override
    public void stop() {
        if (!started) {
            log.warn("Collectors::{}: Not started", collectorId);
            return;
        }
        super.stop();
        keepRunning = false;
        if (eventPublishThread!=null && eventPublishThread.isAlive()) {
            eventPublishThread.interrupt();
        }
        cancelScrapingTasks();
        eventsQueue.clear();
    }

    protected ResponseEntity<String> getData(String url) {
        return null;
    }

    protected void processData(String data, String nodeAddress, ProcessingStats stats) {
    }

    @Override
    public void setConfiguration(Object config) {
        if (config instanceof List sensorConfigList) {
            if (!started || configurations==null || configurations.isEmpty() && ! sensorConfigList.isEmpty()) {
                configurations = sensorConfigList.stream()
                        .filter(o -> o instanceof Map)
                        .toList();
                if (started)
                    applyNewConfigurations();
            }
        }
    }

    public synchronized void activeGroupingChanged(String oldGrouping, String newGrouping) {
        log.info("Collectors::{}: activeGroupingChanged: New Allowed Topics for active grouping: {} -- !! Not used !!", collectorId, newGrouping);
    }

    private void applyNewConfigurations() {
        log.debug("Collectors::{}: applyNewConfigurations: {}", collectorId, configurations);
        if (configurations==null) return;

        // Cancel previous tasks
        cancelScrapingTasks();

        // Create new scraping tasks
        log.trace("Collectors::{}: applyNewConfigurations: Starting new scraping tasks: configurations: {}", collectorId, configurations);
        Instant startInstant = Instant.now();
        configurations.forEach(config -> {
            log.trace("Collectors::{}: applyNewConfigurations: configurations.forEach: BEFORE CHECK: config: {}", collectorId, config);
            if (checkConfig(config)) {
                log.trace("Collectors::{}: applyNewConfigurations: configurations.forEach: AFTER CHECK: config: {}", collectorId, config);
                String destination = config.getOrDefault("name", "").toString();
                String prometheusMetric = getPrometheusMetric(config);
                String url = getUrlPattern(config);
                Map<String,Set<String>> allowedTags = getAllowedTags(config);
                Duration delay = getDelay(config);
                Duration period = getInterval(config);
                Instant startsAt = startInstant.plus(delay);

                // Prepare pod filter
                final PodFilter podFilter = getPrometheusPodFilter(config);

                scrapingTasks.add(taskScheduler.scheduleAtFixedRate(
                        () -> scrapeEndpoint(url, prometheusMetric, allowedTags, destination, podFilter), startsAt, period));
                log.info("Collectors::{}: Added monitoring task: prometheus-metric={}, destination={}, url={}, starts-at={}, period={}",
                        collectorId, prometheusMetric, destination, url, startsAt, period);
            } else
                log.warn("Collectors::{}: applyNewConfigurations: Skipped sensor: {}", collectorId, config);
        });
        log.debug("Collectors::{}: applyNewConfigurations: Started new scraping tasks: {}", collectorId, scrapingTasks);
    }

    private void cancelScrapingTasks() {
        if (! scrapingTasks.isEmpty()) {
            log.trace("Collectors::{}: cancelScrapingTasks: Cancelling previous scraping tasks: {}", collectorId, scrapingTasks);
            List<ScheduledFuture<?>> list = new ArrayList<>(scrapingTasks);
            scrapingTasks.clear();
            list.forEach(task -> task.cancel(true));
            log.trace("Collectors::{}: cancelScrapingTasks: Cancelled previous scraping tasks: {}", collectorId, scrapingTasks);
        }
    }

    private boolean checkConfig(Map<String, Serializable> config) {
        List<String> errors = new ArrayList<>();
        String push = config.getOrDefault("push", "").toString();
        if (! "false".equalsIgnoreCase(push)) errors.add(String.format("Not a Pull sensor. Expected '%s' but found '%s'", false, push));

        String destination = config.getOrDefault("name", "").toString();
        if (StringUtils.isBlank(destination)) errors.add("No destination (name) provided");

        if (config.get("configuration") instanceof Map configMap) {
            String type = configMap.getOrDefault("type", "").toString();
            if (! getName().equalsIgnoreCase(type)) errors.add(String.format("Type mismatch. Expected '%s' but found '%s'", getName(), type));

            int port = Integer.parseInt( configMap.getOrDefault("port", DEFAULT_PROMETHEUS_PORT).toString() );
            if (port<=0 || port>=65536) errors.add("Invalid port provided: "+port);

            String prometheusMetric = configMap.getOrDefault("metric", "").toString();
            if (StringUtils.isBlank(prometheusMetric)) errors.add("No prometheus metric provided");
        } else
            errors.add("No 'configuration' sub-map found in sensor spec: "+config);

        // If no errors found return true
        if (errors.isEmpty()) return true;

        // Print errors and return false
        log.warn("Collectors::{}: checkConfig: Sensor specification has errors: spec={}, errors={}", collectorId, config, errors);
        return false;
    }

    private String getPrometheusMetric(Map<String, Serializable> config) {
        if (config.get("configuration") instanceof Map configMap) {
            String prometheusMetric = configMap.getOrDefault("metric", "").toString();
            if (StringUtils.isNotBlank(prometheusMetric))
                return prometheusMetric;
        }
        return null;
    }

    private PodFilter getPrometheusPodFilter(Map<String, Serializable> config) {
        if (config.get("configuration") instanceof Map configMap) {
            String podNamePrefix = configMap.getOrDefault("components", "").toString();
            String podNamespace = configMap.getOrDefault("namespace", "default").toString();
            final PodFilter podFilter = StringUtils.isAllBlank(podNamePrefix, podNamespace)
                    ? null : new PodFilter(podNamePrefix, podNamespace);
            return podFilter;
        }
        return null;
    }

    private String getUrlPattern(Map<String, Serializable> config) {
        if (config.get("configuration") instanceof Map configMap) {
            int port = Integer.parseInt( configMap.getOrDefault("port", DEFAULT_PROMETHEUS_PORT).toString() );
            String path = configMap.getOrDefault("path", "").toString();
            if (StringUtils.isBlank(path))
                path = configMap.getOrDefault("endpoint", DEFAULT_PROMETHEUS_PATH).toString();
            return "http://%s:"+port+path;
        }
        return null;
    }

    // Expected 'allowed-tags' format:
    //   allowed-rags ==> <TAG-1>=<VALUE-1>,...,<VALUE-N>;<TAG-2>=;<TAG-3>
    // Meaning:
    //   <TAG-1>=<VALUE-1>,...,<VALUE-N>    measurements with 'TAG-1' and value is any of 'VALUE-1' to 'VALUE-N' are accepted
    //   <TAG-1>=<VALUE-1>                  measurements with 'TAG-1' and value equals to 'VALUE-1' is accepted
    //   <TAG-2>=                           measurements with 'TAG-2' and empty value '' is accepted (special case of above)
    //   <TAG-3>                            measurements with 'TAG-3' and any value is accepted
    private Map<String, Set<String>> getAllowedTags(Map<String, Serializable> config) {
        if (config.get("configuration") instanceof Map configMap) {
            String tagsStr = configMap.getOrDefault("allowed-tags", "").toString();
            if (StringUtils.isNotBlank(tagsStr)) {
                Map<String, Set<String>> allowedTags = null;
                String[] pairs = tagsStr.split(";");
                for (String pair : pairs) {
                    if (StringUtils.isNotBlank(pair)) {
                        pair = pair.trim();
                        String[] part = pair.split("=", 2);
                        String tag = part[0].trim();
                        String tagValStr = part.length>1 ? part[1].trim() : null;
                        if (StringUtils.isNotBlank(tag)) {
                            String[] vals = tagValStr!=null ? tagValStr.split(",") : null;
                            HashSet<String> valSet = null;
                            if (vals!=null) {
                                for (int i = 0; i < vals.length; i++) vals[i] = vals[i].trim();
                                valSet = new LinkedHashSet<>(Arrays.asList(vals));
                            }
                            if (allowedTags==null)
                                allowedTags = new LinkedHashMap<>();
                            allowedTags.put(tag, valSet);
                        }
                    }
                }
                return allowedTags;
            }
        }
        return null;
    }

    private Duration getDelay(Map<String, Serializable> config) {
        if (config.get("configuration") instanceof Map configMap) {
            long delay = Long.parseLong(configMap.getOrDefault("delay", DEFAULT_DELAY).toString());
            if (delay>=0)
                return Duration.ofSeconds(delay);
        }
        return Duration.ofSeconds(0);
    }

    private Duration getInterval(Map<String, Serializable> config) {
        if (config.get("interval") instanceof Map intervalMap) {
            long period = Long.parseLong(intervalMap.getOrDefault("period", DEFAULT_INTERVAL).toString());
            ChronoUnit unit = ChronoUnit.valueOf(intervalMap.getOrDefault("unit", DEFAULT_INTERVAL_UNIT).toString().toUpperCase());
            if (period>0)
                return Duration.of(period, unit);
        }
        return Duration.ofSeconds(60);
    }

    private void initRestClientAndParser() {
        // Initialize the REST client
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (properties.getConnectTimeout()>=0)
            factory.setConnectTimeout(properties.getConnectTimeout());
        if (properties.getReadTimeout()>=0)
            factory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();

        // Initialize the OpenMetrics parser
        this.openMetricsParser = new OpenMetricsParser();
    }

    private void scrapeEndpoint(String urlPattern, String prometheusMetric, Map<String, Set<String>> allowedTags, String destination, PodFilter podFilter) {
        log.debug("Collectors::{}: scrapeEndpoint: BEGIN: Scraping Prometheus endpoints for sensor: url-pattern={}, prometheusMetric={}, destination={}, podFilter={}",
                collectorId, urlPattern, prometheusMetric, destination, podFilter);

        // Get nodes/pods to scrape
        Set<Serializable> nodes = collectorContext.getNodesWithoutClient();
        log.trace("Collectors::{}: scrapeEndpoint: Nodes to scrape: {}", collectorId, nodes);
        if (nodes==null || nodes.isEmpty()) {
            log.debug("Collectors::{}: scrapeEndpoint: END: No nodes to scrape: url-pattern={}, prometheusMetric={}, destination={}",
                    collectorId, urlPattern, prometheusMetric, destination);
            return;
        }

        // Filter pods to scrape
        Set<Serializable> pods = collectorContext.getPodInfoSet();
        log.trace("Collectors::{}: scrapeEndpoint: podFilter: {}, pods={}", collectorId, podFilter, pods);
        if (podFilter!=null && pods!=null && ! pods.isEmpty()) {
            log.trace("Collectors::{}: scrapeEndpoint: BEFORE POD FILTERING: nodes={}", collectorId, nodes);
            nodes = pods.stream()
                    .peek(o -> log.trace("Collectors::{}: scrapeEndpoint: --------> {} - {}", collectorId, o.getClass(), o))
                    .filter(o -> o instanceof K8sClient.PodEntry)
                    .map(K8sClient.PodEntry.class::cast)
                    .filter(podFilter::matches)
                    .peek(o -> log.trace("Collectors::{}: scrapeEndpoint: --------> MATCHED: {} - {}", collectorId, o.getClass(), o))
                    .map(K8sClient.PodEntry::podIP)
                    .peek(o -> log.trace("Collectors::{}: scrapeEndpoint: --------> IP ADDR: {}", collectorId, o))
                    .collect(Collectors.toSet());
            log.trace("Collectors::{}: scrapeEndpoint: AFTER POD FILTERING: nodes={}", collectorId, nodes);
        }

        // Scrape nodes and process responses
        nodes.forEach(node -> {
            String url = urlPattern.formatted(node);
            log.info("Collectors::{}: scrapeEndpoint: Scraping node: {} -- Endpoint: {}", collectorId, node, url);

            // Scrape endpoint
            try {
                String payload = restClient
                        .get().uri(url)
                        .retrieve()
                        .body(String.class);
                log.debug("Collectors::{}: scrapeEndpoint: Scrapped node: {} -- Endpoint: {} -- Payload:\n{}", collectorId, node, url, payload);

                // Parser response
                List<OpenMetricsParser.MetricInstance> results = null;
                if (StringUtils.isNotBlank(payload)) {
                    results = openMetricsParser.processInput(payload.split("\n"));
                    log.trace("Collectors::{}: scrapeEndpoint: Parsed payload: {} -- Metrics:\n{}", collectorId, node, results);
                }

                // Get values for the requested metric (and tags if provided)
                if (results != null) {
                    List<OpenMetricsParser.MetricInstance> matches = results.stream()
                            .filter(m -> m.getMetricName().equalsIgnoreCase(prometheusMetric))
                            .filter(m -> matchAnyAllowedTag(m.getTags(), allowedTags))
                            .toList();
                    log.trace("Collectors::{}: scrapeEndpoint: Found metric: {} -- Metric(s):\n{}", collectorId, node, matches);

                    // Publish extracted values
                    queueForPublish(prometheusMetric, destination, matches, node, url);
                }

                log.trace("Collectors::{}: scrapeEndpoint: Done scraping node: {} -- Endpoint: {}", collectorId, node, url);

            } catch (Exception e) {
                if (log.isDebugEnabled())
                    log.debug("Collectors::{}: scrapeEndpoint: FAILED scraping node: {} -- Endpoint: {} -- Exception: \n", collectorId, node, url, e);
                else
                    log.warn("Collectors::{}: scrapeEndpoint: FAILED scraping node: {} -- Endpoint: {} -- Exception: {}", collectorId, node, url, e.getMessage());
            }
        });

        log.debug("Collectors::{}: scrapeEndpoint: END", collectorId);
    }

    private boolean matchAnyAllowedTag(Map<String, String> tags, Map<String, Set<String>> allowedTags) {
        log.trace("Collectors::{}: matchAnyAllowedTag: BEGIN: tags={}, allowed-tags={}", collectorId, tags, allowedTags);
        if (allowedTags==null || allowedTags.isEmpty()) return true;
        for (Map.Entry<String,String> e : tags.entrySet()) {
            String k = e.getKey()!=null ? e.getKey().trim() : null;
            if (StringUtils.isBlank(k)) continue;
            if (allowedTags.containsKey(k)) {
                Set<String> allowedTagValues = allowedTags.get(k);
                if (allowedTagValues==null) return true;
                String v = e.getValue()!=null ? e.getValue().trim() : "";
                if (allowedTagValues.contains(v)) return true;
            }
        }
        return false;
    }

    private void queueForPublish(String prometheusMetric, String destination, List<OpenMetricsParser.MetricInstance> metricInstances, Serializable node, String endpoint) {
        log.debug("Collectors::{}: queueForPublish: metric={}, destination={}, metricInstances={}, node={}, endpoint={}",
                collectorId, node, prometheusMetric, destination, metricInstances, endpoint);
        metricInstances.forEach(v -> {
            EventMap event = new EventMap(v.getMetricValue(), 1);
            event.setEventProperty("metric", prometheusMetric);
            event.setEventProperty("source-node", node);
            event.setEventProperty("source-endpoint", endpoint);
            event.setEventProperty("destination-topic", destination);
            if (v.getTags()!=null)
                v.getTags().forEach((tag,tagValue) -> event.setEventProperty("tag-"+tag, tagValue));
            eventsQueue.add(event);
        });
    }

    private void startEventPublishTask() {
        eventPublishThread = new Thread(() -> {
            keepRunning = true;
            while (keepRunning) {
                try {
                    EventMap event = eventsQueue.take();
                    String destination = event.getEventProperty("destination-topic").toString();
                    CollectorContext.PUBLISH_RESULT result = collectorContext
                            .sendEvent(null, destination, event, properties.isCreateTopic());
                    log.debug("Collectors::{}: Event Publishing: Published event: {} -- Result: {}", collectorId, event, result);
                } catch (InterruptedException e) {
                    log.warn("Collectors::{}: Event Publishing: Interrupted. Exiting event publish loop", collectorId);
                    break;
                }
            }
        });
        eventPublishThread.setName(collectorId + "-event-publish-thread");
        eventPublishThread.setDaemon(true);
        eventPublishThread.start();
    }

    @Data
    private static class PodFilter {
        private final String namePrefix;
        private final String namespace;

        public boolean matches(K8sClient.PodEntry p) {
            if (StringUtils.isNotBlank(namePrefix) && ! StringUtils.startsWith(p.podName(), namePrefix))
                return false;
            if (StringUtils.isNotBlank(namePrefix)) {
                String rest = StringUtils.removeStart(p.podName(), namePrefix);
            }
            if (StringUtils.isNotBlank(namespace) && ! StringUtils.equals(p.podNamespace(), namespace))
                return false;
            return true;
        }
    }
}
