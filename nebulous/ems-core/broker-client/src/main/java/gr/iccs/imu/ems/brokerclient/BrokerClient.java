/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokerclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gr.iccs.imu.ems.brokerclient.event.EventMap;
import gr.iccs.imu.ems.brokerclient.properties.BrokerClientProperties;
import gr.iccs.imu.ems.util.PasswordUtil;
import jakarta.jms.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.activemq.advisory.DestinationSource;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTempQueue;
import org.apache.activemq.command.ActiveMQTempTopic;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.commons.lang3.StringUtils;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Slf4j
@Component
public class BrokerClient {

    @Autowired
    private BrokerClientProperties properties;
    @Autowired
    private PasswordUtil passwordUtil;
    @Autowired
    private TaskScheduler taskScheduler;
    private Connection connection;
    private Session session;
    private HashMap<MessageListener,Set<MessageConsumer>> listeners = new HashMap<>();
    private Gson gson = new GsonBuilder().create();
    private boolean keepRunning;
    private Future<?> receiveEventsWithAutoReconnectFuture;

    private static TaskScheduler defaultTaskScheduler;

    public BrokerClient() {
    }

    public BrokerClient(BrokerClientProperties bcp) {
        properties = bcp;
    }

    public BrokerClient(Properties p) {
        properties = new BrokerClientProperties(p);
    }

    public BrokerClient(PasswordUtil pu) {
        passwordUtil = pu;
    }

    public BrokerClient(BrokerClientProperties bcp, PasswordUtil pu, TaskScheduler ts) {
        properties = bcp;
        passwordUtil = pu;
        taskScheduler = ts;
    }

    public BrokerClient(Properties p, PasswordUtil pu) {
        properties = new BrokerClientProperties(p);
        passwordUtil = pu;
        taskScheduler = getOrCreateTaskScheduler();
    }

    private static synchronized TaskScheduler getOrCreateTaskScheduler() {
        if (defaultTaskScheduler==null) {
            defaultTaskScheduler = new SimpleAsyncTaskScheduler();
        }
        return defaultTaskScheduler;
    }

    // ------------------------------------------------------------------------

    public static BrokerClient newClient() throws java.io.IOException, JMSException {
        log.debug("BrokerClient: Initializing...");

        // get properties file
        String configDir = System.getenv("EMS_CONFIG_DIR");
        if (StringUtils.isBlank(configDir)) configDir = ".";
        log.debug("BrokerClient: config-dir:  {}", configDir);
        String configPropFile = configDir + "/" + "gr.iccs.imu.ems.brokerclient.properties";
        log.debug("BrokerClient: config-file: {}", configPropFile);

        // load properties
        Properties p = new Properties();
        File cfgFile = Paths.get(configPropFile).toFile();
        if (cfgFile.exists() && cfgFile.isFile()) {
            //ClassLoader loader = Thread.currentThread().getContextClassLoader();
            //try (java.io.InputStream in = loader.getClass().getResourceAsStream(configPropFile)) { p.load(in); }
            try (java.io.InputStream in = new java.io.FileInputStream(configPropFile)) {
                log.debug("BrokerClient: Loading config-properties from file: {}", configPropFile);
                p.load(in);
            }
            log.debug("BrokerClient: config-properties: {}", p);
            log.info("BrokerClient: Configuration loaded from file: {}", configPropFile);
        } else {
            log.debug("BrokerClient: Config file not found or is not a file: {}", configPropFile);
            log.debug("BrokerClient: No configuration file found");
        }

        // initialize broker client
        BrokerClient client = new BrokerClient(p, PasswordUtil.getInstance());
        log.debug("BrokerClient: Default Configuration:\n{}", client.properties);

        return client;
    }

    public static BrokerClient newClient(String username, String password) throws java.io.IOException, JMSException {
        BrokerClient client = newClient();
        if (username!=null && password!=null) {
            client.getClientProperties().setBrokerUsername(username);
            client.getClientProperties().setBrokerPassword(password);
        }
        return client;
    }

    // ------------------------------------------------------------------------

    public BrokerClientProperties getClientProperties() {
        checkProperties();
        return properties;
    }

    protected void checkProperties() {
        if (properties==null) {
            //use defaults
            properties = new BrokerClientProperties();
        }
    }

