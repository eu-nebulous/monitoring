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
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.collector.netdata.NetdataCollectorProperties;
import gr.iccs.imu.ems.util.EventBus;
import gr.iccs.imu.ems.util.GROUPING;
import gr.iccs.imu.ems.util.GroupingConfiguration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects measurements from Netdata http server
 */
@Slf4j
@Component
public class NetdataCollector extends gr.iccs.imu.ems.common.collector.netdata.NetdataCollector implements Collector {
    public NetdataCollector(@NonNull NetdataCollectorProperties properties,
                            @NonNull CollectorContext collectorContext,
                            @NonNull TaskScheduler taskScheduler,
                            @NonNull EventBus<String, Object, Object> eventBus)
    {
        super("NetdataCollector", properties, collectorContext, taskScheduler, eventBus);
        if (!(collectorContext instanceof ClientCollectorContext))
            throw new IllegalArgumentException("Invalid CollectorContext provided. Expected: ClientCollectorContext, but got "+collectorContext.getClass().getName());
    }

    public synchronized void activeGroupingChanged(String oldGrouping, String newGrouping) {
        HashSet<String> topics = new HashSet<>();
        for (String g : GROUPING.getNames()) {
            GroupingConfiguration grp = ((ClientCollectorContext)collectorContext).getGroupings().get(g);
            if (grp!=null)
                topics.addAll(grp.getEventTypeNames());
        }
        log.warn("Collectors::Netdata: activeGroupingChanged: New Allowed Topics for active grouping: {} -- {}", newGrouping, topics);
        List<String> tmpList = new ArrayList<>(topics);
        Map<String,String> tmpMap = null;
        if (properties.getAllowedTopics()!=null) {
            tmpMap = properties.getAllowedTopics().stream()
                    .map(s -> s.split(":", 2))
                    .collect(Collectors.toMap(a -> a[0], a -> a.length>1 ? a[1]: ""));
        }
        log.warn("Collectors::Netdata: activeGroupingChanged: New Allowed Topics -- Topics Map: {} -- {}", tmpList, tmpMap);
        synchronized (this) {
            this.allowedTopics = tmpList;
            this.topicMap = tmpMap;
        }
    }

}
