/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep;

import gr.iccs.imu.ems.brokercep.cep.StatementSubscriber;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.util.GroupingConfiguration;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class BrokerCepStatementSubscriber implements StatementSubscriber {
    private final static AtomicLong counterLocalPublishSuccess = new AtomicLong(0);
    private final static AtomicLong counterLocalPublishFailure = new AtomicLong(0);
    private final static AtomicLong counterForwardSuccess = new AtomicLong(0);
    private final static AtomicLong counterForwardFailure = new AtomicLong(0);

    private final String name;
    private final String topic;
    private final String statement;
    private final BrokerCepService brokerCep;
    private final PasswordUtil passwordUtil;
    @Setter
    private Set<GroupingConfiguration.BrokerConnectionConfig> forwardToGroupings;

    public void update(Map<String, Object> eventMap) {
        log.trace("BrokerCepStatementSubscriber.update(): INPUT: {}", eventMap);
        EventMap.checkEvent(eventMap);
        publishToLocalBroker(eventMap);
        forwardToGroupings(eventMap);
    }

    protected void publishToLocalBroker(Map<String, Object> eventMap) {
        log.debug("- New event received: subscriber={}, topic={}, payload={}", name, topic, eventMap);
        String localBrokerUrl = brokerCep.getBrokerCepProperties().getBrokerUrlForConsumer();
        String username = brokerCep.getBrokerUsername();
        String password = brokerCep.getBrokerPassword();
        String passwordEncoded = passwordUtil.encodePassword(password);
        try {
            // Queue new event for publishing to Local Broker topic
            EventForwarder.getInstance().addLocalPublishTask(this, topic, eventMap, ()->countLocalPublish(true), ()->countLocalPublish(false));
            log.trace("- Event queued for publishing to local broker: subscriber={}, local-broker={}, username={}, password={}, topic={}, payload={}",
                    name, localBrokerUrl, username, passwordEncoded, topic, eventMap);
        } catch (Exception ex) {
            log.error("- New event: ERROR while queueing event for publishing to local broker: subscriber={}, local-broker={}, username={}, password={}, topic={}, exception=",
                    name, localBrokerUrl, username, passwordEncoded, topic, ex);
            countLocalPublish(false);
        }
    }

    protected void forwardToGroupings(Map<String, Object> eventMap) {
        // Queue event for forwarding to the next grouping(s)
        log.trace("- Forwarding event to groupings: subscriber={}, forward-to-groupings={}, payload={}",
                name, forwardToGroupings, eventMap);
        if (forwardToGroupings==null)
            return;
        for (GroupingConfiguration.BrokerConnectionConfig fwdToGrouping : forwardToGroupings) {
            try {
                EventForwarder.getInstance().addEventForwardTask(this, fwdToGrouping, topic, eventMap, ()->countForward(true), ()->countForward(false));
                log.debug("- Event queued for forwarding to grouping: subscriber={}, forward-to-grouping={}, topic={}, payload={}",
                        name, fwdToGrouping, topic, eventMap);
            } catch (Exception ex) {
                log.error("- ERROR while queuing event in forward queue: subscriber={}, forward-to-groupings={}, payload={}, exception: ",
                        name, forwardToGroupings, eventMap, ex);
                countForward(false);
            }
        }
    }

    private void countLocalPublish(boolean success) {
        if (success) counterLocalPublishSuccess.incrementAndGet();
        else counterLocalPublishFailure.incrementAndGet();
    }

    private void countForward(boolean success) {
        if (success) counterForwardSuccess.incrementAndGet();
        else counterForwardFailure.incrementAndGet();
    }

    public static long getLocalPublishSuccessCounter() { return counterLocalPublishSuccess.get(); }
    public static long getLocalPublishFailureCounter() { return counterLocalPublishFailure.get(); }
    public static long getForwardSuccessCounter() { return counterForwardSuccess.get(); }
    public static long getForwardFailureCounter() { return counterForwardFailure.get(); }
    public static synchronized void clearCounters() {
        counterLocalPublishSuccess.set(0L);
        counterLocalPublishFailure.set(0L);
        counterForwardSuccess.set(0L);
        counterForwardFailure.set(0L);
    }
}
