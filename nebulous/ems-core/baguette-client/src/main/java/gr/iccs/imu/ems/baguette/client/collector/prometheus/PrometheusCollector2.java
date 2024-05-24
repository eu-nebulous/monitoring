/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.collector.prometheus;

import gr.iccs.imu.ems.baguette.client.Collector;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.common.collector.AbstractEndpointCollector;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.collector.prometheus.OpenMetricsParser;
import gr.iccs.imu.ems.common.collector.prometheus.PrometheusCollectorProperties;
import gr.iccs.imu.ems.util.EventBus;
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

/**
 * Collects measurements from a Prometheus exporter endpoint
 */
@Slf4j
@Component
public class PrometheusCollector2 extends AbstractEndpointCollector<String> implements Collector {
    public final static String DEFAULT_PROMETHEUS_PORT = "9090";
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
        this.autoStartRunner = false;   // Don't start the default runner
    }

    @Override
    public void afterPropertiesSet() {
        log.debug("Collectors::Prometheus2: properties: {}", properties);
        super.afterPropertiesSet();
    }

    @Override
    public void start() {
        initRestClientAndParser();
        startEventPublishTask();
        applyNewConfigurations();
    }

    @Override
    public void stop() {
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
    public String getName() {
        return "prometheus";
    }

    @Override
    public void setConfiguration(Object config) {
        if (config instanceof List sensorConfigList) {
            configurations = sensorConfigList.stream()
                    .filter(o -> o instanceof Map)
                    .toList();
            applyNewConfigurations();
        }
    }

    public synchronized void activeGroupingChanged(String oldGrouping, String newGrouping) {
        log.info("Collectors::Prometheus2: activeGroupingChanged: New Allowed Topics for active grouping: {} -- !! Not used !!", newGrouping);
    }

    private void applyNewConfigurations() {
        log.debug("Collectors::Prometheus2: applyNewConfigurations: {}", configurations);
        if (configurations==null) return;

        // Cancel previous tasks
        cancelScrapingTasks();

        // Create new scraping tasks
        log.trace("Collectors::Prometheus2: applyNewConfigurations: Starting new scraping tasks: configurations: {}", configurations);
        Instant startInstant = Instant.now();
        configurations.forEach(config -> {
            if (checkConfig(config)) {
                String destination = config.getOrDefault("name", "").toString();
                String prometheusMetric = getPrometheusMetric(config);
                String url = getUrlPattern(config);
                Duration delay = getDelay(config);
                Duration period = getInterval(config);
                Instant startsAt = startInstant.plus(delay);

                scrapingTasks.add(taskScheduler.scheduleAtFixedRate(
                        () -> scrapeEndpoint(url, prometheusMetric, destination), startsAt, period));
                log.info("Collectors::Prometheus2: Added monitoring task: prometheus-metric={}, destination={}, url={}, starts-at={}, period={}",
                        prometheusMetric, destination, url, startsAt, period);
            } else
                log.warn("Collectors::Prometheus2: applyNewConfigurations: Skipped sensor: {}", config);
        });
        log.debug("Collectors::Prometheus2: applyNewConfigurations: Started new scraping tasks: {}", scrapingTasks);
    }

    private void cancelScrapingTasks() {
        if (! scrapingTasks.isEmpty()) {
            log.trace("Collectors::Prometheus2: cancelScrapingTasks: Cancelling previous scraping tasks: {}", scrapingTasks);
            List<ScheduledFuture<?>> list = new ArrayList<>(scrapingTasks);
            scrapingTasks.clear();
            list.forEach(task -> task.cancel(true));
            log.trace("Collectors::Prometheus2: cancelScrapingTasks: Cancelled previous scraping tasks: {}", scrapingTasks);
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

            int port = Integer.parseInt( configMap.getOrDefault("port", "0").toString() );
            if (port<=0) errors.add("No or invalid port provided: "+port);

            String prometheusMetric = configMap.getOrDefault("metric", "").toString();
            if (StringUtils.isBlank(prometheusMetric)) errors.add("No prometheus metric provided");
        } else
            errors.add("No 'configuration' sub-map found in sensor spec: "+config);

        // If no errors found return true
        if (errors.isEmpty()) return true;

        // Print errors and return false
        log.warn("Collectors::Prometheus2: checkConfig: Sensor specification has errors: spec={}, errors={}", config, errors);
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

    private String getUrlPattern(Map<String, Serializable> config) {
        if (config.get("configuration") instanceof Map configMap) {
            int port = Integer.parseInt( configMap.getOrDefault("port", DEFAULT_PROMETHEUS_PORT).toString() );
            String endpoint = configMap.getOrDefault("endpoint", "/").toString();
            return "http://%s:"+port+endpoint;
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

    private void scrapeEndpoint(String urlPattern, String prometheusMetric, String destination) {
        log.debug("Collectors::Prometheus2: scrapeEndpoint: BEGIN: Scraping Prometheus endpoints for sensor: url-pattern={}, prometheusMetric={}, destination={}",
                urlPattern, prometheusMetric, destination);

        // Get nodes/pods to scrape
        Set<Serializable> nodes = collectorContext.getNodesWithoutClient();
        log.trace("Collectors::Prometheus2: scrapeEndpoint: Nodes to scrape: {}", nodes);
        if (nodes==null || nodes.isEmpty()) {
            log.debug("Collectors::Prometheus2: scrapeEndpoint: END: No nodes to scrape: url-pattern={}, prometheusMetric={}, destination={}",
                    urlPattern, prometheusMetric, destination);
            return;
        }

        // Scrape nodes and process responses
        nodes.forEach(node -> {
            String url = urlPattern.formatted(node);
            log.trace("Collectors::Prometheus2: scrapeEndpoint: Scraping node: {} -- Endpoint: {}", node, url);

            // Scrape endpoint
            String payload = restClient
                    .get().uri(url)
                    .retrieve()
                    .body(String.class);
            log.debug("Collectors::Prometheus2: scrapeEndpoint: Scrapped node: {} -- Endpoint: {} -- Payload:\n{}", node, url, payload);

            // Parser response
            List<OpenMetricsParser.MetricInstance> results = null;
            if (StringUtils.isNotBlank(payload)) {
                results = openMetricsParser.processInput(payload.split("\n"));
                log.trace("Collectors::Prometheus2: scrapeEndpoint: Parsed payload: {} -- Metrics:\n{}", node, results);
            }

            // Get values for the requested metric
            if (results!=null) {
                List<OpenMetricsParser.MetricInstance> matches = results.stream()
                        .filter(m -> m.getMetricName().equalsIgnoreCase(prometheusMetric)).toList();
                log.trace("Collectors::Prometheus2: scrapeEndpoint: Found metric: {} -- Metric(s):\n{}", node, matches);
                List<Double> values = matches.stream().map(OpenMetricsParser.MetricInstance::getMetricValue).toList();
                log.trace("Collectors::Prometheus2: scrapeEndpoint: Metric value(s): {} -- Value(s):\n{}", node, values);

                // Publish extracted values
                queueForPublish(prometheusMetric, destination, values, node, url);
            }

            log.trace("Collectors::Prometheus2: scrapeEndpoint: Done scraping node: {} -- Endpoint: {}", node, url);
        });

        log.debug("Collectors::Prometheus2: scrapeEndpoint: END");
    }

    private void queueForPublish(String prometheusMetric, String destination, List<Double> values, Serializable node, String endpoint) {
        log.debug("Collectors::Prometheus2: queueForPublish: metric={}, destination={}, values={}, node={}, endpoint={}",
                node, prometheusMetric, destination, values, endpoint);
        values.forEach(v -> {
            EventMap event = new EventMap(v);
            event.setEventProperty("metric", prometheusMetric);
            event.setEventProperty("source-node", node);
            event.setEventProperty("source-endpoint", endpoint);
            event.setEventProperty("destination-topic", destination);
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
                    log.debug("Collectors::Prometheus2: Event Publishing: Published event: {} -- Result: {}", event, result);
                } catch (InterruptedException e) {
                    log.warn("Collectors::Prometheus2: Event Publishing: Interrupted. Exiting event publish loop");
                    break;
                }
            }
        });
        eventPublishThread.setName("PrometheusCollector2-event-publish-thread");
        eventPublishThread.setDaemon(true);
        eventPublishThread.start();
    }
}
