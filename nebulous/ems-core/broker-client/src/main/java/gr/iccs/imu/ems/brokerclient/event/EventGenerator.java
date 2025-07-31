/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokerclient.event;

import gr.iccs.imu.ems.brokerclient.BrokerClient;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

@Slf4j
@Data
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EventGenerator implements Runnable {
    private final static AtomicLong counter = new AtomicLong();
    private final BiFunction<String,EventMap,Boolean> eventPublisher;
    private final BrokerClient client;
    private String brokerUrl;
    private String brokerUsername;
    private String brokerPassword;
    private String destinationName;
    private String eventType;
    private Map<String, String> eventProperties = new HashMap<>();
    private long interval;
    private long howMany = -1;
    private double lowerValue;
    private double upperValue;
    private int level;
    private int countSent;
    private int retriesLimit = Integer.MAX_VALUE;

    private transient Thread runner;
    private transient boolean keepRunning;
    private transient boolean paused;

    public EventGenerator(@NonNull BiFunction<String,EventMap,Boolean> eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.client = null;
    }

    public EventGenerator(@NonNull BrokerClient brokerClient) {
        this.eventPublisher = null;
        this.client = brokerClient;
    }

    @PostConstruct
    public void printCounter() {
        log.info("New EventGenerator with instance number: {}", counter.getAndIncrement());
    }

    public void start() {
        if (keepRunning) return;
        runner = new Thread(this);
        runner.setDaemon(true);
        runner.start();
    }

    public void stop() {
        paused = false;
        _stop();
    }

    protected void _stop() {
        keepRunning = false;
        if (runner!=null)
            runner.interrupt();
        runner = null;
    }

    public void pause() {
        if (paused) return;
        paused = true;
        _stop();
    }

    public void resume() {
        if (paused) start();
        else log.warn("Not paused");
    }

    public void run() {
        log.info("EventGenerator.run(): Start sending events: event-generator: {}", this);

        keepRunning = true;
        if (! paused) countSent = 0; else paused = false;
        double newValue = getNewValue();
        long retries = 0;
        while (keepRunning) {
            try {
                if (retries==0) {
                    newValue = getNewValue();
                }
                EventMap event = new EventMap(newValue, level, System.currentTimeMillis());
                log.info("EventGenerator.run(): Sending event #{}: {}", countSent + 1, event);
                if (eventPublisher!=null)
                    eventPublisher.apply(destinationName, event);
                else
                    client.publishEventWithCredentials(brokerUrl, brokerUsername, brokerPassword, destinationName, eventType, event, eventProperties);
                countSent++;
                if (countSent == howMany) keepRunning = false;
                log.info("EventGenerator.run(): Event sent #{}: {}", countSent, event);
                retries = 0;
            } catch (Exception ex) {
                log.warn("EventGenerator.run(): WHILE-EXCEPTION: ", ex);
                retries++;
                if (retries > retriesLimit) {
                    log.warn("EventGenerator.run(): Retries limit exceeded. Stopping");
                    keepRunning = false;
                }
            }
            // sleep for 'interval' ms
            try {
                if (keepRunning) {
                    Thread.sleep(interval);
                }
            } catch (InterruptedException ex) {
                log.warn("EventGenerator.run(): Sleep interrupted");
            }
        }

        log.info("EventGenerator.run(): Stop sending events: event-generator: {}", this);
    }

    public double getNewValue() {
        return getRandomValue();
    }

    public double getRandomValue() {
        return Math.random() * (upperValue - lowerValue) + lowerValue;
    }

    public void setValues(double l, double u) {
        if (l > u)
            throw new IllegalArgumentException("Lower value is greater than Upper value: "+l+" > "+u);
        lowerValue = l;
        upperValue = u;
    }

    public void setLowerValue(double v) {
        if (v > upperValue)
            throw new IllegalArgumentException("New lower value is greater than upper value: "+v+" > "+upperValue);
        lowerValue = v;
    }

    public void setUpperValue(double v) {
        if (v < lowerValue)
            throw new IllegalArgumentException("New upper value is less than lower value: "+lowerValue+" < "+v);
        upperValue = v;
    }
}