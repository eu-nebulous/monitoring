/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.collector.generator;

import gr.iccs.imu.ems.common.collector.AbstractEndpointCollectorProperties;
import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Data
@EqualsAndHashCode(callSuper=true)
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "collector.generator")
public class GeneratorCollectorProperties extends AbstractEndpointCollectorProperties {
    private boolean enable;
//    private long delay = 60 * 1000;
//    private long initialDelay = 10 * 1000;
    private List<String> destinations;
    private boolean createTopic;

    public enum GENERATOR_TYPE { count, random }
    public enum GENERATOR_VALUE_TYPE { Int, Double }
    public enum GENERATOR_OVERFLOW { round, reset, stop, reverse }

    private GENERATOR_TYPE type = GENERATOR_TYPE.random;
    private GENERATOR_VALUE_TYPE valueType = GENERATOR_VALUE_TYPE.Double;
    private double limitsLower = 0;
    private double limitsUpper = 100;
    private double limitsStart = limitsLower;
    private double limitsStep = 1;
    private GENERATOR_OVERFLOW limitsOnOverflow = GENERATOR_OVERFLOW.round;
    private long limitsMaxCount = -1;

    @Override
    public void afterPropertiesSet() throws Exception {
        setUrl("127.0.0.1");
        setUrlOfNodesWithoutClient("%s");
        log.debug("GeneratorCollectorProperties: {}", this);
    }
}
