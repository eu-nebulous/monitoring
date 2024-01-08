/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Builder
public class EventBus<T,M,S> {  // Topic,Message,Sender
    //enum STANDARD_EVENT_TOPICS { CONTROL_EVENT, TRANSLATOR_EVENT, BAGUETTE_SERVER_EVENT, BROKER_CEP_EVENT, CLIENT_INSTALLER_EVENT }

    /*private static EventBus<Object,Object,Object> _DEFAULT;
    private static void initDefault() {
        _DEFAULT = EventBus.builder()
                //.allowedTopics(Arrays.stream(STANDARD_EVENT_TOPICS.values()).map(Enum::name).collect(Collectors.toSet()))
                .build();
    }
    public static EventBus<Object,Object,Object> getDefault() { initDefault(); return _DEFAULT; }
    public static void setDefault(EventBus<Object,Object,Object> eventBus) { _DEFAULT = eventBus; }*/

    private final Set<T> allowedTopics;
    private final Set<S> allowedSenders;
    private final Map<T, Set<EventConsumer<T,M,S>>> topicsAndConsumers = new LinkedHashMap<>();
    private final Map<EventConsumer<T,M,S>, List<Pattern>> consumerPatternMap = new LinkedHashMap<>();

    public void send(@NonNull T topic, @NonNull M message) {
        send(topic, message, null);
    }

    public void send(@NonNull T topic, @NonNull M message, S sender) {
        sendSync(topic, message, sender);
    }

    public void sendSync(@NonNull final T topic, @NonNull final M message, final S sender) {
        log.debug("EventBus: sendSync: BEGIN: topic={}, sender={}, message={}", topic, sender, message);
        checkTopic(topic);
        checkSender(sender);
        log.trace("EventBus: sendSync: CHECKED: topicsAndConsumers={}, consumerPatternMap={}", topicsAndConsumers, consumerPatternMap);
        Set<EventConsumer<T,M,S>> topicConsumers = topicsAndConsumers.get(topic);
        log.debug("EventBus: sendSync: CHECKED: topic={}, sender={}, message={}, consumers={}", topic, sender, message, topicConsumers);
        if (topicConsumers!=null) {
            topicConsumers.forEach(consumer -> {
                log.debug("EventBus: sendSync: ....SENDING-TO-CONSUMER: topic={}, sender={}, consumer={}, message={}", topic, sender, consumer, message);
                consumer.onMessage(topic, message, sender);
            });
        }
        final String topicString = topic.toString();
        consumerPatternMap.forEach((consumer, patternSet) -> patternSet.forEach(pattern -> {
            log.debug("EventBus: sendSync: ....CHECKING PATTERN: topic={}, sender={}, consumer={}, pattern={}, message={}", topic, sender, consumer, pattern.pattern(), message);
            if (pattern.matcher(topicString).matches()) {
                log.debug("EventBus: sendSync: ....SENDING-TO-PATTERN-CONSUMER: topic={}, sender={}, consumer={}, pattern={}, message={}", topic, sender, consumer, pattern.pattern(), message);
                consumer.onMessage(topic, message, sender);
            }
        }));
    }

    public boolean subscribe(@NonNull T topic, @NonNull EventConsumer<T,M,S> consumer) {
        checkTopic(topic);
        Set<EventConsumer<T,M,S>> topicConsumers = topicsAndConsumers.get(topic);
        if (topicConsumers==null) {
            synchronized (topicsAndConsumers) {
                topicConsumers = topicsAndConsumers.computeIfAbsent(topic, k -> new HashSet<>());
            }
        }

        return topicConsumers.add(consumer);
    }

    public boolean unsubscribe(@NonNull T topic, @NonNull EventConsumer<T,M,S> consumer) {
        checkTopic(topic);
        Set<EventConsumer<T,M,S>> topicConsumers = topicsAndConsumers.get(topic);
        if (topicConsumers!=null) {
            boolean result = topicConsumers.remove(consumer);
            if (topicConsumers.isEmpty()) {
                synchronized (topicsAndConsumers) {
                    topicConsumers = topicsAndConsumers.get(topic);
                    if (topicConsumers.isEmpty()) {
                        topicsAndConsumers.remove(topic);
                    }
                }
            }
            return result;
        }
        return false;
    }

    public boolean subscribePattern(@NonNull String patternString, @NonNull EventConsumer<T,M,S> consumer) {
        Pattern pattern = Pattern.compile(patternString);
        List<Pattern> consumerPatterns = consumerPatternMap.get(consumer);
        if (consumerPatterns==null) {
            synchronized (consumerPatternMap) {
                consumerPatterns = consumerPatternMap.computeIfAbsent(consumer, k -> new ArrayList<>());
            }
        }

        return consumerPatterns.add(pattern);
    }

    public boolean unsubscribePattern(@NonNull String patternString, @NonNull EventConsumer<T,M,S> consumer) {
        List<Pattern> consumerPatterns = consumerPatternMap.get(consumer);
        if (consumerPatterns!=null) {
            Optional<Pattern> item = consumerPatterns.stream().filter(pattern -> pattern.pattern().equals(patternString)).findAny();
            boolean result = false;
            if (item.isPresent())
                result = consumerPatterns.remove(item.get());
            if (consumerPatterns.isEmpty()) {
                synchronized (consumerPatternMap) {
                    consumerPatterns = consumerPatternMap.get(consumer);
                    if (consumerPatterns.isEmpty()) {
                        consumerPatternMap.remove(consumer);
                    }
                }
            }
            return result;
        }
        return false;
    }

    private void checkTopic(@NonNull T topic) {
        if (allowedTopics==null || allowedTopics.isEmpty()) return;
        if (!allowedTopics.contains(topic))
            throw new IllegalArgumentException("Topic not allowed in event bus: "+topic);
    }

    private void checkSender(S sender) {
        if (allowedSenders==null || allowedSenders.isEmpty()) return;
        if (!allowedSenders.contains(sender))
            throw new IllegalArgumentException("Sender not allowed in event bus: "+sender);
    }

    public interface EventConsumer<T,M,S> {
        void onMessage(T topic, M message, S sender);
    }
}
