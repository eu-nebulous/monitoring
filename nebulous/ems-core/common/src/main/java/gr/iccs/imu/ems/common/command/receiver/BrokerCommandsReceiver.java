/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.command.receiver;

import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.common.command.Command;
import gr.iccs.imu.ems.common.command.CommandProcessor;
import gr.iccs.imu.ems.common.command.CommandProcessorProperties;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Service
public class BrokerCommandsReceiver implements InitializingBean {
    private final static String COMMAND_RECEIVER_NAME = "broker";
    private final static String COMMAND_TOPIC = "ems.command";
    private final static String COMMAND_RESULTS_TOPIC = "ems.command.results";

    private final BrokerCepService brokerCepService;
    private final CommandProcessor commandProcessor;
    private final CommandProcessorProperties properties;
    private Map<String, String> config;

    @Override
    public void afterPropertiesSet() throws Exception {
        config = properties.getReceivers().getOrDefault(COMMAND_RECEIVER_NAME, Map.of());
        connectToBroker();
    }

    private void connectToBroker() throws JMSException {
        log.debug("BrokerCommandsReceiver: Connecting to local broker");
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                brokerCepService.getBrokerCepProperties().getBrokerUrlForConsumer());
        ActiveMQConnection connection = (ActiveMQConnection) (StringUtils.isNotBlank(brokerCepService.getBrokerUsername())
                ? connectionFactory.createConnection(brokerCepService.getBrokerUsername(), brokerCepService.getBrokerPassword())
                : connectionFactory.createConnection());
        Session session = connection.createSession(true, 0);

        List<String> topics = Collections.singletonList(
                config.getOrDefault("topic", COMMAND_TOPIC)
        );
        log.debug("BrokerCommandsReceiver: Will subscribe to topics: {}", topics);

        MessageListener listener = getMessageListener();
        for (String topic : topics) {
            MessageConsumer consumer = session.createConsumer(new ActiveMQTopic(topic));
            consumer.setMessageListener(listener);
            log.debug("BrokerCommandsReceiver: Subscribed to topic: {}", topic);
        }

        connection.start();
        log.debug("BrokerCommandsReceiver: STARTED");
    }

    private MessageListener getMessageListener() {
        return message -> {
            try {
                Object bodyObj = message.getBody(Object.class);
                if (bodyObj instanceof Map map) {
                    enqueueCommand(
                            map.getOrDefault("command", "").toString().trim(),
                            map.getOrDefault("ref", "").toString().trim()
                    );
                } else if (bodyObj != null) {
                    enqueueCommand(bodyObj.toString().trim(), null);
                } else
                    log.warn("BrokerCommandsReceiver: Message body is null. Ignoring message");
            } catch (JMSException e) {
                log.warn("BrokerCommandsReceiver: Failed to deserialize message body. Expected map");
            }
        };
    }

    private void enqueueCommand(String commandStr, String ref) {
        if (StringUtils.isNotBlank(commandStr)) {
            Command command = Command.builder()
                    .command(commandStr)
                    .ref(ref)
                    .build();
            command.setCallback(results -> commandCallback(results, command));
            command.splitArgs();
            log.debug("BrokerCommandsReceiver: Command after splitting to args: {}", command);
            if (commandProcessor.addCommand(command))
                log.info("BrokerCommandsReceiver: Enqueued command: {}", command);
            else {
                log.warn("BrokerCommandsReceiver: Command dropped: {}", command);
                sendCommandResult(command, CommandProcessor.COMMAND_QUEUE_IS_FULL);
            }
        } else {
            log.warn("BrokerCommandsReceiver: Command string is empty. Ignoring message");
        }
    }

    private void commandCallback(Object results, Command command) {
        Serializable resultsSer;
        if (results == null) {
            resultsSer = null;
        } else if (results instanceof Serializable s) {
            resultsSer = s;
        } else {
            resultsSer = String.format("Object not serializable: %s: %s", results.getClass().getName(), results);
        }
        sendCommandResult(command, resultsSer);
    }

    private void sendCommandResult(Command command, Serializable resultsSer) {
        try {
            log.debug("BrokerCommandsReceiver: Sending command result: Command: {} -- Result: {}", command, resultsSer);
            HashMap<String, Object> event = new HashMap<>();
            event.put("command", command);
            event.put("results-topic", resultsSer);
            if (StringUtils.isNotBlank(command.getRef()))
                event.put("ref", command.getRef());
            brokerCepService.publishSerializable(config.getOrDefault("results-topic", COMMAND_RESULTS_TOPIC), event, true);
            log.trace("BrokerCommandsReceiver: Sent command result: Command: {} -- Result: {}", command, resultsSer);
        } catch (Exception e) {
            log.warn("BrokerCommandsReceiver: Failed to submit command result: Command: {} -- Exception: ", command, e);
        }
    }
}
