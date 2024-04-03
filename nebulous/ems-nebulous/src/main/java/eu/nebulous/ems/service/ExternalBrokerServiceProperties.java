/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import static gr.iccs.imu.ems.util.EmsConstant.EMS_PROPERTIES_PREFIX;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EMS_PROPERTIES_PREFIX + "external")
public class ExternalBrokerServiceProperties implements InitializingBean {
    public final static String NEBULOUS_TOPIC_PREFIX = "eu.nebulouscloud.";
    public final static String COMPONENT_NAME = "monitoring";
    public final static String BASE_TOPIC_PREFIX = NEBULOUS_TOPIC_PREFIX + COMPONENT_NAME + ".";

    private boolean enabled = true;
    private String brokerAddress;
    private int brokerPort;
    private String brokerUsername;
    @ToString.Exclude
    private String brokerPassword;
    private int healthTimeout = 60;
    private int retryDelay = 10;

    // Used to automatically detect external broker address and port
    public enum COLLECTION_MODE { CONFIG, K8S }
    private COLLECTION_MODE connectionInfoCollectionMode = COLLECTION_MODE.CONFIG;
    private String brokerServiceName = "nebulous-activemq";
    private String brokerServiceNamespace = "default";

    private String applicationIdPropertyName = "application";

    private String commandsTopic         = BASE_TOPIC_PREFIX + "commands";
    private String commandsResponseTopic = BASE_TOPIC_PREFIX + "commands.reply";

    private String metricsTopicPrefix    = BASE_TOPIC_PREFIX + "realtime.";
    private String combinedSloTopic      = BASE_TOPIC_PREFIX + "slo.severity_value";
    private String solutionsTopic        = NEBULOUS_TOPIC_PREFIX + "optimiser.solver.solution";

    private String emsBootTopic          = NEBULOUS_TOPIC_PREFIX + "ems.boot";
    private String emsBootResponseTopic  = NEBULOUS_TOPIC_PREFIX + "ems.boot.reply";

    @Override
    public void afterPropertiesSet() {
        log.debug("ExternalBrokerServiceProperties: {}", this);
    }
}