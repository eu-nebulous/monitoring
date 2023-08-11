/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.recovery;

import gr.iccs.imu.ems.util.EventBus;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public abstract class AbstractRecoveryTask implements RecoveryTask {
    @NonNull protected final EventBus<String,Object,Object> eventBus;
    @NonNull protected final PasswordUtil passwordUtil;
    @NonNull protected final TaskScheduler taskScheduler;
    @NonNull protected final SelfHealingProperties selfHealingProperties;

    @NonNull
    @Getter @Setter
    protected Map nodeInfo = Collections.emptyMap();

    public abstract List<RECOVERY_COMMAND> getRecoveryCommands();
    public abstract void runNodeRecovery(RecoveryContext recoveryContext) throws Exception;
    public abstract void runNodeRecovery(List<RECOVERY_COMMAND> recoveryCommands, RecoveryContext recoveryContext) throws Exception;

    protected void waitFor(long millis, String description) {
        if (millis>0) {
            log.warn("##############  Waiting for {}ms after {}...", millis, description);
            try { Thread.sleep(millis); } catch (InterruptedException ignored) { }
        }
    }

    protected void redirectOutput(InputStream in, String id, AtomicBoolean closed, String connectionClosedMessageFormatter, String exceptionMessageFormatter) {
        taskScheduler.schedule(() -> {
                    try {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                            while (reader.ready()) {
                                log.info(" {}> {}", id, reader.readLine());
                            }
                        }
                    } catch (IOException e) {
                        if (closed.get()) {
                            log.info(connectionClosedMessageFormatter, id);
                        } else {
                            log.error(exceptionMessageFormatter, id, e);
                        }
                    }
                },
                Instant.now()
        );
    }

    protected String prepareCommandString(String command, RecoveryContext recoveryContext) {
        log.trace("AbstractRecoveryTask.prepareCommandString: BEGIN: {}", command);
        command = StringSubstitutor.replaceSystemProperties(command);
        log.trace("AbstractRecoveryTask.prepareCommandString: AFTER replaceSystemProperties: {}", command);
        Map<String, String> variablesMap = recoveryContext.getVariablesMap();
        log.trace("AbstractRecoveryTask.prepareCommandString: VARS: {}", variablesMap);
        command = StringSubstitutor.replace(command, variablesMap);
        log.trace("AbstractRecoveryTask.prepareCommandString: END: {}", command);
        return command;
    }
}
