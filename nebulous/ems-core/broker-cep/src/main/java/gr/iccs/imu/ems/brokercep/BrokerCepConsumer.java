/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep;

import gr.iccs.imu.ems.brokercep.broker.BrokerConfig;
import gr.iccs.imu.ems.brokercep.cep.CepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import gr.iccs.imu.ems.util.StrUtil;
import jakarta.jms.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerCepConsumer implements MessageListener, InitializingBean, ApplicationListener<ContextClosedEvent> {
    private final static AtomicLong eventCounter = new AtomicLong(0);
    private final static AtomicLong textEventCounter = new AtomicLong(0);
    private final static AtomicLong objectEventCounter = new AtomicLong(0);
    private final static AtomicLong otherEventCounter = new AtomicLong(0);
    private final static AtomicLong eventFailuresCounter = new AtomicLong(0);

    private final BrokerCepProperties properties;
    private final BrokerConfig brokerConfig;
    private final BrokerService brokerService;    // Added in order to ensure that BrokerService will be instantiated first
    private final CepService cepService;

    private Connection connection;
    private Session session;
    private final Map<String,MessageConsumer> addedDestinations = new HashMap<>();

    private final TaskScheduler scheduler;
    private boolean shuttingDown;

    private final EventCache eventCache;

    @Getter
    private final List<MessageListener> listeners = new LinkedList<>();

    @Override
    public void afterPropertiesSet() {
        initialize();
    }

    public synchronized void initialize() {
        log.debug("BrokerCepConsumer.initialize(): Initializing Broker-CEP consumer instance...");
        try {
            // close previous session and connection
            closeConnection();

            // clear added destinations list
            addedDestinations.clear();

            // If an alternative Broker URL is provided for consumer, it will be used
            ConnectionFactory connectionFactory = brokerConfig.getConnectionFactoryForConsumer();

            // Initialize connection
            connection = (brokerConfig.getBrokerLocalAdminUsername() != null)
                    ? connectionFactory.createConnection(brokerConfig.getBrokerLocalAdminUsername(), brokerConfig.getBrokerLocalAdminPassword())
                    : connectionFactory.createConnection();
            connection.setExceptionListener(e -> {
                if (!shuttingDown) {
                    log.warn("BrokerCepConsumer: Connection exception listener: Exception caught: ", e);
                    scheduler.schedule(this::initialize, Instant.now());
                }
            });
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            log.debug("BrokerCepConsumer.initialize(): Initializing Broker-CEP consumer instance... done");
        } catch (Exception ex) {
            log.error("BrokerCepConsumer.initialize(): EXCEPTION: ", ex);
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("BrokerCepConsumer is shutting down");
        shuttingDown = true;
    }

    private void closeConnection() {
        // close previous session and connection
        try {
            if (session != null) {
                session.close();
                log.debug("BrokerCepConsumer.closeConnection(): Closed pre-existing sessions");
            }
        } catch (Exception e) {
            log.warn("BrokerCepConsumer.closeConnection(): Exception while closing old session: ", e);
        }
        try {
            if (connection != null) {
                connection.close();
                log.debug("BrokerCepConsumer.closeConnection(): Closed pre-existing connection");
            }
        } catch (Exception e) {
            log.warn("BrokerCepConsumer.closeConnection(): Exception while closing old connection: ", e);
        }
        session = null;
        connection = null;
    }

    public synchronized void addQueue(String queueName) {
        addDestinationAndListener(queueName, false, this);
    }

    public synchronized void addTopic(String topicName) {
        addDestinationAndListener(topicName, true, this);
    }

    private synchronized void addDestinationAndListener(String destinationName, boolean isTopic, MessageListener listener) {
        log.debug("BrokerCepConsumer.addDestinationAndListener(): Adding destination: {}", destinationName);
        if (addedDestinations.containsKey(destinationName)) {
            log.debug("BrokerCepConsumer.addDestinationAndListener(): Destination already added: {}", destinationName);
            return;
        }
        try {
            Destination destination = isTopic
                    ? session.createTopic(destinationName) : session.createQueue(destinationName);
            MessageConsumer consumer = session.createConsumer(destination);
            consumer.setMessageListener(listener);
            addedDestinations.put(destinationName, consumer);
            log.debug("BrokerCepConsumer.addDestinationAndListener(): Added destination: {}", destinationName);
        } catch (Exception ex) {
            log.error("BrokerCepConsumer.addDestinationAndListener(): EXCEPTION: ", ex);
        }
    }

    public synchronized void removeConsumerOf(String name) {
        log.debug("BrokerCepConsumer.removeConsumerOf(): Removing topic or queue: {}", name);
        if (!addedDestinations.containsKey(name)) {
            log.debug("BrokerCepConsumer.removeConsumerOf(): Topic/Queue not exists: {}", name);
            return;
        }
        try {
            MessageConsumer consumer = addedDestinations.remove(name);
            if (consumer!=null) consumer.close();
            log.debug("BrokerCepConsumer.removeConsumerOf(): Removed topic: {}", name);
        } catch (Exception ex) {
            log.error("BrokerCepConsumer.removeConsumerOf(): EXCEPTION: ", ex);
        }
    }

    public boolean containsDestination(String name) {
        return addedDestinations.containsKey(name);
    }

    @Override
    public void onMessage(Message message) {
        // Log message
        logMessage(message);

        // Record message
        if (brokerConfig.getEventRecorder()!=null)
            brokerConfig.getEventRecorder().recordRegisteredEvent(message);

        // Handle message
        try {
            log.trace("BrokerCepConsumer.onMessage(): {}", message);
            if (message instanceof ActiveMQObjectMessage mesg) {
                ActiveMQDestination messageDestination = mesg.getDestination();
                log.debug("BrokerCepConsumer.onMessage(): Message received: source={}, payload={}",
                        messageDestination.getPhysicalName(), mesg.getObject());

                // Send message to Esper
                if (mesg.getObject() instanceof Map) {
                    //cepService.handleEvent(StrUtil.castToMapStringObject(mesg.getObject()), messageDestination.getPhysicalName());
                    EventMap eventMap = new EventMap(StrUtil.castToMapStringObject(mesg.getObject()));
                    copyEventProperties(message, eventMap);
                    cepService.handleEvent(eventMap, messageDestination.getPhysicalName());
                    eventCache.cacheEvent(eventMap, messageDestination.getPhysicalName());
                } else {
                    if (mesg.getObject()!=null) {
                        cepService.handleEvent(mesg.getObject());
                        eventCache.cacheEvent(mesg.getObject(), null, messageDestination.getPhysicalName());
                    }
                }
                objectEventCounter.incrementAndGet();
            } else if (message instanceof ActiveMQTextMessage mesg) {
                ActiveMQDestination messageDestination = mesg.getDestination();
                log.debug("BrokerCepConsumer.onMessage(): Message received: source={}, payload={}, mime={}",
                        messageDestination.getPhysicalName(), mesg.getText(), mesg.getJMSXMimeType());

                // Send message to Esper
                //cepService.handleEvent(mesg.getText(), messageDestination.getPhysicalName());
                EventMap eventMap = new com.google.gson.Gson().fromJson(mesg.getText(), EventMap.class);
                copyEventProperties(message, eventMap);
                log.trace("BrokerCepConsumer.onMessage(): event-map={}", eventMap);
                cepService.handleEvent(eventMap, messageDestination.getPhysicalName());
                eventCache.cacheEvent(eventMap, messageDestination.getPhysicalName());
                textEventCounter.incrementAndGet();
            } else {
                otherEventCounter.incrementAndGet();
                log.warn("BrokerCepConsumer.onMessage(): Message ignored: type={}", message.getClass().getName());
            }
            eventCounter.incrementAndGet();

            // Notify listeners
            listeners.forEach(l -> l.onMessage(message));

        } catch (Exception ex) {
            log.error("BrokerCepConsumer.onMessage(): EXCEPTION: ", ex);
            eventFailuresCounter.incrementAndGet();
        }
    }

    private void logMessage(Message message) {
        boolean logBrokerMessages = properties.isLogBrokerMessages();
        boolean logBrokerMessagesFull = properties.isLogBrokerMessagesFull();
        if (!logBrokerMessages) return;

        long timestamp = System.currentTimeMillis();
        try {
            // Check if message passed is null
            if (message==null) {
                log.warn("\n==========|  **NULL** MESSAGE RECEIVED (timestamp={})", timestamp);
                return;
            }

            // Extract important message data (id, destination, metric-value)
            String jmsMesgId = message.getJMSMessageID();
            Destination jmsDest = message.getJMSDestination();
            String mesgStr = message.toString();
            String metricValue = StringUtils.substringBetween(mesgStr, "metricValue", ",");
            if (metricValue==null) metricValue = StringUtils.substringBetween(mesgStr, "metricValue", "}");
            if (metricValue!=null) metricValue = metricValue.replace("\"", "").replace(":", "").trim();
            else metricValue = logBrokerMessagesFull ? "---See next---" : "---Not found---";

            // Log message data
            if (logBrokerMessagesFull)
                log.info("\n==========|  RECEIVED A MESSAGE: dest={}, metricValue={}, timestamp={}, id={}\n{}", jmsDest, metricValue, timestamp, jmsMesgId, message);
            else
                log.info("\n==========|  RECEIVED A MESSAGE: dest={}, metricValue={}, timestamp={}, id={}", jmsDest, metricValue, timestamp, jmsMesgId);

        } catch (Exception e) {
            // Log error
            if (logBrokerMessagesFull)
                log.warn("\n==========|  RECEIVED A MESSAGE: FAILED TO PARSE. SEE NEXT FOR STACKTRACE (timestamp={})\n{}\n\nSTACKTRACE:\n", timestamp, message, e);
            else
                log.warn("\n==========|  RECEIVED A MESSAGE: FAILED TO PARSE. SEE NEXT FOR STACKTRACE (timestamp={})\n\nSTACKTRACE:\n", timestamp, e);
        }
    }

    private EventMap copyEventProperties(Message message, EventMap eventMap) throws JMSException {
        log.debug("BrokerCepConsumer.copyEventProperties(): BEGIN: message={}, event={}", message, eventMap);

        // Copy message properties to event map
        Collections.list((Enumeration<?>) message.getPropertyNames()).forEach(s -> {
            String n = s.toString();
            log.trace("BrokerCepConsumer.copyEventProperties(): Copying property: message={}, event={}, property={}", message, eventMap, n);
            try {
                String v = message.getStringProperty(n);
                eventMap.setEventProperty(n, v);
                log.debug("BrokerCepConsumer.copyEventProperties(): Copied property: message={}, event={}, property={}, value={}", message, eventMap, n, v);
            } catch (Exception e) {
                log.debug("BrokerCepConsumer.copyEventProperties(): EXCEPTION: while copying property: message={}, event={}, property={}, Exception: ", message, eventMap, n, e);
            }
        });
        return eventMap;
    }

    public static long getEventCounter() { return eventCounter.get(); }
    public static long getTextEventCounter() { return textEventCounter.get(); }
    public static long getObjectEventCounter() { return objectEventCounter.get(); }
    public static long getOtherEventCounter() { return otherEventCounter.get(); }
    public static long getEventFailuresCounter() { return eventFailuresCounter.get(); }
    public static synchronized void clearCounters() {
        eventCounter.set(0L);
        textEventCounter.set(0L);
        objectEventCounter.set(0L);
        otherEventCounter.set(0L);
        eventFailuresCounter.set(0L);
    }
}