/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker;

import gr.iccs.imu.ems.brokercep.broker.interceptor.AbstractMessageInterceptor;
import gr.iccs.imu.ems.brokercep.event.EventRecorder;
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import gr.iccs.imu.ems.util.KeystoreUtil;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.SslBrokerService;
import org.apache.activemq.broker.inteceptor.MessageInterceptorRegistry;
import org.apache.activemq.broker.jmx.ManagementContext;
import org.apache.activemq.broker.region.policy.ConstantPendingMessageLimitStrategy;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.activemq.security.AuthenticationUser;
import org.apache.activemq.security.SimpleAuthenticationPlugin;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.apache.activemq.usage.MemoryUsage;
import org.apache.activemq.usage.StoreUsage;
import org.apache.activemq.usage.SystemUsage;
import org.apache.activemq.usage.TempUsage;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import jakarta.jms.ConnectionFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.*;
import java.util.stream.Collectors;

//import org.apache.activemq.security.JaasAuthenticationPlugin;


@Slf4j
@Service
@EnableJms
@Configuration
@RequiredArgsConstructor
public class BrokerConfig implements InitializingBean {

    private final static int LOCAL_ADMIN_INDEX = 0;
    private final static int LOCAL_USER_INDEX = 1;
    private final static String LOCAL_ADMIN_PREFIX = "admin-local-";
    private final static String LOCAL_USER_PREFIX = "user-local-";
    private final static int USERNAME_RANDOM_PART_LENGTH = 10;
    private final static int PASSWORD_LENGTH = 20;

    private final BrokerCepProperties properties;
    private final PasswordUtil passwordUtil;
    private final ApplicationContext applicationContext;

    private SimpleAuthenticationPlugin brokerAuthenticationPlugin;
    private SimpleBrokerAuthorizationPlugin brokerAuthorizationPlugin;
    private ArrayList<AuthenticationUser> userList;
    private String brokerLocalAdmin;
    private String brokerLocalAdminPassword;
    private String brokerUsername;
    private String brokerPassword;
    private String brokerCert;

    private KeyStore truststore;

    private final HashMap<String, ConnectionFactory> connectionFactoryCache = new HashMap<>();

    private final TaskScheduler scheduler;
    @Getter
    private EventRecorder eventRecorder;

    @Override
    public void afterPropertiesSet() throws Exception {
        _initializeSecurity();
        _initializeEventRecorder();
    }