    // ------------------------------------------------------------------------

    public synchronized Set<String> getDestinationNames(String connectionString) throws JMSException {
        // open or reuse connection
        checkProperties();
        boolean _closeConn = false;
        if (session==null) {
            openConnection(connectionString);
            _closeConn = ! properties.isPreserveConnection();
        }

        // Get destinations from Broker
        log.debug("BrokerClient.getDestinationNames(): Getting destinations: connection={}, username={}", connectionString, properties.getBrokerUsername());
        ActiveMQConnection conn = (ActiveMQConnection)connection;
        DestinationSource ds = conn.getDestinationSource();
        Set<ActiveMQQueue> queues = ds.getQueues();
        Set<ActiveMQTopic> topics = ds.getTopics();
        Set<ActiveMQTempQueue> tempQueues = ds.getTemporaryQueues();
        Set<ActiveMQTempTopic> tempTopics = ds.getTemporaryTopics();
        log.debug("BrokerClient.getDestinationNames(): Getting destinations: done");

        // Get destination names
        HashSet<String> destinationNames = new HashSet<>();
        for (ActiveMQQueue q : queues) destinationNames.add("QUEUE "+q.getQueueName());
        for (ActiveMQTopic t : topics) destinationNames.add("TOPIC "+t.getTopicName());
        for (ActiveMQTempQueue tq : tempQueues) destinationNames.add("Temp QUEUE "+tq.getQueueName());
        for (ActiveMQTempTopic tt : tempTopics) destinationNames.add("Temp TOPIC "+tt.getTopicName());

        // close connection
        if (_closeConn) {
            closeConnection();
        }

        return destinationNames;
    }

    // ------------------------------------------------------------------------

    public enum MESSAGE_TYPE { TEXT, OBJECT, BYTES, MAP };

    public synchronized void publishEvent(String connectionString, String destinationName, Map<String, Object> eventMap) throws JMSException {
        _publishEvent(connectionString, destinationName, MESSAGE_TYPE.TEXT, new EventMap(eventMap), null);
    }

