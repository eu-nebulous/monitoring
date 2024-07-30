/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep;

import com.google.gson.Gson;
import gr.iccs.imu.ems.brokercep.broker.BrokerConfig;
import gr.iccs.imu.ems.brokercep.cep.CepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import gr.iccs.imu.ems.util.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.BrokerView;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.jms.*;
import javax.management.ObjectName;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor(onConstructor = @__({@Autowired}))
public class BrokerCepService {
    private BrokerCepProperties properties;
    private BrokerConfig brokerConfig;
    @Getter
    private BrokerService brokerService;
    private PasswordUtil passwordUtil;

    @Getter
    private BrokerCepConsumer brokerCepBridge;
    @Getter
    private CepService cepService;
    private EventCache eventCache;

    private Gson gson;

    public BrokerCepProperties getBrokerCepProperties() {
        return properties;
    }

    public synchronized void clearState() {
        log.debug("BrokerCepService.clearState(): Clearing Broker-CEP state...");

        // Clear CEP service state
        cepService.clearStatements();
        cepService.clearEventTypes();
        cepService.clearConstants();
        cepService.clearFunctionDefinitions();

        // Clear Broker service state
        try {
            BrokerView bv = brokerService.getAdminView();
            ObjectName[] queues = bv.getQueues();
            //ObjectName[] queueSubscribers = bv.getQueueSubscribers();
            ObjectName[] topics = bv.getTopics();
            //ObjectName[] topicSubscribers = bv.getTopicSubscribers();
            for (ObjectName q : queues) {
                String name = q.getCanonicalName();
                bv.removeQueue(name);
                log.debug("BrokerCepService.clearState(): Queue removed: {}", name);
            }
            for (ObjectName t : topics) {
                String name = t.getCanonicalName();
                bv.removeTopic(name);
                log.debug("BrokerCepService.clearState(): Topic removed: {}", name);
            }

            log.debug("BrokerCepService.clearState(): Broker-CEP state cleared");
        } catch (Exception ex) {
            log.error("BrokerCepService.clearState(): Failed to clear Broker state: ", ex);
        }

        // Reset Broker-CEP Consumer connection and session
        brokerCepBridge.initialize();
        log.debug("BrokerCepService.clearState(): Broker-CEP Consumer has been re-initialized");
    }

    public synchronized void addEventTypes(Set<String> eventTypeNames, String[] eventPropertyNames, Class[] eventPropertyTypes) {
        log.info("BrokerCepService.addEventTypes(): Adding event types: {}", eventTypeNames);
        eventTypeNames.forEach(name -> addEventType(name, eventPropertyNames, eventPropertyTypes));
        log.debug("BrokerCepService.addEventTypes(): Adding event types: ok");
    }

    public synchronized void addEventTypes(Set<String> eventTypeNames, Class eventType) {
        log.info("BrokerCepService.addEventTypes(): Adding event types: {}", eventTypeNames);
        eventTypeNames.forEach(name -> addEventType(name, eventType));
        log.debug("BrokerCepService.addEventTypes(): Adding event types: ok");
    }

    public synchronized void addEventType(String eventTypeName, String[] eventPropertyNames, Class[] eventPropertyTypes) {
        // Add a new queue/topic in ActiveMQ (broker) named after 'eventTypeName'
        //brokerCepBridge.addQueue(eventTypeName);
        brokerCepBridge.addTopic(eventTypeName);

        // Register a new event type in Esper (cep engine)
        cepService.addEventType(eventTypeName, eventPropertyNames, eventPropertyTypes);
        log.debug("BrokerCepService.addEventType(): New event type registered: {}", eventTypeName);
    }

    public synchronized void addEventType(String eventTypeName, Class eventType) {
        // Add a new queue/topic in ActiveMQ (broker) named after 'eventTypeName'
        //brokerCepBridge.addQueue(eventTypeName);
        brokerCepBridge.addTopic(eventTypeName);

        // Register a new event type in Esper (cep engine)
        cepService.addEventType(eventTypeName, eventType);
        log.debug("BrokerCepService.addEventType(): New event type registered: {}", eventTypeName);
    }

    public void setConstant(String constName, double constValue) {
        log.debug("BrokerCepService.setConstant(): Add/Set constant: name={}, value={}", constName, constValue);
        cepService.setConstant(constName, constValue);
    }

