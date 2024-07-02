/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.collector.netdata;

import gr.iccs.imu.ems.common.collector.AbstractEndpointCollector;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.util.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects measurements from Netdata http server
 */
@Slf4j
public class NetdataCollector extends AbstractEndpointCollector<HashMap> implements INetdataCollector {
    public final static String NETDATA_COLLECTION_START = "NETDATA_COLLECTION_START";
    public final static String NETDATA_COLLECTION_OK = "NETDATA_COLLECTION_OK";
    public final static String NETDATA_COLLECTION_ERROR = "NETDATA_COLLECTION_ERROR";
    public final static String NETDATA_CONN_OK = "NETDATA_CONN_OK";
    public final static String NETDATA_CONN_ERROR = "NETDATA_CONN_ERROR";
    public final static String NETDATA_NODE_OK = "NETDATA_NODE_OK";
    public final static String NETDATA_NODE_FAILED = "NETDATA_NODE_FAILED";

    protected NetdataCollectorProperties properties;
    protected RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    public NetdataCollector(String id, NetdataCollectorProperties properties, CollectorContext collectorContext, TaskScheduler taskScheduler, EventBus<String,Object,Object> eventBus) {
        super(id, properties, collectorContext, taskScheduler, eventBus);
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        log.debug("Collectors::Netdata: properties: {}", properties);
        super.afterPropertiesSet();

        if (StringUtils.isBlank(properties.getUrl())) {
            String url = "http://127.0.0.1:19999/api/v1/allmetrics?format=json";
            log.debug("Collectors::Netdata: URL not specified. Assuming {}", url);
            properties.setUrl(url);
        }

        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();

        registerInternalEvents(NETDATA_COLLECTION_START, NETDATA_COLLECTION_OK, NETDATA_COLLECTION_ERROR,
                NETDATA_CONN_OK, NETDATA_CONN_ERROR, NETDATA_NODE_OK, NETDATA_NODE_FAILED);
    }

    protected ResponseEntity<HashMap> getData(String url) {
        return restTemplate.getForEntity(url, HashMap.class);
    }

    protected void processData(HashMap data, String nodeAddress, ProcessingStats stats) {
        Map dataMap = data;
        for (Object key : dataMap.keySet()) {
            log.trace("Collectors::Netdata: ...Loop-1: key={}", key);
            if (key==null) continue;
            Map keyData = (Map)dataMap.get(key);
            log.trace("Collectors::Netdata: ...Loop-1: key-data={}", keyData);
            long timestamp = Long.parseLong( keyData.get("last_updated").toString() );
            Map dimensionsMap = (Map)keyData.get("dimensions");

            log.trace("Collectors::Netdata: ...Loop-1: ...dimensions-keys: {}", dimensionsMap.keySet());
            for (Object dimKey : dimensionsMap.keySet()) {
                log.trace("Collectors::Netdata: ...Loop-1: ...dimensions-key: {}", dimKey);
                if (dimKey==null) continue;
                String metricName = ("netdata."+ key + "."+ dimKey).replace(".", "__");
                log.trace("Collectors::Netdata: ...Loop-1: ...metric-name: {}", metricName);
                Map dimData = (Map)dimensionsMap.get(dimKey);
                Object valObj = dimData.get("value");
                log.trace("Collectors::Netdata: ...Loop-1: ...metric-value: {}", valObj);
                if (valObj!=null) {
                    double metricValue = Double.parseDouble(valObj.toString());
                    log.trace("Collectors::Netdata:           {} = {}", metricName, metricValue);

                    updateStats(publishMetricEvent(metricName, metricValue, timestamp, nodeAddress), stats);
                }
            }

            if (Thread.currentThread().isInterrupted()) break;
        }
    }
}
