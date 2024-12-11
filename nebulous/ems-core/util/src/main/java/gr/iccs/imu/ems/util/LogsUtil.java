/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Logs Utility
 */
@Slf4j
public class LogsUtil {
    public static void setLogLevel(@NonNull Object o, @NonNull String newLevel) {
        setLogLevel(o.getClass().getName(), newLevel);
    }

    public static void setLogLevel(@NonNull String loggerName, String newLevel) {
        if (LoggerFactory.getLogger(loggerName) instanceof ch.qos.logback.classic.Logger logger) {
            logger.setLevel(StringUtils.isNotBlank(newLevel) ? Level.valueOf(newLevel) : null);
            log.info("Changed log level for {} to {} -- Effective level: {}", loggerName, newLevel, logger.getEffectiveLevel());
        }
    }

    public static String getLogLevel(@NonNull Object o) {
        return getLogLevel(o.getClass().getName());
    }

    public static String getLogLevel(@NonNull String loggerName) {
        if (LoggerFactory.getLogger(loggerName) instanceof ch.qos.logback.classic.Logger logger) {
            //return logger.getLevel()!=null ? logger.getLevel().toString() : null;
            return logger.getEffectiveLevel().toString();
        }
        return null;
    }

    public static Map<String, String> getLoggers() {
        return getLoggers(null);
    }

    public static Map<String, String> getLoggers(String prefix) {
        final String prefix1 = (StringUtils.isBlank(prefix)) ? "" : prefix.trim();
        if (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof ch.qos.logback.classic.Logger logger) {
            return logger.getLoggerContext().getLoggerList().stream()
                    .filter(l -> StringUtils.startsWith(l.getName(), prefix1))
                    //.collect(Collectors.toMap(Logger::getName, l -> Objects.requireNonNullElse(l.getLevel(), "").toString()));
                    .collect(Collectors.toMap(Logger::getName, l -> l.getEffectiveLevel().toString()));
        }
        return null;
    }
}
