/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker.interceptor;

import gr.iccs.imu.ems.brokercep.broker.BrokerConfig;
import gr.iccs.imu.ems.brokercep.event.EventRecorder;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.Message;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Lazy
@Component
public class LogMessageUpdateInterceptor extends AbstractMessageInterceptor {
    private EventRecorder eventRecorder;

    @Override
    public void initialized() {
        this.eventRecorder = applicationContext.getBean(BrokerConfig.class).getEventRecorder();
        log.debug("LogMessageUpdateInterceptor: Enabled: {}", eventRecorder!=null);
        if (eventRecorder!=null)
            eventRecorder.startRecording();
    }

    @Override
    public void intercept(Message message) {
        try {
            if (eventRecorder!=null && message instanceof ActiveMQMessage activeMQMessage)
                eventRecorder.recordEvent(activeMQMessage);
        } catch (Exception e) {
            log.error("LogMessageUpdateInterceptor:  EXCEPTION: ", e);
        }
    }
}