    protected synchronized void _initializeSecurity() throws Exception {
        log.debug("BrokerConfig._initializeSecurity(): Initializing broker security: initialize-authentication={}, initialize-authorization={}",
                properties.isAuthenticationEnabled(), properties.isAuthorizationEnabled());

        // initialize authentication
        if (properties.isAuthenticationEnabled()) {
            userList = new ArrayList<>();

            // initialize local admin credentials
            brokerLocalAdmin = LOCAL_ADMIN_PREFIX + RandomStringUtils.randomAlphanumeric(USERNAME_RANDOM_PART_LENGTH);
            brokerLocalAdminPassword = RandomStringUtils.randomAlphanumeric(PASSWORD_LENGTH);
            userList.add(new AuthenticationUser(brokerLocalAdmin, brokerLocalAdminPassword, SimpleBrokerAuthorizationPlugin.ADMIN_GROUP));
            log.debug("BrokerConfig._initializeSecurity(): Initialized local admin: {} / {}",
                    brokerLocalAdmin, passwordUtil.encodePassword(brokerLocalAdminPassword));

            // initialize broker user credentials
            brokerUsername = LOCAL_USER_PREFIX+ RandomStringUtils.randomAlphanumeric(USERNAME_RANDOM_PART_LENGTH);
            brokerPassword = RandomStringUtils.randomAlphanumeric(PASSWORD_LENGTH);
            userList.add(new AuthenticationUser(brokerUsername, brokerPassword, SimpleBrokerAuthorizationPlugin.RO_USER_GROUP));
            log.debug("BrokerConfig._initializeSecurity(): Initialized broker user: {} / {}",
                    brokerUsername, passwordUtil.encodePassword(brokerPassword));

            // initialize additional user credentials from configuration
            if (StringUtils.isNotBlank(properties.getAdditionalBrokerCredentials())) {
                for (String extraUserCred : properties.getAdditionalBrokerCredentials().split(",")) {
                    String[] cred = extraUserCred.split("/", 2);
                    String username = cred[0].trim();
                    String password = cred.length > 1 ? cred[1].trim() : "";
                    userList.add(new AuthenticationUser(username, password, SimpleBrokerAuthorizationPlugin.RW_USER_GROUP));
                    log.debug("BrokerConfig._initializeSecurity(): Initialized additional broker user from configuration: {} / {}",
                            username, passwordUtil.encodePassword(password));
                }
            }

            // initialize Broker authentication plugin
            SimpleAuthenticationPlugin sap = new SimpleAuthenticationPlugin();        //new JaasAuthenticationPlugin()
            sap.setAnonymousAccessAllowed(false);
            sap.setUsers(userList);
            brokerAuthenticationPlugin = sap;

            if (log.isDebugEnabled()) {
                log.debug("BrokerConfig._initializeSecurity(): Initialized broker authentication plugin: anonymous-access={}, user-list={}",
                        sap.isAnonymousAccessAllowed(), sap.getUserPasswords().keySet()
                );
            }
        }

        // initialize authorization (requires authentication being enabled)
        if (properties.isAuthorizationEnabled()) {
            if (properties.isAuthenticationEnabled()) {
                // initialize Broker authorization plugin
                brokerAuthorizationPlugin = new SimpleBrokerAuthorizationPlugin();
                log.debug("BrokerConfig._initializeSecurity(): Initialized broker authorization plugin");
            } else {
                log.error("BrokerConfig._initializeSecurity(): Authorization will not be configured because authentication is not enabled");
            }
        }

        // Initialize Key pair and Certificate for SSL broker
        if (getBrokerUrl().startsWith("ssl")) {
            log.debug("BrokerConfig._initializeSecurity(): Initializing Broker key pair and certificate...");
            initializeKeyPairAndCert();
            log.debug("BrokerConfig._initializeSecurity(): Broker key pair and certificate initialization has been completed");
        } else {
            log.debug("BrokerConfig._initializeSecurity(): Broker key pair and certificate NOT initialized");
        }
    }

    private void initializeKeyPairAndCert() throws Exception {
        log.debug("BrokerConfig.initializeKeyAndCert(): BrokerCepProperties: {}", properties);
        log.debug("BrokerConfig.initializeKeyAndCert(): Initializing keystore, truststore and certificate for Broker-SSL...");
        KeystoreUtil.initializeKeystoresAndCertificate(properties.getSsl(), passwordUtil);

        log.trace("BrokerConfig.initializeKeyAndCert(): Retrieving certificate for Broker-SSL: file={}, type={}, password={}, alias={}...",
                properties.getSsl().getKeystoreFile(), properties.getSsl().getKeystoreType(),
                passwordUtil.encodePassword(properties.getSsl().getKeystorePassword()),
                properties.getSsl().getKeyEntryName());
        log.trace("BrokerConfig.initializeKeyAndCert(): Retrieving certificate for Broker-SSL...");
        this.brokerCert = KeystoreUtil
                .getKeystore(properties.getSsl().getKeystoreFile(), properties.getSsl().getKeystoreType(), properties.getSsl().getKeystorePassword())
                .passwordUtil(passwordUtil)
                .getEntryCertificateAsPEM(properties.getSsl().getKeyEntryName());
        log.trace("BrokerConfig.initializeKeyAndCert(): Retrieved certificate for Broker-SSL: file={}, type={}, password={}, alias={}, cert=\n{}",
                properties.getSsl().getKeystoreFile(), properties.getSsl().getKeystoreType(),
                passwordUtil.encodePassword(properties.getSsl().getKeystorePassword()),
                properties.getSsl().getKeyEntryName(), this.brokerCert);
        log.debug("BrokerConfig.initializeKeyAndCert(): Initializing keystore, truststore and certificate for Broker-SSL... done");
    }

