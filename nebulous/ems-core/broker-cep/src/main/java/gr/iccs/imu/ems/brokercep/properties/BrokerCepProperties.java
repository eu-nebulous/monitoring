/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.properties;

import gr.iccs.imu.ems.brokercep.EventCache;
import gr.iccs.imu.ems.brokercep.event.EventRecorder;
import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.KeystoreAndCertificateProperties;
import gr.iccs.imu.ems.util.NetUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.PropertySource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "brokercep")
//@PropertySource("file:${EMS_CONFIG_DIR}/gr.iccs.imu.ems.brokercep.properties")
public class BrokerCepProperties implements InitializingBean {
    public void afterPropertiesSet() {
        log.debug("BrokerCepProperties: {}", this);
    }

    private String brokerName = "broker";

    // Broker connector URLs
    private List<String> brokerUrl = Collections.singletonList("ssl://0.0.0.0:61616");
    public String getBrokerUrl() { return brokerUrl.get(0); }
    public List<String> getBrokerUrlList() { return brokerUrl; }

    private String brokerUrlForConsumer = "ssl://127.0.0.1:61616";
    private String brokerUrlForClients = "ssl://"+ NetUtil.getPublicIpAddress()+":61616";

    private int managementConnectorPort = -1;
    private boolean bypassLocalBroker;
    private long eventForwarderLoopDelay = 100L;

    // brokercep.ssl.** settings
    @NestedConfigurationProperty
    private KeystoreAndCertificateProperties ssl;

    private boolean authenticationEnabled;
    @ToString.Exclude
    private String additionalBrokerCredentials;
    private boolean authorizationEnabled;

    private boolean brokerPersistenceEnabled;
    private String brokerPersistenceDirectory;
    private boolean brokerUsingJmx;
    private boolean brokerAdvisorySupportEnabled;
    private boolean brokerUsingShutdownHook;

    private boolean brokerEnableStatistics;
    private boolean brokerPopulateJmsxUserId;

    private boolean enableAdvisoryWatcher = true;
    private int advisoryWatcherInitRetryDelay = 5;   // in seconds

    private List<MessageInterceptorConfig> messageInterceptors;
    private Map<String,MessageInterceptorSpec> messageInterceptorsSpecs = new HashMap<>();

    private List<ForwardDestinationConfig> messageForwardDestinations = Collections.emptyList();

    private int maxEventForwardRetries = -1;
    private long maxEventForwardDuration = -1;

    private Usage usage = new Usage();

    private boolean destinationPolicyEnabled = true;
    private String destinationPolicyDestination = ">";
    private long destinationPolicyMemLimit = -1;  // 50 * 1024 * 1024;  // 50MB per destination
    private int pendingMessageLimitStrategyLimit = -1;
    private boolean producerFlowControlEnabled = true;

    private boolean logBrokerMessages = true;
    private boolean logBrokerMessagesFull = false;

    private EventRecorderProperties eventRecorder = new EventRecorderProperties();

    private boolean eventCacheEnabled = true;
    private int eventCacheSize = EventCache.DEFAULT_EVENT_CACHE_SIZE;

    private boolean statsPrinterEnabled;
    private boolean statsPrinterAsJson = true;
    private boolean statsPrinterAsCsv = false;
    private long statsPrinterInitDelay = 30;
    private long statsPrinterRate = 30;

    @Data
    public static class Usage {
        private Memory memory = new Memory();
        private Storage storage = new Storage();
        private Storage temp = new Storage();
    }
    @Data
    public static class Memory {
        private int jvmHeapPercentage = -1;
        private long size = -1;
    }
    @Data
    public static class Storage {
        private int percentLimit = -1;
        private long size = -1;
    }
    @Data
    public static class MessageInterceptorSpec {
        private String className;
        private List<String> params;
    }
    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class MessageInterceptorConfig extends MessageInterceptorSpec {
        private String destination;
    }
    @Data
    public static class ForwardDestinationConfig {
        private String connectionString;
        private String username;
        @ToString.Exclude
        private String password;
    }

    public enum EVENT_RECORDER_FILTER_MODE { ALL, REGISTERED, ALLOWED }

    @Data
    public static class EventRecorderProperties {
        private boolean enabled;
        private EventRecorder.FORMAT format = EventRecorder.FORMAT.CSV;
        private String file;
        private EVENT_RECORDER_FILTER_MODE filterMode = EVENT_RECORDER_FILTER_MODE.REGISTERED;
        private List<String> allowedDestinations;
    }
}