    public void setConstants(Map<String, Double> constants) {
        log.info("BrokerCepService.setConstants(): Add/Set constants: {}", constants);
        cepService.setConstants(constants);
        log.debug("BrokerCepService.setConstants(): Add/Set constants: ok");
    }

    public void addFunctionDefinitions(Set<FunctionDefinition> definitions) {
        log.info("BrokerCepService.addFunctionDefinitions(): Adding function definitions: {}", definitions);
        definitions.forEach(this::addFunctionDefinition);
        log.debug("BrokerCepService.addFunctionDefinitions(): Adding function definitions: ok");
    }

    public void addFunctionDefinition(FunctionDefinition definition) {
        log.info("BrokerCepService.addFunction(): New function definition registered: {}", definition);
        cepService.addFunctionDefinition(definition);
    }

    public boolean destinationExists(String destination) {
        return brokerCepBridge.containsDestination(destination);
    }

    public synchronized void publishEvent(String connectionString, String destinationName, Map<String, Object> eventMap) throws JMSException {
        if (properties.isBypassLocalBroker() && _publishLocalEvent(connectionString, destinationName, new EventMap(eventMap)))
            return;
        _publishEvent(connectionString, destinationName, EventMap.toEventMap(eventMap), true);
    }
	
    public synchronized void publishEvent(String connectionString, String username, String password, String destinationName, Map<String, Object> eventMap) throws JMSException {
        if (properties.isBypassLocalBroker() && _publishLocalEvent(connectionString, destinationName, new EventMap(eventMap)))
            return;
        _publishEvent(connectionString, username, password, destinationName, new EventMap(eventMap), true);
    }

    public synchronized void publishSerializable(String connectionString, String destinationName, Serializable event, boolean convertToJson) throws JMSException {
        if (properties.isBypassLocalBroker() && _publishLocalEvent(connectionString, destinationName, event))
            return;
        _publishEvent(connectionString, destinationName, event, convertToJson);
    }

    public synchronized void publishSerializable(String connectionString, String username, String password, String destinationName, Serializable event, boolean convertToJson) throws JMSException {
        if (properties.isBypassLocalBroker() && _publishLocalEvent(connectionString, destinationName, event))
            return;
        _publishEvent(connectionString, username, password, destinationName, event, convertToJson);
    }

    // When destination is the local broker then hand event to (local) CEP engine, bypassing local broker
    private final static java.util.regex.Pattern urlPattern = java.util.regex.Pattern.compile("^([a-z]+://[a-zA-Z0-9_\\.\\-]+:[0-9]+)([/#\\?].*)?$");

    private synchronized boolean _publishLocalEvent(String connectionString, String destinationName, Serializable event) throws JMSException {
        java.util.regex.Matcher matcher = urlPattern.matcher(connectionString);
        String connBrokerUrl = matcher.matches() ? matcher.group(1) : connectionString;
        log.debug("BrokerCepService._publishLocalEvent(): Check if event is published to the local broker: local-broker-url={}, connection-broker-url={}, connection={}, destination={}, payload={}",
                properties.getBrokerUrl(), connBrokerUrl, connectionString, destinationName, event);
        if (!connBrokerUrl.equals(properties.getBrokerUrl())) return false;

        Class<? extends Serializable> eventClass = event.getClass();
        log.debug("BrokerCepService._publishLocalEvent(): It is local event. Skipping publish through broker: connection={}, destination={}, payload-class={}, payload={}",
                connectionString, destinationName, eventClass.getName(), event);
        if (String.class.isAssignableFrom(eventClass)) {
            log.debug("BrokerCepService._publishLocalEvent(): String event...");
            cepService.handleEvent((String) event, destinationName);
        } else if (Map.class.isAssignableFrom(eventClass)) {
            log.debug("BrokerCepService._publishLocalEvent(): Map event...");
            cepService.handleEvent(StrUtil.castToMapStringObject(event), destinationName);
        } else {
            log.debug("BrokerCepService._publishLocalEvent(): Object event...");
            cepService.handleEvent(event);
        }
        return true;
    }

    private synchronized void _publishEvent(String connectionString, String destinationName, Serializable event, boolean convertToJson) throws JMSException {
        // Get username/password for local broker service
        String username = null;
        String password = null;
        if (_isLocalBrokerUrl(connectionString)) {
            username = brokerConfig.getBrokerLocalAdminUsername();
            password = brokerConfig.getBrokerLocalAdminPassword();
            log.debug("BrokerCepService._publishEvent(): Using LOCAL BROKER credentials: {} / {}",
                    username, passwordUtil.encodePassword(password));
        }
        _publishEvent(connectionString, username, password, destinationName, event, convertToJson);
    }