    private void _initializeEventRecorder() throws IOException {
        // clear previous event recorder (if any)
        if (eventRecorder!=null && !eventRecorder.isClosed())
            eventRecorder.close();

        // create new event recorder
        if (properties.getEventRecorder()!=null) {
            if (properties.getEventRecorder().isEnabled()) {
                eventRecorder = new EventRecorder(properties.getEventRecorder(), scheduler);
                eventRecorder.startRecording();
            }
        }
    }

    public String getBrokerName() {
        log.trace("BrokerConfig.getBrokerName(): broker-name: {}", properties.getBrokerName());
        return properties.getBrokerName();
    }

    public String getBrokerUrl() {
        log.trace("BrokerConfig.getBrokerUrl(): broker-url: {}", properties.getBrokerUrl());
        return properties.getBrokerUrl();
    }

    public String getBrokerCertificate() {
        log.trace("BrokerConfig.getBrokerCertificate(): Broker certificate (PEM):\n{}", brokerCert);
        return brokerCert;
    }

    public KeyStore getBrokerTruststore() { return truststore; }

    public String getBrokerLocalAdminUsername() {
        return brokerLocalAdmin;
    }

    public String getBrokerLocalAdminPassword() {
        return brokerLocalAdminPassword;
    }

    public String getBrokerLocalUserUsername() {
        return brokerUsername;
    }

    public String getBrokerLocalUserPassword() {
        return brokerPassword;
    }

    public void setBrokerUsername(String s) {
        if (userList != null) {
            brokerUsername = s;
            userList.get(LOCAL_USER_INDEX).setUsername(s);     // 'userList' contains at least 2 items or is null (see '_initializeSecurity()' method)
            brokerAuthenticationPlugin.setUsers(userList);
            log.debug("BrokerConfig.setBrokerUsername(): username={}", s);
        } else
            log.debug("BrokerConfig.setBrokerUsername(): Username not set");
    }

    public void setBrokerPassword(String password) {
        if (userList != null) {
            brokerPassword = password;
            userList.get(LOCAL_USER_INDEX).setPassword(password);
            brokerAuthenticationPlugin.setUsers(userList);
            log.debug("BrokerConfig.setBrokerPassword(): password={}", passwordUtil.encodePassword(password));
        } else
            log.debug("BrokerConfig.setBrokerPassword(): Password not set");
    }

    public BrokerPlugin getBrokerAuthenticationPlugin() {
        return brokerAuthenticationPlugin;
    }

    public BrokerPlugin getBrokerAuthorizationPlugin() {
        return brokerAuthorizationPlugin;
    }

