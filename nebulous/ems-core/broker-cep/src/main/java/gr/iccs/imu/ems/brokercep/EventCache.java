/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep;

import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventCache implements InitializingBean {
    public final static int DEFAULT_EVENT_CACHE_SIZE = 100;

    private final BrokerCepProperties properties;
    private final AtomicLong cacheCounter = new AtomicLong(0);
    private ArrayBlockingQueue<CacheEntry> messageCache;
    private boolean enabled;
    @Getter @Setter
    private Set<String> excludeDestinations = new HashSet<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        enabled = properties==null  || properties.isEventCacheEnabled();
        if (properties!=null && properties.getEventCacheSize()==0) enabled = false;
        if (!enabled) return;

        int s = properties!=null ? properties.getEventCacheSize() : -1;
        if (s<0) s = DEFAULT_EVENT_CACHE_SIZE;
        messageCache = new ArrayBlockingQueue<>(s);
    }

    public List<CacheEntry> asList() {
        return enabled ? new ArrayList<>(messageCache) : Collections.emptyList();
    }

    public synchronized void clearCache() {
        clearCache(false);
    }

    public synchronized void clearCache(boolean resetCounter) {
        if (!enabled) return;
        messageCache.clear();
        if (resetCounter) cacheCounter.set(0);
    }

    public void excludeDestination(String destination) {
        if (StringUtils.isBlank(destination)) return;
        excludeDestinations.add(destination.trim());
    }

    public void includeDestination(String destination) {
        if (StringUtils.isBlank(destination)) return;
        excludeDestinations.remove(destination.trim());
    }

    public void cacheEvent(@NonNull EventMap eventMap, String destination) {
        cacheEvent(eventMap, /*eventMap.getEventProperties()*/ null, destination);
    }

    public void cacheEvent(@NonNull Object event, Map<String,Object> properties, String destination) {
        if (!enabled) return;
        if (excludeDestinations.contains(destination)) return;
        CacheEntry entry;
        synchronized (cacheCounter) {
            try {
                while (messageCache.remainingCapacity() == 0)
                    messageCache.poll();
                entry = new CacheEntry(
                        destination,
                        cacheCounter.getAndIncrement(),
                        System.currentTimeMillis());
                if (!messageCache.offer(entry)) {
                    log.warn("EventCache.cacheEvent: Failed to cache event. Cache is full: size={}", messageCache.size());
                    return;
                }
            } catch (Throwable e) {
                log.warn("EventCache.cacheEvent: Exception while caching event: ", e);
                return;
            }
        }
        entry.payload = event;
        entry.properties = properties!=null
                ? properties.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, p -> p.getValue()!=null ? p.getValue().toString() : ""
                  ))
                : Collections.emptyMap();
    }

    @ToString
    @RequiredArgsConstructor
    public static class CacheEntry implements Serializable {
        public Object payload;
        public Map<String, String> properties;
        public final String destination;
        public final long counter;
        public final long timestamp;
    }
}