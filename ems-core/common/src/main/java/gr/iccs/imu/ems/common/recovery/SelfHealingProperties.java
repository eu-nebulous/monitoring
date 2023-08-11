/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.recovery;

import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "self.healing")
public class SelfHealingProperties implements InitializingBean {
	private boolean enabled = true;
	private Recovery recovery = new Recovery();

	@Override
	public void afterPropertiesSet() throws Exception {
		log.debug("SelfHealingProperties: {}", this);
	}

	@Data
	public static class Recovery {
		private long delay = 1000;
		private long retryDelay = 60000;
		private int maxRetries = 3;

		private Map<String,String> file = new HashMap<>();
	}
}
