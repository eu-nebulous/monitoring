/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@EndpointWebExtension(endpoint = InfoEndpoint.class)
@ConditionalOnAvailableEndpoint(endpoint = InfoEndpoint.class /*, exposure = org.springframework.boot.actuate.autoconfigure.endpoint.expose.EndpointExposure.WEB*/)
public class ControlServiceInfoEndpointExtension implements InitializingBean {

    private final ApplicationContext applicationContext;
    private final InfoEndpoint delegate;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Info endpoint is enabled and exposed. Added EMS info extension.");
    }

    @ReadOperation
    public WebEndpointResponse<Map> info() {
        Map<String, Object> info = new HashMap<>(this.delegate.info());
        info.put("ems-build-info", applicationContext.getBean(BuildInfoProvider.class).getMetricValues());
        info.put("ems-live-info", applicationContext.getBean(IEmsInfoService.class).getServerMetricValues());
        return new WebEndpointResponse<>(info, 200);
    }
}
