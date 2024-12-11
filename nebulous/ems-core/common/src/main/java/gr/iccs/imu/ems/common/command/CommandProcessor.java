/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.command;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Common Command Processor:
 * Executes commands received from various channels
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandProcessor implements InitializingBean {
    private final static int MAX_COMMAND_QUEUE_SIZE   = 100;
    public final static String COMMAND_QUEUE_IS_FULL  = "COMMAND_QUEUE_IS_FULL";
    public final static String COMMAND_STRING_BLANK   = "COMMAND_STRING_BLANK";
    public final static String COMMAND_STRING_INVALID = "COMMAND_STRING_INVALID";
    public final static String MISSING_ARGUMENTS      = "MISSING_ARGUMENTS";
    public final static String BLANK_ARGUMENTS        = "BLANK_ARGUMENTS";
    public final static String NO_EXECUTOR_FOUND      = "NO_EXECUTOR_FOUND";

    private final PriorityQueue<ICommandExecutor> commandExecutors = new PriorityQueue<>(Comparator.comparingInt(ICommandExecutor::getPriority));
    private final BlockingQueue<Command> commandsQueue = new LinkedBlockingQueue<>(MAX_COMMAND_QUEUE_SIZE);

    private final AtomicLong commandsCompletedCounter = new AtomicLong();
    private final AtomicLong commandsFailedCounter = new AtomicLong();
    private final AtomicLong commandsSkippedCounter = new AtomicLong();
    private final AtomicLong commandsWithoutCallbacks = new AtomicLong();
    private final AtomicLong commandCallbacksCompletedCounter = new AtomicLong();
    private final AtomicLong commandCallbacksFailedCounter = new AtomicLong();

    @Override
    public void afterPropertiesSet() {
        startCommandExecutionThread();
    }

    public synchronized void registerCommandExecutor(@NonNull ICommandExecutor commandExecutor) {
        commandExecutors.add(commandExecutor);
    }

    public synchronized void unregisterCommandExecutor(@NonNull ICommandExecutor commandExecutor) {
        commandExecutors.remove(commandExecutor);
    }

    public boolean addCommand(@NonNull Command command) {
        if (StringUtils.isBlank(command.getCommand()))
            throw new IllegalArgumentException("Command string cannot be empty or blank");
        command.setReceivedTimestamp(System.currentTimeMillis());
        boolean added = commandsQueue.offer(command);
        if (! added) {
            command.setReceivedTimestamp(0);
            log.warn("CommandProcessor: Command queue is FULL. Command not added: {}", command);
        }
        return added;
    }

    private void startCommandExecutionThread() {
        Thread thread = new Thread(() -> {
            while (true) {
                Command command = null;
                try {
                    command = commandsQueue.take();
                    log.trace("CommandProcessor: Executing command: {}", command);
                    Object result = processCommand(command);
                    log.trace("CommandProcessor: Command completed: {}", command);
                    if (command.getCallback()!=null) {
                        try {
                            log.trace("CommandProcessor: Invoking command callback: {}", command);
                            command.getCallback().accept(result);
                            commandCallbacksCompletedCounter.incrementAndGet();
                            log.trace("CommandProcessor: Command callback completed: {}", command);
                        } catch (Exception e) {
                            commandCallbacksFailedCounter.incrementAndGet();
                            log.warn("CommandProcessor: Command callback failed: {} -- Exception:\n", command, e);
                        }
                    } else {
                        commandsWithoutCallbacks.incrementAndGet();
                        log.trace("CommandProcessor: Command completed has no callback: {}", command);
                    }
                } catch (Exception e) {
                    commandsFailedCounter.incrementAndGet();
                    log.warn("CommandProcessor: Command failed: {} -- Exception:\n", command, e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    protected Object processCommand(Command command) {
        String commandStr = command.getCommand().trim();
        command.setCommand(commandStr);
        if (commandStr.isBlank()) return COMMAND_STRING_BLANK;

        for (ICommandExecutor commandExecutor : commandExecutors) {
            if (commandExecutor.canHandle(command)) {
                command.setStartExecutionTimestamp(System.currentTimeMillis());
                Object result = commandExecutor.execute(command);
                command.setEndExecutionTimestamp(System.currentTimeMillis());
                commandsCompletedCounter.incrementAndGet();
                return result;
            }
        }
        log.warn("CommandProcessor: No command executor found for command: {}", command);
        commandsSkippedCounter.incrementAndGet();
        return NO_EXECUTOR_FOUND;
    }

    public synchronized Map<String,Long> getStatistics() {
        return Map.of(
                "commandsCompletedCounter", commandsCompletedCounter.get(),
                "commandsFailedCounter", commandsFailedCounter.get(),
                "commandsSkippedCounter", commandsSkippedCounter.get(),
                "commandsWithoutCallbacks", commandsWithoutCallbacks.get(),
                "commandCallbacksCompletedCounter", commandCallbacksCompletedCounter.get(),
                "commandCallbacksFailedCounter", commandCallbacksFailedCounter.get()
        );
    }
}
