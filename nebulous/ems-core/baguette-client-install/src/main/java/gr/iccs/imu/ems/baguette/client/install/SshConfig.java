/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * SSH connection information
 */
@Data
@Builder
@ToString(exclude = {"password", "privateKey"})
public class SshConfig {
    private String host;
    @Builder.Default
    private int port = 22;
    private String username;
    private String password;
    private String privateKey;
    private String fingerprint;
}
