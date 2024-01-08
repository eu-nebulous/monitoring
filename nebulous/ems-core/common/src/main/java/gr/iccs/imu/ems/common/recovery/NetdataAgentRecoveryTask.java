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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Remote Netdata agent Self-Healing using an SSH connection
 */
@Slf4j
@Component
public class NetdataAgentRecoveryTask<P extends SshClientProperties> extends VmNodeRecoveryTask<P> {
    @Getter
    private final List<RECOVERY_COMMAND> recoveryCommands = Collections.unmodifiableList(Arrays.asList(
            new RECOVERY_COMMAND("Initial wait...",
                    "pwd", 0, 0),
            new RECOVERY_COMMAND("Sending Netdata agent kill command...",
                    "sudo sh -c  'ps -U netdata -o \"pid\" --no-headers | xargs kill -9' ", 0, 2000),
            new RECOVERY_COMMAND("Sending Netdata agent start command...",
                    "sudo netdata", 0, 10000)
    ));

    public NetdataAgentRecoveryTask(@NonNull EventBus<String, Object, Object> eventBus, @NonNull PasswordUtil passwordUtil, @NonNull TaskScheduler taskScheduler, @NonNull CollectorContext<P> collectorContext, @NonNull SelfHealingProperties selfHealingProperties) {
        super(eventBus, passwordUtil, taskScheduler, selfHealingProperties, collectorContext);
    }

    public void runNodeRecovery(RecoveryContext recoveryContext) throws Exception {
        String netdataRecoveryFile = selfHealingProperties.getRecovery().getFile().get("netdata");
        log.debug("runNodeRecovery: file={}", netdataRecoveryFile);
        if (StringUtils.isNotBlank(netdataRecoveryFile))
            runNodeRecovery(netdataRecoveryFile, recoveryContext);
        else
            runNodeRecovery(recoveryCommands, recoveryContext);
    }
}
