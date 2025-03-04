/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static gr.iccs.imu.ems.util.EmsConstant.EMS_PROPERTIES_PREFIX;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = EMS_PROPERTIES_PREFIX + "boot-initializer")
public class EmsBootInitializerProperties implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("{}", this);
    }

    private boolean enabled = true;
    private Duration initialWait = Duration.ofSeconds(1);
    private Duration retryPeriod = Duration.ofSeconds(30);
}
