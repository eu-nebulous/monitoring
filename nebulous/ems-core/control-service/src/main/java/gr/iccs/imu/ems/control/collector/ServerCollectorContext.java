/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.collector;

import gr.iccs.imu.ems.baguette.server.NodeRegistry;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.util.ClientConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerCollectorContext implements CollectorContext {
    private final NodeRegistry nodeRegistry;
    private final BrokerCepService brokerCepService;
    private final ApplicationContext applicationContext;

    @Override
    public List<ClientConfiguration> getNodeConfigurations() {
        return null;
    }

    @Override
    public Set<Serializable> getNodesWithoutClient() {
        if (nodeRegistry==null || nodeRegistry.getCoordinator()==null) return null;
        return nodeRegistry.getCoordinator().supportsAggregators()
                ? Collections.emptySet()
                : nodeRegistry.getNodes().stream()
                    .filter(entry -> entry.getState()== NodeRegistryEntry.STATE.NOT_INSTALLED)
                    .map(NodeRegistryEntry::getIpAddress)
                    .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Set<Serializable> getPodInfoSet() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAggregator() {
        return true;
    }

    @Override
    @SneakyThrows
    public PUBLISH_RESULT sendEvent(String connectionString, String destinationName, EventMap event, boolean createDestination) {
        assert(connectionString==null);
        if (createDestination || brokerCepService.destinationExists(destinationName)) {
            brokerCepService.publishEvent(null, destinationName, event);
            return PUBLISH_RESULT.SENT;
        }
        return PUBLISH_RESULT.SKIPPED;
    }

    public Collection<IServerCollector> getCollectors() {
        return applicationContext.getBeansOfType(IServerCollector.class, true, true).values();
    }

    public IServerCollector getCollectorByName(String name) {
        if (StringUtils.isBlank(name))
            throw new IllegalArgumentException("getCollectorByName: Argument cannot be blank");
        List<IServerCollector> list = getCollectors().stream()
                .filter(col -> col.getName().equals(name)).toList();
        if (list.size()>1)
            throw new IllegalArgumentException("getCollectorByName: Multiple collectors match name: "+name);
        return list.size()==1 ? list.getFirst() : null;
    }
}