    /**
     * Creates an embedded JMS server
     */
    @Bean
    public BrokerService createBrokerService() throws Exception {

        // Create new broker service instance
        String brokerUrl = getBrokerUrl();
        log.debug("BrokerConfig: Creating new Broker Service instance: url={}", brokerUrl);

        SslBrokerService brokerService = new SslBrokerService();;
        brokerService.setBrokerName(getBrokerName());

        // Initialize keystore and truststore for broker SSL connectors
        KeyManager[] keystore = null;
        TrustManager[] truststore = null;
        if (secureConnectorsExist()) {
            keystore = readKeystore();
            truststore = readTruststore();
        }

        // Start broker connectors
        if (properties.getBrokerUrlList()!=null) {
            int i = 1;
            for (String url : properties.getBrokerUrlList()) {
                if (StringUtils.isNotBlank(url)) {
                    String num = (i==1 ? "st" : (i==2 ? "nd" : "rd"));
                    log.debug("BrokerConfig: {}{} connector: {}", i++, num, url);
                    if (isSecureUrl(url))
                        // Add an SSL broker connector
                        brokerService.addSslConnector(url, keystore, truststore, null);
                    else
                        // Add a non-SSL broker connector
                        brokerService.addConnector(url);
                }
            }
        }

        // Set authentication and authorization plugins
        List<BrokerPlugin> plugins = new ArrayList<>();
        if (getBrokerAuthenticationPlugin()!=null) plugins.add(getBrokerAuthenticationPlugin());
        if (getBrokerAuthorizationPlugin()!=null) plugins.add(getBrokerAuthorizationPlugin());
        if (!plugins.isEmpty()) {
            brokerService.setPlugins(plugins.toArray(new BrokerPlugin[0]));
        }

        // Configure broker service instance
        log.debug("BrokerConfig: Broker configuration: persistence={}, use-jmx={}, advisory-support={}, use-shutdown-hook={}",
                properties.isBrokerPersistenceEnabled(), properties.isBrokerUsingJmx(), properties.isBrokerAdvisorySupportEnabled(), properties.isBrokerUsingShutdownHook());
        brokerService.setPersistent(properties.isBrokerPersistenceEnabled());
        brokerService.setUseJmx(properties.isBrokerUsingJmx());
        brokerService.setUseShutdownHook(properties.isBrokerUsingShutdownHook());
        brokerService.setAdvisorySupport(properties.isBrokerAdvisorySupportEnabled());

        brokerService.setPopulateJMSXUserID(properties.isBrokerPopulateJmsxUserId());
        brokerService.setEnableStatistics(properties.isBrokerEnableStatistics());

        // Configure persistent storage
        if (properties.isBrokerPersistenceEnabled()) {
            String dir = StringUtils.firstNonBlank(properties.getBrokerPersistenceDirectory(), "data/activemq");
            log.debug("BrokerConfig.createBrokerService(): Setting persistence adapter (Kaha): directory: {}", dir);
            KahaDBPersistenceAdapter kahaDBPersistenceAdapter = new KahaDBPersistenceAdapter();
            kahaDBPersistenceAdapter.setDirectory(Paths.get(dir).toFile());
            brokerService.setPersistenceAdapter(kahaDBPersistenceAdapter);
        }

        // Change the JMX connector port
        if (properties.getManagementConnectorPort() > 0) {
            if (brokerService.getManagementContext() != null) {
                log.debug("BrokerConfig.createBrokerService(): Setting connector port to: {}", properties.getManagementConnectorPort());
                brokerService.getManagementContext().setConnectorPort(properties.getManagementConnectorPort());
            }
        }

        // Print Management Context information
        try {
            log.debug("BrokerConfig.createBrokerService(): Management Context (MC) settings:");
            ManagementContext mc = brokerService.getManagementContext();
            log.debug("    MC: BrokerName: {}", mc.getBrokerName());
            log.debug("    MC: ConnectorHost: {}", mc.getConnectorHost());
            log.debug("    MC: ConnectorPath: {}", mc.getConnectorPath());
            log.debug("    MC: Environment: {}", mc.getEnvironment());
            log.debug("    MC: JmxDomainName: {}", mc.getJmxDomainName());
            log.debug("    MC: RmiServerPort: {}", mc.getRmiServerPort());
            log.debug("    MC: SuppressMBean: {}", mc.getSuppressMBean());
            log.debug("    MC: AllowRemoteAddressInMBeanNames: {}", mc.isAllowRemoteAddressInMBeanNames());
            log.debug("    MC: ConnectorStarted: {}", mc.isConnectorStarted());
            log.debug("    MC: CreateConnector: {}", mc.isCreateConnector());
            log.debug("    MC: CreateMBeanServer: {}", mc.isCreateMBeanServer());
            log.debug("    MC: FindTigerMbeanServer: {}", mc.isFindTigerMbeanServer());
            log.debug("    MC: UseMBeanServer: {}", mc.isUseMBeanServer());

            log.debug("    MC->MBS: DefaultDomain: {}", mc.getMBeanServer().getDefaultDomain());
            log.debug("    MC->MBS: Domains: {}", (Object[])mc.getMBeanServer().getDomains());
            log.debug("    MC->MBS: MBeanCount: {}", mc.getMBeanServer().getMBeanCount());
        } catch (Exception ex) {
            log.error("    MC: EXCEPTION: ", ex);
        }

        // Set system usage limits
        final SystemUsage systemUsage = new SystemUsage();

        // Set memory limit in order not to use too much memory
        int memHeapPercent = properties.getUsage().getMemory().getJvmHeapPercentage();
        long memSize = properties.getUsage().getMemory().getSize();
        if (memHeapPercent > 0 || memSize > 0) {
            final MemoryUsage memoryUsage = new MemoryUsage();
            if (memHeapPercent > 0) {
                memoryUsage.setPercentOfJvmHeap(memHeapPercent);
                log.debug("BrokerConfig: Limiting Broker Service instance memory usage to {}% of JVM heap size", memHeapPercent);
            } else {
                memoryUsage.setUsage(memSize);
                log.debug("BrokerConfig: Limiting Broker Service instance memory usage to {} bytes", memSize);
            }
            systemUsage.setMemoryUsage(memoryUsage);
        }

        // Set disk storage limit in order not to use too much disk space
        int percentLimit = properties.getUsage().getStorage().getPercentLimit();
        long storeSize = properties.getUsage().getStorage().getSize();
        if (percentLimit > 0 ||  storeSize > 0) {
            final StoreUsage storeUsage = new StoreUsage();
            if (percentLimit > 0) {
                storeUsage.setPercentLimit(percentLimit);
                log.debug("BrokerConfig: Limiting Broker Service instance store usage to {}% of disk size", percentLimit);
            } else {
                storeUsage.setLimit(storeSize);
                log.debug("BrokerConfig: Limiting Broker Service instance store usage to {} bytes", storeSize);
            }
            systemUsage.setStoreUsage(storeUsage);
        }

        // Set temp storage limit in order not to use too much temp disk space
        int tempPercentLimit = properties.getUsage().getTemp().getPercentLimit();
        long tempSize = properties.getUsage().getTemp().getSize();
        if (tempPercentLimit > 0 ||  tempSize > 0) {
            final TempUsage tempUsage = new TempUsage();
            if (tempPercentLimit > 0) {
                tempUsage.setPercentLimit(tempPercentLimit);
                log.debug("BrokerConfig: Limiting Broker Service instance temp usage to {}% of disk size", tempPercentLimit);
            } else {
                tempUsage.setLimit(tempSize);
                log.debug("BrokerConfig: Limiting Broker Service instance temp usage to {} bytes", tempSize);
            }
            systemUsage.setTempUsage(tempUsage);
        }

        // Set system usage
        brokerService.setSystemUsage(systemUsage);

        // Configure flow control via destination policy
        if (properties.isDestinationPolicyEnabled()) {
            PolicyMap policyMap = new PolicyMap();
            PolicyEntry policyEntry = new PolicyEntry();
            String destination = StringUtils.firstNonBlank(properties.getDestinationPolicyDestination(), ">");
            //policyEntry.setQueue(destination);  // '>': Apply to all queues
            policyEntry.setTopic(destination);  // '>': Apply to all topics
            policyEntry.setProducerFlowControl(properties.isProducerFlowControlEnabled());
            if (properties.getDestinationPolicyMemLimit() > 0) {
                policyEntry.setMemoryLimit(properties.getDestinationPolicyMemLimit());
            }
            if (properties.getPendingMessageLimitStrategyLimit() > 0) {
                ConstantPendingMessageLimitStrategy strategy = new ConstantPendingMessageLimitStrategy();
                strategy.setLimit(properties.getPendingMessageLimitStrategyLimit());
                policyEntry.setPendingMessageLimitStrategy(strategy);
            }
            policyMap.setDefaultEntry(policyEntry);
            //policyMap.put(policyEntry.getDestination(), policyEntry);
            brokerService.setDestinationPolicy(policyMap);
        }

        // start broker service instance
        brokerService.start();

        // register broker service interceptors
        registerMessageInterceptors(brokerService);

        return brokerService;
    }

