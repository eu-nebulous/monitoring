/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.selfhealing;

import gr.iccs.imu.ems.common.recovery.SelfHealingProperties;
import gr.iccs.imu.ems.common.selfhealing.SelfHealingManager;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@ToString(callSuper=true)
@EqualsAndHashCode(callSuper = true)
@Configuration
public class ServerSelfHealingProperties extends SelfHealingProperties implements InitializingBean {
	private SelfHealingManager.MODE mode = SelfHealingManager.MODE.INCLUDED;

	@Override
	public void afterPropertiesSet() throws Exception {
		log.debug("ServerSelfHealingProperties: {}", this);
	}
}
