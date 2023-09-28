/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install;

import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsSet;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Client installation task
 */
@Data
@Builder
public class ClientInstallationTask {
    private final String id;
    private final String nodeId;
    private final String requestId;
    private final String name;
    private final String os;
    private final String address;
    private final String type;
    private final String provider;
    private final SshConfig ssh;
    private final NodeRegistryEntry nodeRegistryEntry;
    private final List<InstructionsSet> instructionSets;
    private final TranslationContext translationContext;
    private boolean nodeMustBeInRegistry = true;
}
