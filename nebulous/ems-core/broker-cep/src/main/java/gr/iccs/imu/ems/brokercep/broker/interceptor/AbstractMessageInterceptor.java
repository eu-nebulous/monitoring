/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker.interceptor;

import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.broker.inteceptor.MessageInterceptor;
import org.apache.activemq.broker.inteceptor.MessageInterceptorRegistry;
import org.apache.activemq.command.Message;
import org.springframework.context.ApplicationContext;

import java.util.Map;

@Slf4j
@Data
public abstract class AbstractMessageInterceptor implements MessageInterceptor {
    protected MessageInterceptorRegistry registry;
    protected ApplicationContext applicationContext;
    protected Map<String, BrokerCepProperties.MessageInterceptorSpec> messageInterceptorSpecs;

    protected BrokerCepProperties.MessageInterceptorSpec interceptorSpec;
    private ProducerBrokerExchange producerBrokerExchange;

    public void initialized() { }

    @Override
    public void intercept(ProducerBrokerExchange producerBrokerExchange, Message message) {
        try {
            this.producerBrokerExchange = producerBrokerExchange;
            intercept(message);
            registry.injectMessage(producerBrokerExchange, message);
        } catch (Exception e) {
            log.error("AbstractMessageInterceptor:  EXCEPTION: ", e);
        }
    }

    public abstract void intercept(Message message);
}