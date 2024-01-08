/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker.interceptor;

import gr.iccs.imu.ems.brokercep.broker.InterceptorHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.Message;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SequentialCompositeInterceptor extends CompositeInterceptor {
    private final List<AbstractMessageInterceptor> interceptors = new ArrayList<>();

    @Override
    public void initialized() {
        initChildInterceptors();
    }

    private void initChildInterceptors() {
        log.debug("SequentialCompositeInterceptor: Initializing child interceptors...");
        InterceptorHelper helper = InterceptorHelper.newInstance();
        List<String> params = getInterceptorSpec().getParams();
        if (params!=null)
            params.forEach(p -> {
                log.debug(" - SequentialCompositeInterceptor: Initializing child interceptor for: {}", p);
                addMessageInterceptor(helper.initializeInterceptorFor(this, p));
                log.debug(" - SequentialCompositeInterceptor: Child interceptor initialized for: {}", p);
            });
        log.debug("SequentialCompositeInterceptor: Initializing child interceptors...done");
    }

    public void addMessageInterceptor(AbstractMessageInterceptor interceptor) {
        if (interceptor == null) throw new IllegalArgumentException("Argument is null");
        interceptors.add(interceptor);
    }

    @Override
    public void intercept(Message message) {
        log.debug("SequentialCompositeInterceptor:  Message IN:  {}", message);
        interceptors.forEach(interceptor -> {
            log.debug("SequentialCompositeInterceptor:  - Calling interceptor:  {}", interceptor.getClass().getSimpleName());
            interceptor.setProducerBrokerExchange(getProducerBrokerExchange());
            interceptor.intercept(message);
        });
        log.debug("SequentialCompositeInterceptor:  Message OUT: {}", message);
    }
}