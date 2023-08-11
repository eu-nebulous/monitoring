/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.collector.netdata;

import gr.iccs.imu.ems.common.collector.AbstractEndpointCollectorProperties;
import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "collector.netdata")
public class NetdataCollectorProperties extends AbstractEndpointCollectorProperties {
    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("NetdataCollectorProperties: {}", this);
    }
}
