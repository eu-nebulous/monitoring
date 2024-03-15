/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker.interceptor;

import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.NetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.Connection;
import org.apache.activemq.command.Message;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class SourceAddressMessageUpdateInterceptor extends AbstractMessageInterceptor {
    private final String SOURCE_ADDRESS_PROPERTY_NAME = EmsConstant.EVENT_PROPERTY_SOURCE_ADDRESS;

    @Override
    public void intercept(Message message) {
        log.trace("SourceAddressMessageUpdateInterceptor:  Message: {}", message);
        try {
            Object sourceProperty = message.getProperty(SOURCE_ADDRESS_PROPERTY_NAME);
            if (sourceProperty!=null && StringUtils.isNotBlank(sourceProperty.toString())) {
                log.trace("SourceAddressMessageUpdateInterceptor:  Message has Producer Host property set: {}", sourceProperty);
                return;
            }

            // get remote address from connection
            Connection conn = getProducerBrokerExchange().getConnectionContext().getConnection();
            log.trace("SourceAddressMessageUpdateInterceptor:  Connection: {}", conn);
            String address = conn.getRemoteAddress();
            log.trace("SourceAddressMessageUpdateInterceptor:  Producer address: {}", address);

            // extract remote host address
            if (StringUtils.isNotBlank(address)) {
                address = StringUtils.substringsBetween(address, "//", ":") [0];
            }
            log.trace("SourceAddressMessageUpdateInterceptor:  Producer host: {}", address);

            // check if host address is local
            boolean isLocal = StringUtils.isBlank(address) || NetUtil.isLocalAddress(address.trim());
            if (isLocal) {
                log.trace("SourceAddressMessageUpdateInterceptor:  Producer host is local. Getting our public IP address");
                address = NetUtil.getIpSettingAddress();
                log.trace("SourceAddressMessageUpdateInterceptor:  Producer host ({}): {}",
                        NetUtil.isUsePublic() ? "public" : "default", address);
            } else {
                log.trace("SourceAddressMessageUpdateInterceptor:  Producer host is not local. Ok");
            }

            // get message remote address old value (if any)
            String oldAddress = (String) message.getProperty(SOURCE_ADDRESS_PROPERTY_NAME);
            log.trace("SourceAddressMessageUpdateInterceptor:  Producer host property in message: {}", oldAddress);

            // set new remote address value, if needed
            if (StringUtils.isBlank(oldAddress) && StringUtils.isNotBlank(address)) {
                log.trace("SourceAddressMessageUpdateInterceptor:  Setting producer host property in message: host={}, message={}", address, message);
                message.setProperty(SOURCE_ADDRESS_PROPERTY_NAME, address);
                log.debug("SourceAddressMessageUpdateInterceptor:  Set producer host property in message: host={}, message={}", address, message);
            } else if (StringUtils.isNotBlank(oldAddress)) {
                log.debug("SourceAddressMessageUpdateInterceptor:  Producer host property already set (keeping previous value): host={}, message={}", oldAddress, message);
            } else if (StringUtils.isBlank(address)) {
                log.warn("SourceAddressMessageUpdateInterceptor:  Could not resolve Producer host property: message={}", message);
            }

        } catch (Exception e) {
            log.error("SourceAddressMessageUpdateInterceptor:  EXCEPTION: ", e);
        }
    }
}
