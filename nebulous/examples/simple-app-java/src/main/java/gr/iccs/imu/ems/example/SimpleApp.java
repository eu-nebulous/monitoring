/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.example;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

public class SimpleApp {
    private Connection connection;
    private Session session;

    public static void main(String[] args) throws InterruptedException {
        // Load configuration
        String brokerAddress = getOrDefault("BROKER_SERVER", "localhost");
        String brokerPort = getOrDefault("BROKER_PORT", "61616");
        String brokerProperties = "daemon=true&trace=false&useInactivityMonitor=false&connectionTimeout=0&keepAlive=true";
        String connectionString = String.format("tcp://%s:%s?%s", brokerAddress, brokerPort, brokerProperties);
        String username = getOrDefault("BROKER_USERNAME", "aaa");
        String password = getOrDefault("BROKER_PASSWORD", "111");
        String destinationName = getOrDefault("TARGET_TOPIC", "sample_topic");

        int retryConnect = Integer.parseInt(getOrDefault("RETRY_CONNECT", "5"));
        int sendDelay = Integer.parseInt(getOrDefault("SEND_DELAY", "10"));
        double valueMin = Double.parseDouble(getOrDefault("VALUE_MIN", "0"));
        double valueMax = Double.parseDouble(getOrDefault("VALUE_MAX", "100"));

        // Print configuration
        System.out.println("=====  Configuration  =====");
        System.out.println("  BROKER_SERVER: "+brokerAddress);
        System.out.println("    BROKER_PORT: "+brokerPort);
        System.out.println(" CONNECTION-STR: "+connectionString);
        System.out.println("BROKER_USERNAME: "+username);
        System.out.println("BROKER_PASSWORD: "+password);
        System.out.println("   TARGET_TOPIC: "+destinationName);

        System.out.println("  RETRY_CONNECT: "+retryConnect);
        System.out.println("     SEND_DELAY: "+sendDelay);
        System.out.println("      VALUE_MIN: "+valueMin);
        System.out.println("      VALUE_MAX: "+valueMax);

        // Run example
        while (true) {
            try {
                SimpleApp app = new SimpleApp();
                app.runExample(connectionString, username, password,
                        destinationName, 1000L * sendDelay, valueMin, valueMax);
            } catch (Exception e) {
                System.err.println("EXCEPTION: "+e.getMessage());
                e.printStackTrace(System.err);
            }
            System.out.println("Retrying in "+retryConnect+" seconds");
            Thread.sleep(1000L * retryConnect);
        }
    }

    protected static String getOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value==null || value.trim().isEmpty())
            value = defaultValue;
        if (value==null)
            throw new IllegalArgumentException("Property "+name+" is not provided or is empty, and default value is null");
        return value.trim();
    }

    public void runExample(String connectionString, String username, String password,
                           String destinationName, long sendDelay, double valueMin, double valueMax)
            throws JMSException, InterruptedException
    {
        try {
            // open connection
            openConnection(connectionString, username, password);
            System.out.println("Connected to broker: "+connectionString+", as user: "+username);

            // Create the destination (Topic)
            Topic destination = session.createTopic(destinationName);
            System.out.println("Sending to topic: "+destinationName);

            // Create a MessageProducer from the Session to the Topic
            MessageProducer producer = session.createProducer(destination);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // Publish values
            double valueRange = valueMax - valueMin;
            while (true) {
                publishEvent(producer, valueRange * Math.random() - valueMin);
                Thread.sleep(sendDelay);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.err.println("Exception while opening connection: "+e.getMessage());
        } finally {
            // close connection
            try {
                closeConnection();
            } catch (Exception e) {
                e.printStackTrace(System.err);
                System.err.println("Exception while closing connection: "+e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------------

    protected void publishEvent(MessageProducer producer, double value) throws JMSException {
        try {
            // Create a messages
            String payloadText = String.format("{ \"metricValue\": %f, \"level\": 1, \"timestamp\": %d }", value, System.currentTimeMillis());
            Message message = session.createTextMessage(payloadText);

            // Send the message
            producer.send(message);
            System.out.println("Sent: " + payloadText);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.err.println("Exception while sending event: "+e.getMessage());
        }
    }

    // ------------------------------------------------------------------------

    public void openConnection(String connectionString, String username, String password) throws JMSException {
        // Create connection factory
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(connectionString);
        connectionFactory.setBrokerURL(connectionString);
        connectionFactory.setUserName(username);
        connectionFactory.setPassword(password);
        connectionFactory.setTrustAllPackages(true);
        connectionFactory.setWatchTopicAdvisories(true);

        // Create a Connection
        Connection connection = connectionFactory.createConnection();
        connection.start();

        // Create a Session
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        this.connection = connection;
        this.session = session;
    }

    public void closeConnection() throws JMSException {
        // Clean up
        if (session!=null) session.close();
        if (connection!=null) connection.close();
        session = null;
        connection = null;
    }
}