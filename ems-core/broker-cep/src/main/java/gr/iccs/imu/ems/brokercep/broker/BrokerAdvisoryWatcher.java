/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker;

import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.DataStructure;
import org.apache.activemq.command.DestinationInfo;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import javax.jms.*;
import java.time.Instant;

@Slf4j
@Service
@ConditionalOnProperty(name="brokercep.enable-advisory-watcher", matchIfMissing = true)
@RequiredArgsConstructor
public class BrokerAdvisoryWatcher implements MessageListener, InitializingBean, ApplicationListener<ContextClosedEvent> {
	private final BrokerService brokerService;	// Added in order to ensure that BrokerService will be instantiated first
	private final BrokerConfig brokerConfig;
	private final BrokerCepService brokerCepService;
	private final BrokerCepProperties properties;

	private ConnectionFactory connectionFactory;

	private final PasswordUtil passwordUtil;
	private final TaskScheduler taskScheduler;

	private Connection connection;
	private Session session;
	private boolean shuttingDown;

	@Override
	public void afterPropertiesSet() {
		log.debug("BrokerAdvisoryWatcher: afterPropertiesSet: BrokerCepProperties: {}", brokerCepService.getBrokerCepProperties());
		initialize();
	}
	
	protected void initialize() {
		log.debug("BrokerAdvisoryWatcher.init(): Initializing instance...");
		try {
			// close previous session and connection
			closeConnection();

			// If an alternative Broker URL is provided for consumer, it will be used
			if (connectionFactory==null) {
				connectionFactory = brokerConfig.getConnectionFactoryForConsumer();
			}

			// If authentication is enabled get credentials
			boolean usesAuthentication = brokerCepService.getBrokerCepProperties().isAuthenticationEnabled();
			String username = brokerCepService.getBrokerUsername();
			String password = brokerCepService.getBrokerPassword();
			log.debug("BrokerAdvisoryWatcher.init(): uses-authentication={}, username={}, password={}",
					usesAuthentication, username, passwordUtil.encodePassword(password));

			// Create and start new connection
			this.connection = usesAuthentication
					? connectionFactory.createConnection(username, password)
					: connectionFactory.createConnection();
			connection.setExceptionListener(e -> {
				if (!shuttingDown) {
					log.warn("BrokerAdvisoryWatcher: Connection exception listener: Exception caught: ", e);
					initialize();
				}
			});
			this.connection.start();

			// Create a new session, and new consumer for topic
			this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Topic topic = session.createTopic("ActiveMQ.Advisory.>");
			MessageConsumer consumer = session.createConsumer(topic);
			consumer.setMessageListener( this );

			log.debug("BrokerAdvisoryWatcher.init(): Initializing instance... done");
		} catch (Exception ex) {
			log.error("BrokerAdvisoryWatcher.init(): EXCEPTION: while retry in {} seconds:", properties.getAdvisoryWatcherInitRetryDelay(), ex);
			final BrokerAdvisoryWatcher _this = this;
			taskScheduler.schedule(_this::initialize, Instant.now().plusSeconds(properties.getAdvisoryWatcherInitRetryDelay()));
		}
	}

	@Override
	public void onApplicationEvent(ContextClosedEvent event) {
		log.info("BrokerAdvisoryWatcher is shutting down");
		shuttingDown = true;
	}

	private void closeConnection() {
		// close previous session and connection
		try {
			if (session != null) {
				session.close();
				log.debug("BrokerCepConsumer.closeConnection(): Closed pre-existing sessions");
			}
		} catch (Exception e) {
			log.warn("BrokerCepConsumer.closeConnection(): Exception while closing old session: ", e);
		}
		try {
			if (connection != null) {
				connection.close();
				log.debug("BrokerCepConsumer.closeConnection(): Closed pre-existing connection");
			}
		} catch (Exception e) {
			log.warn("BrokerCepConsumer.closeConnection(): Exception while closing old connection: ", e);
		}
		session = null;
		connection = null;
	}

	@Override
	public void onMessage(Message message) {
		try {
			log.trace("BrokerAdvisoryWatcher.onMessage(): {}", message);
			ActiveMQMessage mesg = (ActiveMQMessage) message;
			ActiveMQDestination messageDestination = mesg.getDestination();
			log.trace("BrokerAdvisoryWatcher.onMessage(): advisory-message-source={}", messageDestination);
			
			DataStructure ds = mesg.getDataStructure();
			log.trace("BrokerAdvisoryWatcher.onMessage(): advisory-message-data-structure={}", ds==null ? null : ds.getClass().getSimpleName());
			if (ds!=null) {
				// Advisory event
				processAdvisoryMessage(ds);
			} else {
				// Non-advisory event
				processPlainMessage(mesg);
			}
		} catch (Exception ex) {
			log.error("BrokerAdvisoryWatcher.onMessage(): EXCEPTION: ", ex);
		}
	}

	private void processPlainMessage(ActiveMQMessage mesg) throws JMSException {
		if (mesg instanceof TextMessage) {
			TextMessage txtMesg = (TextMessage) mesg;
			String topicName = mesg.getDestination().getPhysicalName();
			log.trace("BrokerAdvisoryWatcher.onMessage(): Text Message received: topic={}, message={}", topicName, txtMesg.getText());
		} else {
			String topicName = mesg.getDestination().getPhysicalName();
			log.trace("BrokerAdvisoryWatcher.onMessage(): Non-text Message received: topic={}, type={}", topicName, mesg.getClass().getName());
		}
	}

	private void processAdvisoryMessage(DataStructure ds) throws JMSException {
		if (ds instanceof DestinationInfo) {
			DestinationInfo info = (DestinationInfo) ds;
			ActiveMQDestination destination = info.getDestination();
			boolean isAdd = info.isAddOperation();
			boolean isDel = info.isRemoveOperation();
			log.debug("BrokerAdvisoryWatcher.onMessage(): Received a DestinationInfo message: destination={}, is-queue={}, is-topic={}, is-add={}, is-del={}",
					destination, destination.isQueue(), destination.isTopic(), isAdd, isDel);

			// Subscribe to topic
			if (isAdd) {
				String topicName = destination.getPhysicalName();
				log.debug("BrokerAdvisoryWatcher.onMessage(): Subscribing to topic: {}", topicName);

				MessageConsumer consumer = session.createConsumer(destination);
				consumer.setMessageListener(this);
			}
			/*if (isDel) {
				String topicName = destination.getPhysicalName();
				log.info("BrokerAdvisoryWatcher.onMessage(): Leaving topic: {}", topicName);
			}*/

		} else {
			log.trace("BrokerAdvisoryWatcher.onMessage(): Message ignored");
		}
	}
}