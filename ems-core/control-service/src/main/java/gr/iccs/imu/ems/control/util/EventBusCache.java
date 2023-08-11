/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util;

import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.properties.InfoServiceProperties;
import gr.iccs.imu.ems.util.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventBusCache implements InitializingBean, EventBus.EventConsumer<String, Object, Object> {
    public final static int DEFAULT_EVENT_BUS_CACHE_SIZE = 100;
    public final static List<String> DEFAULT_TOPICS = Arrays.asList(
            ControlServiceCoordinator.COORDINATOR_STATUS_TOPIC
    );

    private final EventBus<String,Object,Object> eventBus;
    private final InfoServiceProperties properties;
    private final AtomicLong cacheCounter = new AtomicLong(0);
    private ArrayBlockingQueue<EventBusCache.CacheEntry> messageCache;
    private boolean enabled;

    @Override
    public void afterPropertiesSet() throws Exception {
        enabled = properties.isEventBusCacheEnabled() && properties.getEventBusCacheSize()!=0;
        if (!enabled) return;

        int s = properties.getEventBusCacheSize();
        if (s<0) s = DEFAULT_EVENT_BUS_CACHE_SIZE;
        messageCache = new ArrayBlockingQueue<>(s);

        DEFAULT_TOPICS.forEach(topic -> eventBus.subscribe(topic, this));
    }

    public List<EventBusCache.CacheEntry> asList() {
        return enabled ? new ArrayList<>(messageCache) : Collections.emptyList();
    }

    public synchronized void clearCache() {
        clearCache(false);
    }

    public synchronized void clearCache(boolean resetCounter) {
        if (!enabled) return;
        messageCache.clear();
        cacheCounter.set(0);
    }

    public void cacheEvent(String topic, Map<String,Object> message, Object sender) {
        if (!enabled) return;
        EventBusCache.CacheEntry entry;
        synchronized (cacheCounter) {
            try {
                while (messageCache.remainingCapacity() == 0)
                    messageCache.poll();
                entry = new EventBusCache.CacheEntry(
                        topic, message, Map.of("sender", sender),
                        cacheCounter.getAndIncrement(),
                        System.currentTimeMillis());
                if (!messageCache.offer(entry)) {
                    log.warn("EventBusCache.cacheEvent: Failed to cache event. Cache is full: size={}", messageCache.size());
                }
            } catch (Throwable e) {
                log.warn("EventBusCache.cacheEvent: Exception while caching event: ", e);
            }
        }
    }

    @Override
    public void onMessage(String topic, Object message, Object sender) {
        if (message instanceof Map<?,?> m) {
            Map<String, Object> map = m.entrySet().stream()
                    .filter(e -> e.getKey() instanceof String)
                    .collect(Collectors.toMap(
                            e -> ((String) e.getKey()), Map.Entry::getValue
                    ));
            cacheEvent(topic, map, sender!=null ? sender.getClass().getSimpleName() : null);
        }
    }

    @RequiredArgsConstructor
    public static class CacheEntry {
        public final String destination;
        public final Map<String,Object> payload;
        public final Map<String,Object> properties;
        public final long counter;
        public final long timestamp;
    }
}