    public synchronized void publishEvent(String connectionString, String destinationName, Map<String, Object> eventMap, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, destinationName, MESSAGE_TYPE.TEXT, new EventMap(eventMap), propertiesMap);
    }

    public synchronized void publishEvent(String connectionString, String destinationName, MESSAGE_TYPE messageType, Map<String, Object> eventMap, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, destinationName, messageType, new EventMap(eventMap), propertiesMap);
    }

    public synchronized void publishEvent(String connectionString, String destinationName, String eventContents) throws JMSException {
        _publishEvent(connectionString, destinationName, MESSAGE_TYPE.TEXT, eventContents, null);
    }

    public synchronized void publishEvent(String connectionString, String destinationName, String eventContents, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, destinationName, MESSAGE_TYPE.TEXT, eventContents, propertiesMap);
    }

    public synchronized void publishEvent(String connectionString, String destinationName, MESSAGE_TYPE messageType, String eventContents, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, destinationName, messageType, eventContents, propertiesMap);
    }

    public synchronized void publishEvent(String connectionString, String destinationName, String type, Serializable eventContents, Map<String,String> propertiesMap) throws JMSException {
        MESSAGE_TYPE messageType = StringUtils.isNotBlank(type)
                ? MESSAGE_TYPE.valueOf(type.trim().toUpperCase())
                : MESSAGE_TYPE.TEXT;
        _publishEvent(connectionString, destinationName, messageType, eventContents, propertiesMap);
    }

    public synchronized void publishEventWithCredentials(String connectionString, String username, String password, String destinationName, Map<String, Object> eventMap) throws JMSException {
        _publishEvent(connectionString, username, password, destinationName, MESSAGE_TYPE.TEXT, new EventMap(eventMap), null);
    }

    public synchronized void publishEventWithCredentials(String connectionString, String username, String password, String destinationName, Map<String, Object> eventMap, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, username, password, destinationName, MESSAGE_TYPE.TEXT, new EventMap(eventMap), propertiesMap);
    }

    public synchronized void publishEventWithCredentials(String connectionString, String username, String password, String destinationName, MESSAGE_TYPE messageType, Map<String, Object> eventMap, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, username, password, destinationName, messageType, new EventMap(eventMap), propertiesMap);
    }

    public synchronized void publishEventWithCredentials(String connectionString, String username, String password, String destinationName, String eventContents) throws JMSException {
        _publishEvent(connectionString, username, password, destinationName, MESSAGE_TYPE.TEXT, eventContents, null);
    }

    public synchronized void publishEventWithCredentials(String connectionString, String username, String password, String destinationName, String eventContents, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, username, password, destinationName, MESSAGE_TYPE.TEXT, eventContents, propertiesMap);
    }

    public synchronized void publishEventWithCredentials(String connectionString, String username, String password, String destinationName, MESSAGE_TYPE messageType, String eventContents, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, username, password, destinationName, messageType, eventContents, propertiesMap);
    }

    public synchronized void publishEventWithCredentials(String connectionString, String username, String password, String destinationName, String type, Serializable eventContents, Map<String,String> propertiesMap) throws JMSException {
        MESSAGE_TYPE messageType = StringUtils.isNotBlank(type)
                ? MESSAGE_TYPE.valueOf(type.trim().toUpperCase())
                : MESSAGE_TYPE.TEXT;
        _publishEvent(connectionString, username, password, destinationName, messageType, eventContents, propertiesMap);
    }

    protected synchronized void _publishEvent(String connectionString, String destinationName, MESSAGE_TYPE messageType, Serializable event, Map<String,String> propertiesMap) throws JMSException {
        _publishEvent(connectionString, null, null, destinationName, messageType, event, propertiesMap);
    }

    @SneakyThrows
    protected synchronized void _publishEvent(String connectionString, String username, String password, String destinationName, MESSAGE_TYPE messageType, Serializable event, Map<String,String> propertiesMap) throws JMSException {
        // open or reuse connection
        checkProperties();
        boolean _closeConn = false;
        if (session==null) {
            if (StringUtils.isBlank(username))
                openConnection(connectionString);
            else
                openConnection(connectionString, username, password);
            _closeConn = ! properties.isPreserveConnection();
        }

        // Create the destination (Topic or Queue)
        //Destination destination = session.createQueue( destinationName );
        Destination destination = session.createTopic(destinationName);

        // Create a MessageProducer from the Session to the Topic or Queue
        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        // Create a messages
        String payloadText = null;
        Message message;
        switch (messageType) {
            case MAP:
                if (event instanceof Map map) {
                    final MapMessage mapMsg = session.createMapMessage();
                    for (Object key : map.keySet()) {
                        Object val = map.get(key);
                        String k = key != null ? key.toString() : null;
                        mapMsg.setObject(k, val);
                    }
                    payloadText = gson.toJson(event);
                    message = mapMsg;
                    break;
                } else {
                    log.warn("BrokerClient.publishEvent(): Payload is not a Map: {}", event.getClass().getName());
                    log.warn("BrokerClient.publishEvent(): Will send an Object message");
                    messageType = MESSAGE_TYPE.OBJECT;
                }
            case OBJECT:
                payloadText = (event instanceof Map)
                        ? gson.toJson(event)
                        : event.toString();
                message = session.createObjectMessage(event);
                break;
            case BYTES:
                byte[] bytesArr;
                if (event instanceof byte[] bytes) {
                    bytesArr = bytes;
                } else {
                    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                         ObjectOutputStream out = new ObjectOutputStream(bos))
                    {
                        out.writeObject(event);
                        bytesArr = bos.toByteArray();
                    }
                }
                BytesMessage bytesMsg = session.createBytesMessage();
                bytesMsg.writeBytes(bytesArr);
                payloadText = new String(bytesArr);
                message = bytesMsg;
                break;
            case TEXT:
            default:
                payloadText = event instanceof Map
                        ? gson.toJson(event)
                        : event.toString();
                message = session.createTextMessage(payloadText);
                break;
        }
        log.debug("BrokerClient.publishEvent(): Message payload: payload={}", payloadText);

        if (propertiesMap!=null)
            for (Map.Entry<String,String> e : propertiesMap.entrySet())
                if (StringUtils.isNotBlank(e.getKey()))
                    message.setStringProperty(e.getKey(), e.getValue());

        // Tell the producer to send the message
        long hash = message.hashCode();
        log.debug("BrokerClient.publishEvent(): Sending {} message: connection={}, username={}, destination={}, hash={}, payload={}, properties={}", messageType, connectionString, properties.getBrokerUsername(), destinationName, hash, event, propertiesMap);
        producer.send(message);
        log.debug("BrokerClient.publishEvent(): {} message sent: connection={}, username={}, destination={}, hash={}, payload={}, properties={}", messageType, connectionString, properties.getBrokerUsername(), destinationName, hash, event, propertiesMap);

        // close connection
        if (_closeConn) {
            closeConnection();
        }
    }

    // ------------------------------------------------------------------------

    public enum ON_EXCEPTION { IGNORE, LOG_AND_IGNORE, THROW, LOG_AND_THROW }

    public void subscribe(String connectionString, String destinationNames, MessageListener listener) throws JMSException {
        subscribe(connectionString, destinationNames, listener, ON_EXCEPTION.LOG_AND_IGNORE);
    }

    public void subscribe(String connectionString, String destinationNames, MessageListener listener, ON_EXCEPTION onException) throws JMSException {
        // Create or open connection
        checkProperties();
        if (session==null) {
            openConnection(connectionString);
        }

        // Create the destinations (Topics or Queues)
        subscribeToTopics(destinationNames, listener, onException);
    }

    public void subscribeWithAutoReconnect(String connectionString, String destinationNames, MessageListener listener, ON_EXCEPTION onException, Consumer<Integer> exitCallback) {
        receiveEventsWithAutoReconnectFuture = taskScheduler.schedule(() -> {
            int returnCode = 0;
            try {
                receiveEventsWithAutoReconnect(connectionString, destinationNames, listener, onException);
                log.debug("BrokerClient: receiveEventsWithAutoReconnect returned normally");
            } catch (JMSException e) {
                returnCode = 1;
                log.debug("BrokerClient: receiveEventsWithAutoReconnect threw an exception");
                log.error("BrokerClient: Exception:\n", e);
            }
            if (exitCallback!=null)
                exitCallback.accept(returnCode);
        }, Instant.now());
    }

    public void unsubscribe(MessageListener listener) throws JMSException {
        if (listeners!=null && listener!=null) {
            Set<MessageConsumer> set = listeners.get(listener);
            if (set!=null) {
                for (MessageConsumer consumer : set) {
                    if (consumer != null) {
                        consumer.close();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------

    public void receiveEvents(String connectionString, String destinationNames, MessageListener listener) throws JMSException {
        receiveEvents(connectionString, destinationNames, listener, ON_EXCEPTION.LOG_AND_IGNORE);
    }

    public void receiveEvents(String connectionString, String destinationNames, MessageListener listener, ON_EXCEPTION onException) throws JMSException {
        checkProperties();
        boolean _closeConn = false;
        try {
            // Create or open connection
            if (session==null) {
                openConnection(connectionString);
                _closeConn = ! properties.isPreserveConnection();
            }
        } catch (Exception e) {
            // Clean up
            log.error("BrokerClient: Connection failed...\n", e);
            closeConnection();
            return;
        }

        try {
            // Subscribe to destinations (Topics or Queues)
            subscribeToTopics(destinationNames, listener, onException);

            // Wait for messages
            log.info("BrokerClient: Waiting for messages...");
            keepRunning = true;
            while (keepRunning) {
                try { Thread.sleep(Duration.ofDays(1)); } catch (InterruptedException e) { break; }
            }
            log.debug("BrokerClient: Message wait loop exited...");

        } finally {
            // Clean up
            log.info("BrokerClient: Closing connection...");
            if (_closeConn) {
                closeConnection();
            }
        }
    }

    public void receiveEventsWithAutoReconnect(String connectionString, String destinationNames, MessageListener listener, ON_EXCEPTION onException) throws JMSException {
        long retryResetThreshold = properties.getRetryResetThreshold();
        long initDelay = properties.getRetryInitDelay();
        long maxDelay = properties.getRetryMaxDelay();
        double backoffFactor = properties.getRetryBackoffFactor();
        long lastRestart = 0;
        long retries = 0;
        long delay = initDelay;
        keepRunning = true;
        while (keepRunning) {
            try {
                receiveEvents(connectionString, destinationNames, listener, onException);
            } catch (JMSException e) {
                if (log.isDebugEnabled())
                    log.error("BrokerClient: Error while receiving events: ", e);
                else
                    log.error("BrokerClient: Error while receiving events: {}", e.getMessage());

                if (properties.getRetryMaxRetries()>=0 && retries>=properties.getRetryMaxRetries()) {
                    log.error("BrokerClient: Reached max retries limit");
                    throw e;
                }

                if (System.currentTimeMillis() - lastRestart > retryResetThreshold) {
                    retries = 1;
                    delay = initDelay;
                } else {
                    retries++;
                    delay = (long)(backoffFactor * delay);
                    if (delay>maxDelay) delay = maxDelay;
                }

                log.debug("BrokerClient: Wait for {}ms before retrying", delay);
                try { Thread.sleep(delay); } catch (InterruptedException ignored) { }
                if (keepRunning)
                    log.info("BrokerClient: Reconnecting...: Retry #{}", retries);
                lastRestart = System.currentTimeMillis();
            }
        }
    }

    public void stopRunning(boolean waitToStop) {
        stopRunning(waitToStop, properties.getStopWaitTimeout());
    }

    public void stopRunning(boolean waitToStop, long waitTimeoutMillis) {
        keepRunning = false;
        if (waitToStop && receiveEventsWithAutoReconnectFuture!=null) {
            try {
                receiveEventsWithAutoReconnectFuture.cancel(true);
                if (waitTimeoutMillis>0)
                    receiveEventsWithAutoReconnectFuture.get(waitTimeoutMillis, TimeUnit.MILLISECONDS);
                else
                    receiveEventsWithAutoReconnectFuture.get();
            } catch (InterruptedException e) {
                log.warn("BrokerClient: Interrupted waiting receive-events-task to exit: ", e);
            } catch (ExecutionException e) {
                log.error("BrokerClient: Task execution failed: ", e);
            } catch (TimeoutException e) {
                log.warn("BrokerClient: Timeout waiting receive-events-task to exit: ", e);
            }
        }
    }

    private void subscribeToTopics(String destinationNames, MessageListener listener, ON_EXCEPTION onException) throws JMSException {
        // Prepare message listener
        MessageListener wrapperListener = getWrapperMessageListener(listener, onException);

        // Create the destinations (Topics or Queues)
        List<String> destinationNamesList = Arrays.stream(destinationNames.split("[,;]"))
                .filter(StringUtils::isNotBlank).map(String::trim)
                .toList();
        for (String destinationName : destinationNamesList) {
            destinationName = destinationName.trim();

            log.info("BrokerClient: Subscribing to destination: {}...", destinationName);
            //Destination destination = session.createQueue( destinationName );
            Destination destination = this.session.createTopic(destinationName);

            // Create a MessageConsumers from the Session to the Topics or Queues
            MessageConsumer consumer = this.session.createConsumer(destination);
            consumer.setMessageListener(wrapperListener);
            listeners.computeIfAbsent(listener, ml -> new HashSet<>()).add(consumer);
        }
    }

    private static MessageListener getWrapperMessageListener(MessageListener listener, ON_EXCEPTION onException) {
        boolean logException = onException==ON_EXCEPTION.LOG_AND_IGNORE || onException==ON_EXCEPTION.LOG_AND_THROW;
        boolean throwException = onException==ON_EXCEPTION.THROW || onException==ON_EXCEPTION.LOG_AND_THROW;
        return (message) -> {
            try {
                listener.onMessage(message);
            } catch (Exception e) {
                if (logException) {
                    if (log.isDebugEnabled())
                        log.debug("BrokerClient: Exception in callback listener: {}: {}\nevent: {}\nException: ",
                                e.getClass().getName(), e.getMessage(), message, e);
                    else
                        log.warn("BrokerClient: Exception in callback listener: {}: {}\nevent: {}",
                                e.getClass().getName(), e.getMessage(), message);
                }
                if (throwException)
                    throw e;
            }
        };
    }

    // ------------------------------------------------------------------------

    public ConnectionFactory createConnectionFactory(String brokerUrl, String username, String password) {
        if (StringUtils.startsWithIgnoreCase(brokerUrl, "amqp:"))
            return createQpidConnectionFactory(brokerUrl, username, password);
        return createActiveMQConnectionFactory(brokerUrl, username, password);
    }

    private ConnectionFactory createQpidConnectionFactory(String brokerUrl, String username, String password) {
        return new JmsConnectionFactory(username, password, brokerUrl);
    }

    private ActiveMQConnectionFactory createActiveMQConnectionFactory(String brokerUrl, String username, String password) {
        // Create connection factory based on Broker URL scheme
        checkProperties();
        final ActiveMQConnectionFactory connectionFactory;
        //String brokerUrl = properties.getBrokerUrl();
        if (brokerUrl.startsWith("ssl")) {
            log.debug("BrokerClient.createConnectionFactory(): Creating new SSL connection factory instance: url={}", brokerUrl);
            final ActiveMQSslConnectionFactory sslConnectionFactory = new ActiveMQSslConnectionFactory(brokerUrl);
            try {
                sslConnectionFactory.setTrustStore(properties.getSsl().getTruststoreFile());
                sslConnectionFactory.setTrustStoreType(properties.getSsl().getTruststoreType());
                sslConnectionFactory.setTrustStorePassword(properties.getSsl().getTruststorePassword());
                sslConnectionFactory.setKeyStore(properties.getSsl().getKeystoreFile());
                sslConnectionFactory.setKeyStoreType(properties.getSsl().getKeystoreType());
                sslConnectionFactory.setKeyStorePassword(properties.getSsl().getKeystorePassword());
                //sslConnectionFactory.setKeyStoreKeyPassword( properties........ );

                sslConnectionFactory.setUserName(username);
                sslConnectionFactory.setPassword(password);

                connectionFactory = sslConnectionFactory;
            } catch (final Exception theException) {
                throw new Error(theException);
            }
        } else {
            log.debug("BrokerClient.createConnectionFactory(): Creating new non-SSL connection factory instance: url={}", brokerUrl);
            connectionFactory = new ActiveMQConnectionFactory(username, password, brokerUrl);
        }

        // Other connection factory settings
        //connectionFactory.setSendTimeout(....5000L);
        //connectionFactory.setTrustedPackages(Arrays.asList("gr.iccs.imu.ems"));
        connectionFactory.setTrustAllPackages(true);
        connectionFactory.setWatchTopicAdvisories(true);

        return connectionFactory;
    }

    // ------------------------------------------------------------------------

    public synchronized void openConnection() throws JMSException {
        checkProperties();
        openConnection(properties.getBrokerUrl(), null, null);
    }

    public synchronized void openConnection(String connectionString) throws JMSException {
        openConnection(connectionString, null, null);
    }

    public synchronized void openConnection(String connectionString, String username, String password) throws JMSException {
        openConnection(connectionString, username, password, properties.isPreserveConnection());
    }

    public synchronized void openConnection(String connectionString, String username, String password, boolean preserveConnection) throws JMSException {
        checkProperties();
        if (connectionString == null) connectionString = properties.getBrokerUrl();
        log.debug("BrokerClient: Credentials provided as arguments: username={}, password={}", username, passwordUtil.encodePassword(password));
        if (StringUtils.isBlank(username)) {
            username = properties.getBrokerUsername();
            password = properties.getBrokerPassword();
            log.debug("BrokerClient: Credentials read from properties: username={}, password={}", username, passwordUtil.encodePassword(password));
        }

        // Create connection factory
        ConnectionFactory connectionFactory = createConnectionFactory(connectionString, username, password);
        log.debug("BrokerClient: Connection credentials: username={}, password={}", username, passwordUtil.encodePassword(password));

        // Create a Connection
        log.debug("BrokerClient: Connecting to broker: {}...", connectionString);
        Connection connection = connectionFactory.createConnection();
        connection.start();

        // Create a Session
        log.debug("BrokerClient: Opening session...");
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        this.connection = connection;
        this.session = session;
    }

    public synchronized void closeConnection() throws JMSException {
        // Clean up
        if (session!=null)
            session.close();
        if (connection!=null)
            connection.close();
        session = null;
        connection = null;
    }
}