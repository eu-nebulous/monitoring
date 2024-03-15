/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.collector.netdata;

import gr.iccs.imu.ems.baguette.client.Collector;
import gr.iccs.imu.ems.baguette.client.collector.ClientCollectorContext;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.collector.netdata.NetdataCollectorProperties;
import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects measurements from Netdata agents in a Kubernetes cluster
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class K8sNetdataCollector implements Collector, InitializingBean {
    protected final static Set<String> SENSOR_CONFIG_KEYS_EXCLUDED = Set.of("endpoint", "type", "_containerName");
    protected final static String NETDATA_DATA_API_V1_PATH = "/api/v1/data";
    protected final static String NETDATA_DATA_API_V2_PATH = "/api/v2/data";
    protected final static String DEFAULT_NETDATA_DATA_API_PATH = NETDATA_DATA_API_V2_PATH;

    private final NetdataCollectorProperties properties;
    private final CollectorContext collectorContext;
    private final TaskScheduler taskScheduler;
    private final EventBus<String, Object, Object> eventBus;
    private final RestClient restClient = RestClient.create();
    private final List<ScheduledFuture<?>> scheduledFuturesList = new LinkedList<>();
    private boolean started;
    private List<Map<String, Object>> configuration;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!(collectorContext instanceof ClientCollectorContext))
            throw new IllegalArgumentException("Invalid CollectorContext provided. Expected: ClientCollectorContext, but got "+collectorContext.getClass().getName());
    }

    @Override
    public String getName() {
        return "netdata";
    }

    @Override
    public void setConfiguration(Object config) {
        if (config instanceof List sensorConfigList) {
            configuration = sensorConfigList.stream()
                    .filter(o -> o instanceof Map)
                    .filter(map -> ((Map)map).keySet().stream().allMatch(k->k instanceof String))
                    .toList();
            log.debug("K8sNetdataCollector: setConfiguration: {}", configuration);

            // If configuration changes while collector running we need to restart it
            if (started) {
                log.debug("K8sNetdataCollector: setConfiguration: Restarting collector");
                stop();
                start();
                log.info("K8sNetdataCollector: setConfiguration: Restarted collector");
            }
        } else
            log.warn("K8sNetdataCollector: setConfiguration: Ignoring unsupported configuration object: {}", config);
    }

    @Override
    public void start() {
        if (started) return;
        if (configuration!=null)
            doStart();
        started = true;
        log.debug("K8sNetdataCollector: Started");
    }

    @Override
    public void stop() {
        if (!started) return;
        started = false;
        doStop();
        log.debug("K8sNetdataCollector: Stopped");
    }

    private synchronized void doStart() {
        log.debug("K8sNetdataCollector: doStart(): BEGIN: configuration={}", configuration);
        log.trace("K8sNetdataCollector: doStart(): BEGIN: scheduledFuturesList={}", scheduledFuturesList);

        // Get Netdata agent address and port from env. vars
        String netdataAddress = null;
        if (StringUtils.isBlank(netdataAddress)) netdataAddress = System.getenv("NETDATA_ADDRESS");
        if (StringUtils.isBlank(netdataAddress)) netdataAddress = System.getenv("NETDATA_IP");
        if (StringUtils.isBlank(netdataAddress)) netdataAddress = System.getenv("HOST_IP");
        if (StringUtils.isBlank(netdataAddress)) netdataAddress = "127.0.0.1";
        log.trace("K8sNetdataCollector: doStart(): netdataAddress={}", netdataAddress);

        int netdataPort = Integer.parseInt(
                StringUtils.defaultIfBlank(System.getenv("NETDATA_PORT"), "19999").trim());
        final String baseUrl = String.format("http://%s:%d", netdataAddress.trim(), netdataPort);
        log.trace("K8sNetdataCollector: doStart(): baseUrl={}", baseUrl);

        // Process each sensor configuration
        AtomicInteger sensorNum = new AtomicInteger(0);
        configuration.forEach(map -> {
            log.debug("K8sNetdataCollector: doStart(): Sensor-{}: map={}", sensorNum.incrementAndGet(), map);

            // Check if it is a Pull sensor. (Push sensors are ignored)
            if ("true".equalsIgnoreCase( get(map, "pushSensor", "false") )) {
                log.debug("K8sNetdataCollector: doStart(): Sensor-{}: It is a Push sensor. Ignoring this sensor", sensorNum.get());
                return;
            }
            // else it is a Pull sensor

            // Get destination (topic) and component name
            String destinationName = get(map, "name", null);
            log.trace("K8sNetdataCollector: doStart(): Sensor-{}: destination={}", sensorNum.get(), destinationName);
            if (StringUtils.isBlank(destinationName)) {
                log.warn("K8sNetdataCollector: doStart(): Sensor-{}: No destination found in sensor config: {}", sensorNum.get(), map);
                return;
            }

            // Get metric URL
            int apiVer;
            String url = null;
            String component = null;
            String context = null;
            String dimensions = "*";
            if (map.get("configuration") instanceof Map cfgMap) {
                log.trace("K8sNetdataCollector: doStart(): Sensor-{}: cfgMap={}", sensorNum.get(), cfgMap);

                // Get component name
                component = get(cfgMap, "_containerName", null);
                log.trace("K8sNetdataCollector: doStart(): Sensor-{}: component={}", sensorNum.get(), component);

                // Process 'configuration' map entries, to build metric URL
                Map<String, Object> sensorConfig = (Map<String, Object>) cfgMap;
                String endpoint = get(sensorConfig, "endpoint", DEFAULT_NETDATA_DATA_API_PATH);
                log.trace("K8sNetdataCollector: doStart(): Sensor-{}: endpoint={}", sensorNum.get(), endpoint);

                if (NETDATA_DATA_API_V1_PATH.equalsIgnoreCase(endpoint)) {
                    apiVer = 1;

                    // If expanded by a shorthand expression
                    context = get(sensorConfig, EmsConstant.NETDATA_METRIC_KEY, null);
                    if (StringUtils.isNotBlank(context))
                        addEntryIfMissingOrBlank(sensorConfig, "context", context);

                    // Else check sensor config for 'context' key
                    context = get(sensorConfig, "context", null);

                    addEntryIfMissingOrBlank(sensorConfig, "dimension", "*");
                    addEntryIfMissingOrBlank(sensorConfig, "after", "-1");
                    addEntryIfMissingOrBlank(sensorConfig, "group", "average");
                    addEntryIfMissingOrBlank(sensorConfig, "format", "json2");
                } else
                if (NETDATA_DATA_API_V2_PATH.equalsIgnoreCase(endpoint)) {
                    apiVer = 2;

                    // If expanded by a shorthand expression
                    context = get(sensorConfig, EmsConstant.NETDATA_METRIC_KEY, null);
                    if (StringUtils.isNotBlank(context))
                        addEntryIfMissingOrBlank(sensorConfig, "scope_contexts", context);

                    // Else check sensor config for 'scope_contexts' or 'context' key
                    context = get(sensorConfig, "scope_contexts", null);
                    if (StringUtils.isBlank(context))
                        context = get(sensorConfig, "context", null);

                    boolean isK8s = StringUtils.startsWithIgnoreCase(context, "k8s");
                    if (isK8s) {
                        addEntryIfMissingOrBlank(sensorConfig, "group_by", "label");
                        addEntryIfMissingOrBlank(sensorConfig, "group_by_label", "k8s_pod_name");
                    }
                    addEntryIfMissingOrBlank(sensorConfig, "dimension", "*");
                    addEntryIfMissingOrBlank(sensorConfig, "after", "-1");
                    addEntryIfMissingOrBlank(sensorConfig, "time_group", "average");
                    addEntryIfMissingOrBlank(sensorConfig, "format", "ssv");
                } else {
                    log.warn("K8sNetdataCollector: doStart(): Sensor-{}: Invalid Netdata endpoint found in sensor config: {}", sensorNum.get(), map);
                    return;
                }
                dimensions = get(sensorConfig, "dimension", dimensions);

                StringBuilder sb = new StringBuilder(endpoint);
                final AtomicBoolean first = new AtomicBoolean(true);
                sensorConfig.forEach((key, value) -> {
                    if (StringUtils.isNotBlank(key) && ! SENSOR_CONFIG_KEYS_EXCLUDED.contains(key)) {
                        if (value instanceof String valueStr) {
                            sb.append(first.get() ? "?" : "&").append(key).append("=").append(valueStr);
                            first.set(false);
                        }
                    }
                });

                if (StringUtils.isNotBlank(context)) {
                    url = baseUrl + sb;
                } else {
                    log.warn("K8sNetdataCollector: doStart(): Sensor-{}: No 'context' found in sensor configuration: {}", sensorNum.get(), map);
                    return;
                }
            } else {
                log.warn("K8sNetdataCollector: doStart(): Sensor-{}: No sensor configuration found is spec: {}", sensorNum.get(), map);
                return;
            }
            log.trace("K8sNetdataCollector: doStart(): Sensor-{}: Metric url={}", sensorNum.get(), url);

            // Get interval and configure scheduler
            long period = 60;
            TimeUnit unit = TimeUnit.SECONDS;
            Duration duration = null;
            if (map.get("interval") instanceof Map intervalMap) {
                log.trace("K8sNetdataCollector: doStart(): Sensor-{}: intervalMap={}", sensorNum.get(), intervalMap);
                period = Long.parseLong(get(intervalMap, "period", Long.toString(period)));
                if (period>0) {
                    String unitStr = get(intervalMap, "unit", unit.name());
                    unit = StringUtils.isNotBlank(unitStr)
                            ? TimeUnit.valueOf(unitStr.toUpperCase().trim()) : TimeUnit.SECONDS;
                    duration = Duration.of(period, unit.toChronoUnit());
                }
            }
            log.trace("K8sNetdataCollector: doStart(): Sensor-{}: duration-from-spec={}", sensorNum.get(), duration);
            if (duration==null) {
                duration = Duration.of(period, unit.toChronoUnit());
            }
            log.trace("K8sNetdataCollector: doStart(): Sensor-{}: duration={}", sensorNum.get(), duration);

            final int apiVer1 = apiVer;
            final String url1 = url;
            final String component1 = component;
            scheduledFuturesList.add( taskScheduler.scheduleAtFixedRate(() -> {
                collectData(apiVer1, url1, destinationName, component1);
            }, duration) );
            log.debug("K8sNetdataCollector: doStart(): Sensor-{}: destination={}, component={}, interval={}, url={}",
                    sensorNum.get(), destinationName, component, duration, url);
            log.info("K8sNetdataCollector: Collecting Netdata metric '{}.{}' into '{}', every {} {}",
                    context, dimensions, destinationName, period, unit.name().toLowerCase());
        });
        log.trace("K8sNetdataCollector: doStart():  scheduledFuturesList={}", scheduledFuturesList);
        log.debug("K8sNetdataCollector: doStart():  END");
    }

    private String get(Map<String,Object> map, String key, String defaultValue) {
        Object valObj;
        if (!map.containsKey(key) || (valObj = map.get(key))==null) return defaultValue;
        String value = valObj.toString();
        if (StringUtils.isBlank(value)) return defaultValue;
        return value;
    }

    private void addEntryIfMissingOrBlank(Map<String, Object> map, String key, Object value) {
        if (map.containsKey(key) && map.get(key)!=null)
            if (map.get(key) instanceof String s && StringUtils.isNotBlank(s))
                return;
        map.put(key, value);
    }

    private void collectData(int apiVer, String url, String destination, String component) {
        long startTm = System.currentTimeMillis();
        log.debug("K8sNetdataCollector: collectData(): BEGIN: apiVer={}, url={}, destination={}, component={}",
                apiVer, url, destination, component);

        Map<String,Double> resultsMap = new HashMap<>();
        long timestamp = -1L;
        if (apiVer==1) {
            Map response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            // Need to call for each pod separately
            // or get the total
            timestamp = Long.parseLong( response.get("before").toString() );
            List<String> chart_ids = (List) response.get("chart_ids");
            List<String> dimension_ids = (List) response.get("dimension_ids");
            List<String> latest_values = (List) response.get("view_latest_value");
            for (int i=0, n=chart_ids.size(); i<n; i++) {
                String id = chart_ids.get(i) + "|" + dimension_ids.get(i);
                try {
//                    double v = values.get(i).doubleValue();
                    double v = Double.parseDouble(latest_values.get(i));
                    resultsMap.put(id, v);
                } catch (Exception e) {
                    log.warn("K8sNetdataCollector: collectData(): ERROR at index #{}: id={}, value={}, Exception: ",
                            i, id, latest_values.get(i), e);
                    resultsMap.put(id, 0.0);
                }
            }
        } else
        if (apiVer==2) {
            log.warn("K8sNetdataCollector: collectData(): Calling Netdata: apiVer={}, url={}", apiVer, url);
            Map response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            log.trace("K8sNetdataCollector: collectData(): apiVer={}, response={}", apiVer, response);

            double result = Double.parseDouble( response.get("result").toString() );
            Map view = (Map) response.get("view");
            long after = Long.parseLong( view.get("after").toString() );
            long before = Long.parseLong( view.get("before").toString() );
            timestamp = before;
            log.trace("K8sNetdataCollector: collectData(): result={}, after={}, before={}", result, after, before);
            Map dimensions = (Map) view.get("dimensions");
            List<String> ids = (List<String>) dimensions.get("ids");
            List<String> units = (List<String>) dimensions.get("units");
            List<Number> values = (List<Number>) ((Map)dimensions.get("sts")).get("avg");
            log.trace("K8sNetdataCollector: collectData():    ids={}", ids);
            log.trace("K8sNetdataCollector: collectData():  units={}", units);
            log.trace("K8sNetdataCollector: collectData(): values={}", values);
            for (int i=0, n=ids.size(); i<n; i++) {
                try {
                    double v = values.get(i).doubleValue();
                    resultsMap.put(ids.get(i), v);
                } catch (Exception e) {
                    log.warn("K8sNetdataCollector: collectData(): ERROR at index #{}: id={}, value={}, Exception: ",
                            i, ids.get(i), values.get(i), e);
                    resultsMap.put(ids.get(i), 0.0);
                }
            }
            //resultsMap.put("result", result);
        }

        log.warn("K8sNetdataCollector: collectData(): Data collected: timestamp={}, results={}", timestamp, resultsMap);

        // Publish collected data to destination
        final long timestamp1 = timestamp;
        Map<String, CollectorContext.PUBLISH_RESULT> publishResults = new LinkedHashMap<>();
        resultsMap.forEach((k,v) -> {
            publishResults.put( k+"="+v, publishMetricEvent(destination, k, v, timestamp1, null) );
        });
        log.debug("K8sNetdataCollector: collectData(): Events published: results={}", publishResults);

        long endTm = System.currentTimeMillis();
        log.warn("K8sNetdataCollector: collectData(): END: duration={}ms", endTm-startTm);
    }

    private synchronized void doStop() {
        log.debug("K8sNetdataCollector: doStop():  BEGIN");
        log.trace("K8sNetdataCollector: doStop():  BEGIN: scheduledFuturesList={}", scheduledFuturesList);
        // Cancel all task scheduler futures
        scheduledFuturesList.forEach(future -> future.cancel(true));
        scheduledFuturesList.clear();
        log.debug("K8sNetdataCollector: doStop():  END");
    }

    public synchronized void activeGroupingChanged(String oldGrouping, String newGrouping) {
        log.debug("K8sNetdataCollector: activeGroupingChanged: {} --> {}", oldGrouping, newGrouping);
    }

    protected CollectorContext.PUBLISH_RESULT publishMetricEvent(String metricName, String key, double metricValue, long timestamp, String nodeAddress) {
        EventMap event = new EventMap(metricValue, 1, timestamp);
        return sendEvent(metricName, metricName, key, event, null, true);
    }

    private CollectorContext.PUBLISH_RESULT sendEvent(String metricName, String originalTopic, String key, EventMap event, String nodeAddress, boolean createDestination) {
        event.setEventProperty(EmsConstant.EVENT_PROPERTY_SOURCE_ADDRESS, nodeAddress);
        event.getEventProperties().put(EmsConstant.EVENT_PROPERTY_EFFECTIVE_DESTINATION, metricName);
        event.getEventProperties().put(EmsConstant.EVENT_PROPERTY_ORIGINAL_DESTINATION, originalTopic);
        event.getEventProperties().put(EmsConstant.EVENT_PROPERTY_KEY, key);
        log.debug("K8sNetdataCollector:    Publishing metric: {}: {}", metricName, event.getMetricValue());
        CollectorContext.PUBLISH_RESULT result = collectorContext.sendEvent(null, metricName, event, createDestination);
        log.trace("K8sNetdataCollector:    Publishing metric: {}: {} -> result: {}", metricName, event.getMetricValue(), result);
        return result;
    }
}