    private synchronized void _publishEvent(String connectionString, String username, String password, String destinationName, Serializable event, boolean convertToJson) throws JMSException {
        // Clone connection factory
        if (connectionString == null) connectionString = properties.getBrokerUrlForConsumer();
        ConnectionFactory connectionFactory = brokerConfig.getConnectionFactoryFor(connectionString);

        // Create a Connection
        log.trace("BrokerCepService._publishEvent(): Connection info: conn-string={}, username={}, password={}",
                connectionString, username, passwordUtil.encodePassword(password));
        Connection connection = StringUtils.isBlank(username)
                ? connectionFactory.createConnection()
                : connectionFactory.createConnection(username, password);
        connection.start();

        // Publish event
        _publishEvent(connection, destinationName, event, convertToJson);

        // Clean up
        connection.close();
    }

    private synchronized void _publishEvent(Connection connection, String destinationName, Serializable event, boolean convertToJson) throws JMSException {
        log.trace("BrokerCepService._publishEvent(): Connection given: {}", connection);

        // Create a Session
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Publish event
        _publishEvent(session, destinationName, event, convertToJson);

        // Clean up
        session.close();
    }

    private synchronized void _publishEvent(Session session, String destinationName, Serializable event, boolean convertToJson) throws JMSException {
        log.trace("BrokerCepService._publishEvent(): Session: {}", session);

        // Create the destination (Topic or Queue)
        log.trace("BrokerCepService._publishEvent(): Destination info: name={}", destinationName);
        //Destination destination = session.createQueue( destinationName );
        Destination destination = session.createTopic(destinationName);

        // Create a MessageProducer from the Session to the Topic or Queue
        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        // Create a message
        //ObjectMessage message = session.createObjectMessage(event);
        String payload = convertToJson ? gson.toJson(event) : (event!=null ? event.toString() : null);
        log.trace("BrokerCepService.publishEvent(): Message payload: topic={}, convert-to-json={}, payload={}", destination, convertToJson, payload);
        TextMessage message = session.createTextMessage(payload);

        // Set message properties
        addEventPropertiesToMessage(event, message);

        // Tell the producer to send the message
        long hash = message.hashCode();
        //log.info("BrokerCepService.publishEvent(): Sending message: connection={}, username={}, destination={}, hash={}, payload={}", connectionString, username, destinationName, hash, event);
        log.trace("BrokerCepService.publishEvent(): Sending message: destination={}, hash={}, payload={}", destinationName, hash, event);
        producer.send(message);
        //log.info("BrokerCepService.publishEvent(): Message sent: connection={}, username={}, destination={}, hash={}, payload={}", connectionString, username, destinationName, hash, event);
        log.debug("BrokerCepService.publishEvent(): Message sent: destination={}, hash={}, payload={}", destinationName, hash, event);
    }

    private void addEventPropertiesToMessage(Serializable event, Message message) {
        if (event instanceof EventMap map) {
            Map<String, Object> eventProperties = map.getEventProperties();
            if (eventProperties!=null) {
                eventProperties.forEach((pName,pValue)->{
                    try {
                        message.setStringProperty(pName, pValue!=null ? pValue.toString() : null);
                    } catch (JMSException e) {
                        log.warn("BrokerCepService.publishEvent(): Exception while setting event property. Skipping it: name={}, value={}", pName, pValue);
                        log.debug("BrokerCepService.publishEvent(): Exception while setting event property. Skipping it: name={}, value={}, EXCEPTION:\n", pName, pValue, e);
                    }
                });
            }
        }
    }

    private String getAddressFromBrokerUrl(String url) {
        return StringUtils.substringBetween(url, "://",":");
    }

