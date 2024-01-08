/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.recovery;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Self-Healing task
 */
public interface RecoveryTask {
    Map getNodeInfo();
    void setNodeInfo(Map nodeInfo);

    List<RECOVERY_COMMAND> getRecoveryCommands();

    void runNodeRecovery(RecoveryContext context) throws Exception;

    void runNodeRecovery(List<RECOVERY_COMMAND> recoveryCommandsList, RecoveryContext context) throws Exception;

    default void runNodeRecovery(String recoveryCommandsFile, RecoveryContext context) throws Exception {
        try (FileReader reader = new FileReader(Paths.get(recoveryCommandsFile).toFile())) {
            Type listType = new TypeToken<List<RECOVERY_COMMAND>>(){}.getType();
            List<RECOVERY_COMMAND> recoveryCommandsList = new Gson().fromJson(reader, listType);
            runNodeRecovery(recoveryCommandsList, context);
        }
    }
}
