/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.recovery;

import gr.iccs.imu.ems.common.client.SshClientProperties;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.util.EventBus;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EMS client Self-Healing
 */
@Slf4j
@Component
public class EmsClientRecoveryTask<P extends SshClientProperties> extends VmNodeRecoveryTask<P> {
    @Getter
    private final List<RECOVERY_COMMAND> recoveryCommands = List.of(
            new RECOVERY_COMMAND("Initial wait...",
                    "pwd", 0, 0),
            new RECOVERY_COMMAND("Sending baguette client kill command...",
                    "${BAGUETTE_CLIENT_BASE_DIR}/bin/kill.sh", 0, 2000),
            new RECOVERY_COMMAND("Sending baguette client start command...",
                    "${BAGUETTE_CLIENT_BASE_DIR}/bin/run.sh", 0, 10000)
    );

    public EmsClientRecoveryTask(@NonNull EventBus<String, Object, Object> eventBus, @NonNull PasswordUtil passwordUtil, @NonNull TaskScheduler taskScheduler, @NonNull CollectorContext<P> collectorContext, @NonNull SelfHealingProperties selfHealingProperties) {
        super(eventBus, passwordUtil, taskScheduler, selfHealingProperties, collectorContext);
    }

    public void runNodeRecovery(RecoveryContext recoveryContext) throws Exception {
        String emsRecoveryFile = selfHealingProperties.getRecovery().getFile().get("baguette");
        log.debug("runNodeRecovery: file={}", emsRecoveryFile);
        if (StringUtils.isNotBlank(emsRecoveryFile))
            runNodeRecovery(emsRecoveryFile, recoveryContext);
        else
            runNodeRecovery(recoveryCommands, recoveryContext);
    }
}
