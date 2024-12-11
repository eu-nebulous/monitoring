/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.command.executor;

import gr.iccs.imu.ems.common.command.Command;
import gr.iccs.imu.ems.common.command.CommandProcessor;
import gr.iccs.imu.ems.common.command.ICommandExecutor;
import gr.iccs.imu.ems.util.LogsUtil;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Log-Level Command Executor:
 * Sets/Prints the current log level
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogLevelICommandExecutor implements ICommandExecutor, InitializingBean {
    public final static int EXECUTOR_PRIORITY = 0;
    public final static String HELP_STRING =
            "LogLevelICommandExecutor: Expected args: 'SET ; logger-name ; log-level' OR 'PRINT ; logger-name-pattern ; TRUE/FALSE (return Map, if pattern)'";

    private final CommandProcessor commandProcessor;

    @Override
    public void afterPropertiesSet() throws Exception {
        commandProcessor.registerCommandExecutor(this);
    }

    @Override
    public int getPriority() {
        return EXECUTOR_PRIORITY;
    }

    @Override
    public boolean canHandle(@NonNull Command command) {
        return "LOG-LEVEL".equalsIgnoreCase(command.getCommand());
    }

    @Override
    public Object execute(@NonNull Command command) {
        if (!canHandle(command)) return null;
        if (command.getArgs().size()<2) {
            log.warn("LogLevelICommandExecutor: Not enough arguments: {}", command);
            log.warn(HELP_STRING);
            return CommandProcessor.MISSING_ARGUMENTS;
        }
        String subCommand = command.getArgs().getFirst().trim().toUpperCase();
        String loggerName = command.getArgs().get(1).trim();
        if (StringUtils.isAnyEmpty(subCommand, loggerName)) {
            log.warn("LogLevelICommandExecutor: Arguments cannot be empty: {}", command);
            log.warn(HELP_STRING);
            return CommandProcessor.BLANK_ARGUMENTS;
        }

        switch (subCommand) {
            case "SET" -> {
                if (command.getArgs().size()<3) {
                    log.warn("LogLevelICommandExecutor: Not enough arguments: {}", command);
                    log.warn(HELP_STRING);
                    return CommandProcessor.MISSING_ARGUMENTS;
                }
                String logLevel = command.getArgs().get(2).trim().toUpperCase();
                if (StringUtils.equalsAnyIgnoreCase(logLevel, "-", "null")) logLevel = null;
                LogsUtil.setLogLevel(loggerName, logLevel);
                return LogsUtil.getLogLevel(loggerName);
            }
            case "GET", "PRINT" -> {
                if (StringUtils.endsWithAny(loggerName, "*")) {
                    boolean returnMap =
                            (command.getArgs().size()>=3 && "TRUE".equalsIgnoreCase(command.getArgs().get(2).trim()));
                    TreeMap<String,String> resultMap = returnMap ? new TreeMap<>() : null;

                    String prefix = StringUtils.stripEnd(loggerName, "*");
                    log.info("LOG-LEVELS for: {}", loggerName);
                    Objects.requireNonNullElse(LogsUtil.getLoggers(prefix), Map.<String,String>of()).entrySet().stream()
                            .filter(entry ->
                                    StringUtils.startsWith(entry.getKey(), prefix))
                            .forEach(entry -> {
                                    log.info("  {} {}", String.format("%-5s",entry.getValue()), entry.getKey());
                                    if (returnMap) resultMap.put(entry.getKey(), entry.getValue());
                            });
                    Objects.<Map<String,String>>requireNonNullElse(LogsUtil.getLoggers(prefix), Map.of())
                            .entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> log.info("  {} {}", String.format("%-5s", entry.getValue()), entry.getKey()));
                    return resultMap;
                } else {
                    String logLevel = LogsUtil.getLogLevel(loggerName);
                    log.info("LOG-LEVEL for: {} --> {}", loggerName, logLevel);
                    return logLevel;
                }
            }
            default -> {
                log.warn("LogLevelICommandExecutor: Invalid sub-command: {}", command);
            }
        }
        return CommandProcessor.COMMAND_STRING_INVALID;
    }
}
