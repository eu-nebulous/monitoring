/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker.interceptor;

import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.Message;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import javax.jms.JMSException;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class MessageForwarderInterceptor extends AbstractMessageInterceptor {
    private final static MessageQueueProcessor messageQueueProcessor = new MessageQueueProcessor();

    public void initialized() {
        startQueueProcessing(applicationContext);
    }

    @Override
    public void intercept(Message message) {
        log.trace("MessageForwarderInterceptor:  Message: {}", message);
        // enqueue message for processing
        messageQueueProcessor.getMessageQueue().add(message);
    }

    private void startQueueProcessing(ApplicationContext applicationContext) {
        synchronized (messageQueueProcessor) {
            if (!messageQueueProcessor.isRunning()) {
                messageQueueProcessor.setApplicationContext(applicationContext);
                messageQueueProcessor.start();
            }
        }
    }

    protected static class MessageQueueProcessor implements Runnable {
        private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
        private ApplicationContext applicationContext;
        private BrokerCepService brokerCepService;
        private Thread runner;
        private boolean keepRunning;
        protected List<BrokerCepProperties.ForwardDestinationConfig> forwardDestinations;

        public void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        public Queue<Message> getMessageQueue() {
            return messageQueue;
        }

        @Override
        public void run() {
            log.info("MessageQueueProcessor: Starts processing message queue and forward messages");
            while (keepRunning) {
                String connectionString = null;
                String username = null;
                String password = null;
                String destination = null;
                try {
                    Message m = messageQueue.take();
                    log.trace("MessageQueueProcessor: Message taken from queue: {}", m);

                    if (! isMessageForwardPossible(m)) {
                        //keepRunning = false;
                        continue;
                    }

                    destination = m.getDestination().getPhysicalName();
                    EventMap eventMap = messageToEvent(m);
                    for (BrokerCepProperties.ForwardDestinationConfig config : forwardDestinations) {
                        connectionString = config.getConnectionString();
                        username = config.getUsername();
                        password = config.getPassword();
                        log.trace("MessageQueueProcessor: Forwarding message to: {}/{} (username: {}): {}",
                                connectionString, destination, username, eventMap);
                        if (StringUtils.isBlank(username))
                            brokerCepService.publishEvent(connectionString, destination, eventMap);
                        else
                            brokerCepService.publishEvent(connectionString, username, password, destination, eventMap);
                        log.debug("MessageQueueProcessor: Message forwarded to: {}/{} (username: {}): {}",
                                connectionString, destination, username, eventMap);
                    }

                } catch (InterruptedException e) {
                    log.error("MessageQueueProcessor: Exception while taking message from queue: ", e);
                } catch (JMSException e) {
                    log.error("MessageQueueProcessor: Exception while sending message to: {}/{}: Exception: ",
                            connectionString, destination, e);
                }
            }
            runner = null;
            log.warn("MessageQueueProcessor: Stopped processing message queue");
        }

        private boolean isMessageForwardPossible(Message m) {
            if (brokerCepService==null) {
                try {
                    this.brokerCepService = applicationContext.getBean(BrokerCepService.class);
                    if (brokerCepService==null) {
                        log.error("MessageQueueProcessor: Null BrokerCepService instance returned");
                        return false;
                    }
                } catch (Exception e) {
                    log.error("MessageQueueProcessor: Exception while getting BrokerCepService instance: ", e);
                    return false;
                }
            }
            if (forwardDestinations==null) {
                try {
                    BrokerCepProperties bcp = applicationContext.getBean(BrokerCepProperties.class);
                    if (bcp==null) {
                        log.error("MessageQueueProcessor: Null BrokerCepProperties instance returned");
                        return false;
                    }
                    forwardDestinations = bcp.getMessageForwardDestinations();
                    log.info("MessageQueueProcessor: Forward destinations initialized: {}", forwardDestinations);
                } catch (Exception e) {
                    log.error("MessageQueueProcessor: Exception while getting BrokerCepProperties instance: ", e);
                    return false;
                }
            }
            if (forwardDestinations.size()==0) {
                log.debug("MessageQueueProcessor: No forward destinations specified. Discarding message: {}", m);
                return false;
            }
            return true;
        }

        private EventMap messageToEvent(Message message) {
            try {
                log.trace("MessageForwarderInterceptor.messageToEvent(): message: {}", message);
                Map<String, Object> eventProperties = message.getProperties();
                log.trace("MessageForwarderInterceptor.messageToEvent(): event-properties: {}", eventProperties);
                if (message instanceof ActiveMQObjectMessage mesg) {
                    if (mesg.getObject() instanceof Map) {
                        EventMap eventMap = new EventMap(StrUtil.castToMapStringObject(mesg.getObject()));
                        if (eventProperties!=null) eventMap.putAll(eventProperties);
                        log.trace("MessageForwarderInterceptor.messageToEvent(): event-map: {}", eventMap);
                        return eventMap;
                    }
                } else if (message instanceof ActiveMQTextMessage mesg) {
                    // Send message to Esper
                    EventMap eventMap = EventMap.parseEventMap(mesg.getText());
                    if (eventProperties!=null) eventMap.putAll(eventProperties);
                    log.trace("MessageForwarderInterceptor.messageToEvent(): event-map: {}", eventMap);
                    return eventMap;
                } else {
                    log.warn("MessageForwarderInterceptor.messageToEvent(): Message ignored: type={}", message.getClass().getName());
                }
            } catch (Exception ex) {
                log.error("MessageForwarderInterceptor.messageToEvent(): EXCEPTION: ", ex);
            }
            throw new RuntimeException("Unsupported Message type: "+message.getClass());
        }

        public synchronized void start() {
            if (runner==null) {
                keepRunning = true;
                runner = new Thread(this);
                runner.setDaemon(true);
                runner.start();
            } else {
                log.warn("MessageQueueProcessor is already running");
            }
        }

        public synchronized void stop() {
            if (isRunning()) {
                keepRunning = false;
                runner.interrupt();
            } else {
                log.warn("MessageQueueProcessor is not running");
            }
        }

        public boolean isRunning() {
            if (runner==null) return false;
            return runner.isAlive();
        }
    }
}
