/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.properties;

import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "beacon")
public class TopicBeaconProperties implements InitializingBean {
    private boolean enabled = true;
    @Min(0) private long initialDelay = 60000;
    @Min(1) private long delay = 60000;
    @Min(1) private long rate = 60000;
    private boolean useDelay = true;

    private Set<String> heartbeatTopics = new HashSet<>();
    private Set<String> thresholdTopics = new HashSet<>();
    private Set<String> instanceTopics = new HashSet<>();
    private Set<String> predictionTopics = new HashSet<>();
    @Min(1) private long predictionRate = 60000;
    @Min(1) private long predictionMinAllowedRate = 1;
    @Min(1) private long predictionMaxAllowedRate = 365*24*3600*1000L;
    private Set<String> sloViolationDetectorTopics = new HashSet<>();

    @Override
    public void afterPropertiesSet() {
        log.debug("TopicBeaconProperties: {}", this);
    }
}
