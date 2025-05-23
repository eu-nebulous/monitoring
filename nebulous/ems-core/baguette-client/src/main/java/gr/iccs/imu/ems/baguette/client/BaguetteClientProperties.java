/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client;

import gr.iccs.imu.ems.common.client.SshClientProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Configuration
@ConfigurationProperties
@PropertySource(value = {
		"file:${EMS_CONFIG_DIR}/ems-client.yml",
		"file:${EMS_CONFIG_DIR}/ems-client.properties",
		"file:${EMS_CONFIG_DIR}/baguette-client.yml",
		"file:${EMS_CONFIG_DIR}/baguette-client.properties"
}, ignoreResourceNotFound = true)
public class BaguetteClientProperties extends SshClientProperties {
	private String baseDir;

	private boolean connectionRetryEnabled = true;
	private long connectionRetryDelay = 10 * 1000L;
	private int connectionRetryLimit = -1;

	private boolean exitCommandAllowed = false;
	private int killDelay = 5;

	private List<Class<? extends IClientCollector>> collectorClasses;
	private Map<String,List<Map<String,Object>>> collectorConfigurations;

	private boolean collectFromLocal = true;
	private boolean autodetectCollectFromLocal = true;		// Checks if 'POD_NAME' env var is set to turn-off collection from localhost
	private List<String> collectFromAdditionalAddresses = new LinkedList<>();

	private String debugFakeIpAddress;

	private long sendStatisticsDelay = 10000L;
	private String sendStatisticsDestination;
}