    private void registerMessageInterceptors(BrokerService brokerService) {
        // get message interceptor registry
        final MessageInterceptorRegistry registry = MessageInterceptorRegistry.getInstance().get(brokerService);    // or ...get(BrokerRegistry.getInstance().findFirst());
        log.trace("BrokerConfig: Message interceptor registry: {}", registry);

        if (properties.getMessageInterceptors()==null) {
            log.warn("BrokerConfig: No message interceptors configured");
            return;
        }

        log.debug("BrokerConfig: Message interceptors initializing...");
        List<BrokerCepProperties.MessageInterceptorSpec> interceptorSpecs = properties.getMessageInterceptors()
                .stream()
                .map(c -> (BrokerCepProperties.MessageInterceptorSpec)c)
                .collect(Collectors.toList());
        List<AbstractMessageInterceptor> interceptors = InterceptorHelper.newInstance()
                .initializeInterceptors(registry, applicationContext,
                        properties.getMessageInterceptorsSpecs(), interceptorSpecs);
        log.debug("BrokerConfig: Message interceptors initialized");

        // register interceptors
        log.debug("BrokerConfig: Registering message interceptors...");
        interceptors.forEach(i -> {
            String destinationPattern = ((BrokerCepProperties.MessageInterceptorConfig) i.getInterceptorSpec()).getDestination();
            registry.addMessageInterceptorForTopic(destinationPattern, i);
            log.debug("BrokerConfig: - Registered message interceptor with spec.: {}", i.getInterceptorSpec());
        });
        log.debug("BrokerConfig: Registering message interceptors... done");
    }

