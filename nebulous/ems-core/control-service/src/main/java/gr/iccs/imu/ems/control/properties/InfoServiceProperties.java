/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.properties;

import gr.iccs.imu.ems.control.util.EventBusCache;
import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "info")
public class InfoServiceProperties implements InitializingBean {
    @Min(1)
    private long metricsUpdateInterval = 1000;
    @Min(1)
    private long metricsClientUpdateInterval = 500; //XXX:TODO: Not really needed since clients PUSH their statistics to server
    @Min(1)
    private int metricsStreamUpdateInterval = 10;    // in seconds
    @NotBlank
    private String metricsStreamEventName = "ems-metrics-event";
    private List<String> envVarPrefixes = Arrays.asList("WEBSSH_SERVICE_-^", "WEB_ADMIN_!^");
                // ! at the end means to trim off the prefix; - at the end means to convert '_' to '-';
                // ^ at the end means convert to upper case; ~ at the end means convert to lower case;

    private FileExplorerProperties files = new FileExplorerProperties();

    private List<Path> logViewerFiles = Collections.emptyList();

    private boolean eventBusCacheEnabled = true;
    private int eventBusCacheSize = EventBusCache.DEFAULT_EVENT_BUS_CACHE_SIZE;

    @Override
    public void afterPropertiesSet() {
        log.debug("InfoServiceProperties: {}", this);
    }

    @Data
    public static class FileExplorerProperties {
        private boolean enabled = true;
        private List<Path> roots = Collections.emptyList();
        private List<String> extensionsBlocked = Collections.emptyList();
        private boolean listBlocked = true;
        private boolean listHidden = true;
    }
}
