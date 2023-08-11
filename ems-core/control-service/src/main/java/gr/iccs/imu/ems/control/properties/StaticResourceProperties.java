/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.properties;

import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "web.static")
public class StaticResourceProperties implements InitializingBean {
    @Override
    public void afterPropertiesSet() {
        log.debug("StaticResourceProperties: {}", this);
    }

    /*private String faviconContext = "/favicon.ico";
    private String faviconPath;*/

    private String resourceContext = "/resources/**";
    private List<String> resourcePath;

    private String logsContext = "/logs/**";
    private List<String> logsPath;

    private String redirect;
    private Map<String,String> redirects = new LinkedHashMap<>();
}
