/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.helper;

import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsSet;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.translate.TranslationContext;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface InstallationHelper {
    Optional<List<String>> getInstallationInstructionsForOs(NodeRegistryEntry entry) throws IOException;

    List<InstructionsSet> prepareInstallationInstructionsForOs(NodeRegistryEntry entry) throws IOException;
    List<InstructionsSet> prepareInstallationInstructionsForWin(NodeRegistryEntry entry);
    List<InstructionsSet> prepareInstallationInstructionsForLinux(NodeRegistryEntry entry) throws IOException;

    List<InstructionsSet> prepareUninstallInstructionsForOs(NodeRegistryEntry entry) throws IOException;
    List<InstructionsSet> prepareUninstallInstructionsForWin(NodeRegistryEntry entry);
    List<InstructionsSet> prepareUninstallInstructionsForLinux(NodeRegistryEntry entry) throws IOException;

    default ClientInstallationTask createClientInstallationTask(NodeRegistryEntry entry) throws Exception {
        return createClientInstallationTask(entry, null);
    }
    ClientInstallationTask createClientInstallationTask(NodeRegistryEntry entry, TranslationContext translationContext) throws Exception;
    ClientInstallationTask createClientReinstallTask(NodeRegistryEntry entry, TranslationContext translationContext) throws Exception;
    ClientInstallationTask createClientUninstallTask(NodeRegistryEntry entry, TranslationContext translationContext) throws Exception;
}
