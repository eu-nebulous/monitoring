/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokerclient.properties;

import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "brokerclient")
@Slf4j
public class BrokerClientProperties {
    private String brokerName = "broker";
    private String brokerUrl = "tcp://localhost:61616";
    private String brokerUrlProperties;
    private int managementConnectorPort = -1;
    private boolean preserveConnection;

    private Ssl ssl = new Ssl();

    private String brokerUsername;
    @ToString.Exclude
    private String brokerPassword;

    private long retryResetThreshold = Duration.ofMinutes(5).toMillis();
    private long retryInitDelay = 100L;
    private long retryMaxDelay = Duration.ofMinutes(1).toMillis();
    private double retryBackoffFactor = 2;
    private long retryMaxRetries = -1L;
    private long stopWaitTimeout = -1L;

    @Data
    public static class Ssl {
        private boolean clientAuthRequired;
        private String truststoreFile;
        private String truststoreType;
        @ToString.Exclude
        private String truststorePassword;
        private String keystoreFile;
        private String keystoreType;
        @ToString.Exclude
        private String keystorePassword;
    }

    public BrokerClientProperties() {
    }

    public BrokerClientProperties(java.util.Properties p) {
        brokerName = p.getProperty("brokerclient.broker-name", "broker");
        brokerUrl = p.getProperty("brokerclient.broker-url", "tcp://localhost:61616");
        brokerUrlProperties = p.getProperty("brokerclient.broker-url-properties", "");
        managementConnectorPort = Integer.parseInt(p.getProperty("brokerclient.connector-port", "-1"));
        preserveConnection = Boolean.parseBoolean(p.getProperty("brokerclient.preserve-connection", "false"));

        ssl = new Ssl();
        ssl.truststoreFile = p.getProperty("brokerclient.ssl.truststore.file", "");
        ssl.truststoreType = p.getProperty("brokerclient.ssl.truststore.type", "");
        ssl.truststorePassword = p.getProperty("brokerclient.ssl.truststore.password", "");
        ssl.keystoreFile = p.getProperty("brokerclient.ssl.keystore.file", "");
        ssl.keystoreType = p.getProperty("brokerclient.ssl.keystore.type", "");
        ssl.keystorePassword = p.getProperty("brokerclient.ssl.keystore.password", "");
        ssl.clientAuthRequired = Boolean.parseBoolean(p.getProperty("brokerclient.ssl.client-auth.required", "false"));

        brokerUsername = p.getProperty("brokerclient.broker-username", "");
        brokerPassword = p.getProperty("brokerclient.broker-password", "");

        brokerUrlProperties = brokerUrlProperties.replace("${brokerclient.ssl.client-auth.required}", Boolean.toString(ssl.clientAuthRequired));

        retryResetThreshold = getLongProperty(p, "brokerclient.retry-reset-threshold", this.retryResetThreshold);
        retryInitDelay = getLongProperty(p, "brokerclient.retry-init-delay", this.retryInitDelay);
        retryMaxDelay = getLongProperty(p, "brokerclient.retry-max-delay", this.retryMaxDelay);
        retryBackoffFactor = getDoubleProperty(p, "brokerclient.retry-backoff-factor", this.retryBackoffFactor);
        retryMaxRetries = getLongProperty(p, "brokerclient.retry-max-retries", this.retryMaxRetries);
        stopWaitTimeout = getLongProperty(p, "brokerclient.stop-wait-timeout", this.stopWaitTimeout);
    }

    long getLongProperty(java.util.Properties p, String propName, long defaultValue) {
        return Long.parseLong(p.getProperty(propName, Long.toString(defaultValue)));
    }

    double getDoubleProperty(java.util.Properties p, String propName, double defaultValue) {
        return Double.parseDouble(p.getProperty(propName, Double.toString(defaultValue)));
    }
}
