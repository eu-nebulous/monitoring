/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties
public class EmsNebulousProperties implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("{}", this);
    }

    private String applicationId;
    private String appId;
    private String appUid;
    private String appUuid;

    private String emsServerPodUid;
    private String emsServerPodNamespace;
    private String appPodLabel = "app";

    public String getApplicationId() {
        return StringUtils.firstNonBlank(applicationId, appId, appUid, appUuid);
    }
}
