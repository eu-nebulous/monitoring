/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.boot;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "boot")
public class EmsBootProperties implements InitializingBean {
    public final static String NEBULOUS_TOPIC_PREFIX = "eu.nebulouscloud.";
    public static final String COMPONENT_NAME = "monitoring";

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("{}", this);
    }

    private boolean enabled;

    private long processorPeriod = 500; // millis
    private String applicationIdPropertyName = "application";
    private String modelsDir = "models";
    private String modelsIndexFile = "models/index.json";

    private String dslTopic              = NEBULOUS_TOPIC_PREFIX + "ui.dsl.generic";
    private String optimiserMetricsTopic = NEBULOUS_TOPIC_PREFIX + "optimiser.controller.metric_list";
    private String modelsTopic           = NEBULOUS_TOPIC_PREFIX + "ui.dsl.metric_model";
    private String modelsResponseTopic   = NEBULOUS_TOPIC_PREFIX + "ui.dsl.metric_model.reply";
    private String emsBootTopic          = NEBULOUS_TOPIC_PREFIX + "ems.boot";
    private String emsBootResponseTopic  = NEBULOUS_TOPIC_PREFIX + "ems.boot.reply";

    private boolean validateModels       = true;
    private boolean storeInvalidModels   = true;
    private boolean reportInvalidModels  = true;
}
