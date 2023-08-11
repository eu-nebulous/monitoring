/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jmx.export.annotation.*;
import org.springframework.jmx.export.notification.NotificationPublisher;
import org.springframework.jmx.export.notification.NotificationPublisherAware;
import org.springframework.jmx.support.MetricType;
import org.springframework.stereotype.Component;

import javax.management.Notification;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component("emsControl")
@ManagedResource(
        objectName = "gr.iccs.imu.ems:category=EmsInfo,name=emsControl",
        log = true,
//        logFile = "ems_notif.txt",
        description="EMS Control Service Bean")
@ManagedNotifications({
        @ManagedNotification(name = "randNumNotif", notificationTypes = { "java.lang.String", "java.lang.Double" }),
        @ManagedNotification(name = "timestampNotif", notificationTypes = { "java.lang.String" })
})
public class ControlServiceMBean implements NotificationPublisherAware {
    private NotificationPublisher notificationPublisher;
    private AtomicLong notificationSequence = new AtomicLong(0);

    @ManagedOperation
    public void testOk() {
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!! testOk");
    }
    
    @ManagedOperation
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "message", description = "Message param")
    })
    public void test2(String mesg) {
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!! test2: {}", mesg);
    }

    private String attrib;

    @ManagedAttribute
    public String getAttrib() {
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!! getAttrib: {}", attrib);
        return attrib;
    }
    @ManagedAttribute
    public void setAttrib(String s) {
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!! setAttrib: {} -> {}", attrib, s);
        attrib = new String(s);
    }

    @ManagedMetric(category = "ems-metrics", description = "EMS metrics bla bla", displayName = "Curr Date",
        metricType = MetricType.COUNTER, unit = "_date")
    public Date getCurrDate() {
        Date now = new Date();
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!! getCurrDate: {}", now);
        return now;
    }

    @Override
    public void setNotificationPublisher(NotificationPublisher notificationPublisher) {
        this.notificationPublisher = notificationPublisher;
    }

    @ManagedOperation
    public void trigger() {
        if (notificationPublisher != null) {
            final Notification notification = new Notification("java.lang.String",
                    getClass().getName(),
                    notificationSequence.get(),
                    "A random number:  "+(Math.random()*10000000000L));
            notificationPublisher.sendNotification(notification);
            log.warn("!!!!!!!!!!!!!!!!!!!!!!!! trigger/1: {}", notification);

            final Notification notification2 = new Notification("java.lang.Double",
                    "source2",
                    notificationSequence.getAndIncrement(),
                    ""+(Math.random()*10000000000L));
            notificationPublisher.sendNotification(notification2);
            log.warn("!!!!!!!!!!!!!!!!!!!!!!!! trigger/2: {}", notification2);
        }
    }
}
