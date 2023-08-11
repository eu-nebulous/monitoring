/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker.interceptor;

import gr.iccs.imu.ems.brokercep.properties.NodeProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.Message;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Lazy
@Component
public class NodePropertiesMessageUpdateInterceptor extends AbstractMessageInterceptor {
    private NodeProperties nodeProperties;

    @Override
    public void initialized() {
        this.nodeProperties = applicationContext.getBean(NodeProperties.class);
        log.debug("NodePropertiesMessageUpdateInterceptor: Node properties: {}", nodeProperties);
        assert (nodeProperties != null);
    }

    @Override
    public void intercept(Message message) {
        // Check if interceptor is enabled
        if (! nodeProperties.isAddNodePropertiesToEventsEnabled()) {
            log.trace("NodePropertiesMessageUpdateInterceptor:  Not enabled!");
            return;
        }

        log.trace("NodePropertiesMessageUpdateInterceptor:  Message: {}", message);
        try {
            // Check if node properties have already been set.
            // If at least one node property is set then we skip further processing.
            if (message.getProperties()!=null) {
                if (nodeProperties.getNodeProperties().keySet().stream().anyMatch(message.getProperties()::containsKey)) {
                    log.trace("NodePropertiesMessageUpdateInterceptor:  Found at least one node property set in message. Skipping further processing: message: {}", message);
                    return;
                }
            }

            // Add node properties as message properties
            log.debug("NodePropertiesMessageUpdateInterceptor:  Message properties before adding node properties: properties={}, message: {}", message.getProperties(), message);

            for (Map.Entry<String, String> entry : nodeProperties.getNodeProperties().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (StringUtils.isBlank(key)) continue;
                if (nodeProperties.isSkipNullValues() && value==null || nodeProperties.isSkipBlankValues() && StringUtils.isBlank(value)) {
                    log.trace("NodePropertiesMessageUpdateInterceptor:  Skipping null- or blank-value node property due to configuration: property={}, message: {}", key, message);
                    continue;
                }
                log.trace("NodePropertiesMessageUpdateInterceptor:  Added node property to message: property={}, value={}, message: {}", key, value, message);
                message.setProperty(key, value);
            }
            log.debug("NodePropertiesMessageUpdateInterceptor:  Message properties after adding node properties: properties={}, message: {}", message.getProperties(), message);

        } catch (Exception e) {
            log.error("NodePropertiesMessageUpdateInterceptor:  EXCEPTION: ", e);
        }
    }
}
