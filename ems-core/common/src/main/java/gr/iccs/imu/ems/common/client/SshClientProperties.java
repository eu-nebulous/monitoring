/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.client;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties
@ToString(exclude = "serverPassword")
public class SshClientProperties {
	private long connectTimeout = 60000;
	private long authTimeout = 60000;
	private long heartbeatInterval = 60000;
	private long heartbeatReplyWait = heartbeatInterval;
	private long execTimeout = 120000;
	private long retryPeriod = 60000;

	private String clientId;

	private String serverAddress;
	private int serverPort = 22;
	private String serverPubkey;
	private String serverPubkeyFingerprint;
	private String serverPubkeyAlgorithm;
	private String serverPubkeyFormat;

	private String serverUsername;
	private String serverPassword;
}