    private boolean _isLocalBrokerUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            log.debug("BrokerCepService._isLocalBrokerUrl(): url={}, is-local=true", url);
            return true;
        }
        log.trace("BrokerCepService._isLocalBrokerUrl(): url={}", url);
        try {
            String address = getAddressFromBrokerUrl(url);
            boolean isLocal = NetUtil.isLocalAddress(address);
            log.debug("BrokerCepService._isLocalBrokerUrl(): url={}, address={}, is-local={}", url, address, isLocal);
            return isLocal;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void setBrokerCredentials(String username, String password) {
        brokerConfig.setBrokerUsername(username);
        brokerConfig.setBrokerPassword(password);
        log.info("BrokerCepService.setBrokerCredentials(): Broker credentials set: username={}, password={}",
                username, passwordUtil.encodePassword(password));
    }

    public String getBrokerUsername() {
        return brokerConfig.getBrokerLocalUserUsername();
    }

    public String getBrokerPassword() {
        return brokerConfig.getBrokerLocalUserPassword();
    }

    public KeyStore getBrokerTruststore() {
        return brokerConfig.getBrokerTruststore();
    }

    public String getBrokerCertificate() {
        return brokerConfig.getBrokerCertificate();
    }

    public Certificate addOrReplaceCertificateInTruststore(String alias, String certPem) throws Exception {
        log.trace("BrokerCepService.addOrReplaceCertificateInTruststore(): BEGIN: alias={}, cert-PEM=\n{}", alias, certPem);
        if (StringUtils.isNotEmpty(certPem)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream inputStream = new ByteArrayInputStream(certPem.getBytes(Charset.forName("UTF-8")))) {
                Certificate cert = cf.generateCertificate(inputStream);
                log.debug("BrokerCepService.addOrReplaceCertificateInTruststore(): X509 Certificate: {}",
                        ((X509Certificate) cert).getSubjectX500Principal().getName());
                return addOrReplaceCertificateInTruststore(alias, cert);
            }
        } else {
            log.debug("BrokerCepService.addOrReplaceCertificateInTruststore(): PEM certificate is empty. Returning 'null'");
            return null;
        }
    }

    public synchronized Certificate addOrReplaceCertificateInTruststore(String alias, Certificate cert) throws Exception {
        log.trace("BrokerCepService.addOrReplaceCertificateInTruststore(): BEGIN: alias={}, cert=\n{}", alias, cert);
        brokerConfig.getBrokerTruststore().setCertificateEntry(alias, cert);
        brokerConfig.writeTruststore();
        log.debug("BrokerCepService.addOrReplaceCertificateInTruststore(): Certificate added with alias: {}", alias);
        log.debug("BrokerCepService.addOrReplaceCertificateInTruststore(): New Truststore certificates: {}",
                KeystoreUtil.getCertificateAliases(brokerConfig.getBrokerTruststore()));
        return cert;
    }

    public synchronized void deleteCertificateFromTruststore(String alias) throws KeyStoreException {
        log.trace("BrokerCepService.deleteCertificateFromTruststore(): BEGIN: alias={}", alias);
        brokerConfig.getBrokerTruststore().deleteEntry(alias);
        log.debug("BrokerCepService.deleteCertificateFromTruststore(): Deleted certificate with alias: {}", alias);
        log.debug("BrokerCepService.addOrReplaceCertificateInTruststore(): New Truststore certificates: {}",
                KeystoreUtil.getCertificateAliases(brokerConfig.getBrokerTruststore()));
    }

    public Map<String,Object> getBrokerCepStatistics() {
        Map<String,Object> bcepStats = new HashMap<>();
        bcepStats.put("count-event-local-publish-success", BrokerCepStatementSubscriber.getLocalPublishSuccessCounter());
        bcepStats.put("count-event-local-publish-failure", BrokerCepStatementSubscriber.getLocalPublishFailureCounter());
        bcepStats.put("count-event-forwards-success", BrokerCepStatementSubscriber.getForwardSuccessCounter());
        bcepStats.put("count-event-forwards-failure", BrokerCepStatementSubscriber.getForwardFailureCounter());
        bcepStats.put("count-total-events", BrokerCepConsumer.getEventCounter());
        bcepStats.put("count-total-events-text", BrokerCepConsumer.getTextEventCounter());
        bcepStats.put("count-total-events-object", BrokerCepConsumer.getObjectEventCounter());
        bcepStats.put("count-total-events-other", BrokerCepConsumer.getOtherEventCounter());
        bcepStats.put("count-total-events-failures", BrokerCepConsumer.getEventFailuresCounter());
        bcepStats.put("count-cep-events", CepService.getEventCounter());

        bcepStats.put("latest-events", eventCache.asList());

        return bcepStats;
    }

    public void clearBrokerCepStatistics() {
        BrokerCepStatementSubscriber.clearCounters();
        BrokerCepConsumer.clearCounters();
        CepService.clearCounters();
        log.debug("BrokerCepService.clearBrokerCepStatistics(): broker-CEP statistics cleared");
    }
}