    private boolean isSecureUrl(String url) {
        int p = url.indexOf(":");
        if (p<=0) return false;
        String scheme = url.substring(0, p);
        return scheme.startsWith("ssl") || scheme.contains("+ssl") || scheme.startsWith("https:");
    }

    private boolean secureConnectorsExist() {
        if (properties.getBrokerUrlList()!=null) {
            for (String url : properties.getBrokerUrlList())
                if (isSecureUrl(url.trim())) return true;
        }
        return false;
    }

    private KeyManager[] readKeystore() throws Exception {
        final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        final KeyStore keystore = KeyStore.getInstance(properties.getSsl().getKeystoreType());

        //final Resource keystoreResource = new ClassPathResource( properties.getKeystoreFile() );
        final FileSystemResource keystoreResource = new FileSystemResource(properties.getSsl().getKeystoreFile());
        keystore.load(keystoreResource.getInputStream(), properties.getSsl().getKeystorePassword().toCharArray());
        keyManagerFactory.init(keystore, properties.getSsl().getKeystorePassword().toCharArray());
        final KeyManager[] keystoreManagers = keyManagerFactory.getKeyManagers();
        return keystoreManagers;
    }

    private TrustManager[] readTruststore() throws Exception {
        this.truststore = KeyStore.getInstance(properties.getSsl().getTruststoreType());

        //final Resource truststoreResource = new ClassPathResource( properties.getTruststoreFile() );
        final FileSystemResource truststoreResource = new FileSystemResource(properties.getSsl().getTruststoreFile());
        this.truststore.load(truststoreResource.getInputStream(), properties.getSsl().getTruststorePassword().toCharArray());
        final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(this.truststore);
        final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        return trustManagers;
    }

