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
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.inteceptor.MessageInterceptorRegistry;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class InterceptorHelper {
    public static InterceptorHelper newInstance() {
        return new InterceptorHelper();
    }

    public List<AbstractMessageInterceptor> initializeInterceptors(final MessageInterceptorRegistry registry,
                                                                   ApplicationContext applicationContext,
                                                                   Map<String, BrokerCepProperties.MessageInterceptorSpec> specs,
                                                                   List<BrokerCepProperties.MessageInterceptorSpec> interceptorSpecs)
    {
        log.debug("InterceptorHelper: Initialize interceptors...");

        List<AbstractMessageInterceptor> interceptors = new ArrayList<>();
        interceptorSpecs
                .forEach(spec -> {
                    AbstractMessageInterceptor interceptor = initializeInterceptor(registry, applicationContext, specs, spec);
                    interceptors.add(interceptor);
                });
        log.debug("InterceptorHelper: Initialize interceptors... done");
        return interceptors;
    }

    @SuppressWarnings("unchecked")
    public AbstractMessageInterceptor initializeInterceptor(final MessageInterceptorRegistry registry,
                                      ApplicationContext applicationContext,
                                      Map<String, BrokerCepProperties.MessageInterceptorSpec> specs,
                                      BrokerCepProperties.MessageInterceptorSpec interceptorSpec)
    {
        log.debug("InterceptorHelper: Initializing message interceptor with spec.: {}", interceptorSpec);
        String interceptorClassName = interceptorSpec.getClassName();
        Class<? extends AbstractMessageInterceptor> interceptorClass;
        try {
            interceptorClass = (Class<? extends AbstractMessageInterceptor>) Class.forName(interceptorClassName);
        } catch (ClassNotFoundException e) {
            log.error("InterceptorHelper: Error while registering message interceptor: {}. Exception: ", interceptorSpec, e);
            throw new RuntimeException(e);
        }

        AbstractMessageInterceptor interceptor = null;
        Exception lastException = null;
        // Try 2-args constructor
        try {
            interceptor = interceptorClass
                    .getDeclaredConstructor(MessageInterceptorRegistry.class, ApplicationContext.class)
                    .newInstance(registry, applicationContext);
        } catch (Exception e) {
            log.debug("InterceptorHelper: Instantiating message interceptor with 2-args constructor failed: {} {}",
                    e.getClass().getSimpleName(), e.getMessage());
            lastException = e;
        }
        // Try 1-arg constructor
        if (interceptor==null) {
            try {
                interceptor = interceptorClass
                        .getDeclaredConstructor(MessageInterceptorRegistry.class)
                        .newInstance(registry);
            } catch (Exception e) {
                log.debug("InterceptorHelper: Instantiating message interceptor with 1-arg constructor failed: {} {}",
                        e.getClass().getSimpleName(), e.getMessage());
                lastException = e;
            }
        }
        // Try no-args constructor
        if (interceptor==null) {
            try {
                interceptor = interceptorClass
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Exception e) {
                log.debug("InterceptorHelper: Instantiating message interceptor with no-args constructor failed: {} {}",
                        e.getClass().getSimpleName(), e.getMessage());
                lastException = e;
            }
        }
        // Throw exception if all tries failed
        if (interceptor==null) {
            log.error("InterceptorHelper: Instantiating message interceptor failed: Last exception: ", lastException);
            throw new RuntimeException("Interceptor initialization exception", lastException);
        }

        // Initialize interceptor
        interceptor.setRegistry(registry);
        interceptor.setApplicationContext(applicationContext);
        interceptor.setMessageInterceptorSpecs(specs);
        interceptor.setInterceptorSpec(interceptorSpec);
        interceptor.initialized();
        log.debug("InterceptorHelper: Message interceptor initialized: {}", interceptorSpec);

        return interceptor;
    }

    public AbstractMessageInterceptor initializeInterceptor(final AbstractMessageInterceptor parent,
                                                            BrokerCepProperties.MessageInterceptorSpec interceptorSpec)
    {
        return initializeInterceptor(parent.getRegistry(), parent.getApplicationContext(),
                parent.getMessageInterceptorSpecs(), interceptorSpec);
    }

    public AbstractMessageInterceptor initializeInterceptorFor(final AbstractMessageInterceptor parent, String specId)
    {
        BrokerCepProperties.MessageInterceptorSpec interceptorSpec =
                getInterceptorSpecFor(specId, parent.getMessageInterceptorSpecs());
        return initializeInterceptor(parent.getRegistry(), parent.getApplicationContext(),
                parent.getMessageInterceptorSpecs(), interceptorSpec);
    }

    /*public AbstractMessageInterceptor initializeInterceptorFor(final MessageInterceptorRegistry registry,
                                                            ApplicationContext applicationContext,
                                                            Map<String, BrokerCepProperties.MessageInterceptorSpec> specs,
                                                            String specId)
    {
        BrokerCepProperties.MessageInterceptorSpec interceptorSpec = getInterceptorSpecFor(specId, specs);
        return initializeInterceptor(registry, applicationContext, specs, interceptorSpec);
    }*/

    public BrokerCepProperties.MessageInterceptorSpec getInterceptorSpecFor(String specId, Map<String, BrokerCepProperties.MessageInterceptorSpec> specs) {
        log.debug("InterceptorHelper:   getInterceptorSpecFor: spec-id={}, specs={}", specId, specs);
        BrokerCepProperties.MessageInterceptorSpec spec;
        if (specId.startsWith("#")) {
            specId = specId.substring(1);
            spec = specs.get(specId);
            if (spec==null)
                throw new IllegalArgumentException("Message Interceptor Spec Id not found in configuration: "+specId);
        } else {
            spec = new BrokerCepProperties.MessageInterceptorSpec();
            spec.setClassName(specId);
        }
        return spec;
    }
}