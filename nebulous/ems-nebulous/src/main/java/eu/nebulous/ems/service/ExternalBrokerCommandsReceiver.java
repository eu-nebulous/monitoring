/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import com.google.gson.Gson;
import gr.iccs.imu.ems.common.command.Command;
import gr.iccs.imu.ems.common.command.CommandProcessor;
import gr.iccs.imu.ems.common.command.CommandProcessorProperties;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
@Service
public class ExternalBrokerCommandsReceiver implements InitializingBean {
    private final static String COMMAND_RECEIVER_NAME = "nebulous-external-broker";

    private final ApplicationContext applicationContext;
    private final CommandProcessor commandProcessor;
    private final CommandProcessorProperties properties;
    private final Gson gson;
    private Map<String, String> config;

    @Override
    public void afterPropertiesSet() throws Exception {
        config = properties.getReceivers().getOrDefault(COMMAND_RECEIVER_NAME, Map.of());
    }

    public void processCommandMessage(@NonNull Object commandObject) {
        try {
            if (commandObject instanceof Map map) {
                enqueueCommand(
                        map.getOrDefault("command", "").toString().trim(),
                        map.getOrDefault("ref", "").toString().trim()
                );
            } else {
                enqueueCommand(commandObject.toString().trim(), null);
            }
        } catch (Exception e) {
            log.warn("ExternalBrokerCommandsReceiver: Failed to deserialize message body. Expected map");
        }
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
            //event.put("command", command.toString());
            event.put("command", gson.fromJson(gson.toJson(command), Map.class));
            event.put("results-topic", resultsSer);
            if (StringUtils.isNotBlank(command.getRef()))
                event.put("ref", command.getRef());
            applicationContext.getBean(ExternalBrokerListenerService.class).getCommandsResponsePublisher().send(event);
            log.trace("BrokerCommandsReceiver: Sent command result: Command: {} -- Result: {}", command, resultsSer);
        } catch (Exception e) {
            log.warn("BrokerCommandsReceiver: Failed to submit command result: Command: {} -- Exception: ", command, e);
        }
    }
}