    public void writeTruststore() throws Exception {
        //final Resource truststoreResource = new ClassPathResource( properties.getTruststoreFile() );
        final FileSystemResource truststoreResource = new FileSystemResource(properties.getSsl().getTruststoreFile());
        this.truststore.store(truststoreResource.getOutputStream(), properties.getSsl().getTruststorePassword().toCharArray());
    }

    /**
     * Creates a new connection factory
     */
    public ConnectionFactory connectionFactory() {
        return connectionFactory(null);
    }

    public ConnectionFactory connectionFactory(String brokerUrl) {
        if (brokerUrl==null) brokerUrl = properties.getBrokerUrlForClients();

        // Create connection factory based on Broker URL scheme
        final ActiveMQConnectionFactory connectionFactory;
        if (brokerUrl.startsWith("ssl")) {
            log.debug("BrokerConfig: Creating new SSL connection factory instance: url={}", brokerUrl);
            final ActiveMQSslConnectionFactory sslConnectionFactory = new ActiveMQSslConnectionFactory(brokerUrl);
            try {
                sslConnectionFactory.setTrustStore(properties.getSsl().getTruststoreFile());
                sslConnectionFactory.setTrustStoreType(properties.getSsl().getTruststoreType());
                sslConnectionFactory.setTrustStorePassword(properties.getSsl().getTruststorePassword());
                sslConnectionFactory.setKeyStore(properties.getSsl().getKeystoreFile());
                sslConnectionFactory.setKeyStoreType(properties.getSsl().getKeystoreType());
                sslConnectionFactory.setKeyStorePassword(properties.getSsl().getKeystorePassword());
                //sslConnectionFactory.setKeyStoreKeyPassword( properties.getSsl()........ );

                connectionFactory = sslConnectionFactory;
            } catch (final Exception theException) {
                throw new Error(theException);
            }
        } else {
            log.debug("BrokerConfig: Creating new non-SSL connection factory instance: url={}", brokerUrl);
            connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        }

        // Set credentials, if using local broker URL
		if (brokerUrl.equals(properties.getBrokerUrlForClients()) && getBrokerLocalUserUsername()!=null) {
			connectionFactory.setUserName(getBrokerLocalUserUsername());
			connectionFactory.setPassword(getBrokerLocalUserPassword());
		}

        // Other connection factory settings
        //connectionFactory.setSendTimeout(....5000L);
        //connectionFactory.setTrustedPackages(Arrays.asList("gr.iccs.imu.ems"));
        connectionFactory.setTrustAllPackages(true);
        connectionFactory.setWatchTopicAdvisories(true);

        // Make pooled connection factory
        PooledConnectionFactory pooledConnectionFactory = new PooledConnectionFactory(connectionFactory);
        pooledConnectionFactory.setMaxConnections(64);
        log.trace("BrokerConfig: New connection factory created: {}", pooledConnectionFactory);

        return pooledConnectionFactory;
    }

    public ConnectionFactory getConnectionFactoryFor(String connectionString) {
        return connectionFactoryCache
                .computeIfAbsent(connectionString, this::connectionFactory);
    }

    public ConnectionFactory getConnectionFactoryForConsumer() {
        String connStr;
        if (StringUtils.isNotBlank(properties.getBrokerUrlForConsumer())) {
            log.debug("BrokerConfig.getConnectionFactoryForConsumer(): Broker URL for Broker-CEP consumer instance: {}", properties.getBrokerUrlForConsumer());
            connStr = properties.getBrokerUrlForConsumer();
        } else {
            log.debug("BrokerConfig.getConnectionFactoryForConsumer(): Default broker URL will be used for Broker-CEP consumer instance: {}", properties.getBrokerUrlForClients());
            connStr = null;
        }
        return connectionFactoryCache
                .computeIfAbsent(connStr, this::connectionFactory);
    }
}