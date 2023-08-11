/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.properties;

import gr.iccs.imu.ems.util.KeystoreAndCertificateProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;

import static gr.iccs.imu.ems.util.EmsConstant.EMS_PROPERTIES_PREFIX;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EMS_PROPERTIES_PREFIX + "control")
/*@PropertySource(value = {
        "file:${EMS_CONFIG_DIR}/ems-server.yml",
        "file:${EMS_CONFIG_DIR}/ems-server.properties",
        "file:${EMS_CONFIG_DIR}/ems.yml",
        "file:${EMS_CONFIG_DIR}/ems.properties"
}, ignoreResourceNotFound = true)*/
public class ControlServiceProperties {
    public enum IpSetting {
        DEFAULT_IP,
        PUBLIC_IP
    }

    public enum ExecutionWare {
        CLOUDIATOR, PROACTIVE
    }

    private boolean printBuildInfo;

    private IpSetting ipSetting = IpSetting.PUBLIC_IP;
    private ExecutionWare executionware = ExecutionWare.PROACTIVE;

    private String upperwareGrouping;
    private String metasolverConfigurationUrl;
    private String esbUrl;

    private Preload preload = new Preload();

    private boolean skipTranslation;
    private boolean skipMvvRetrieve;
    private boolean skipBrokerCep;
    private boolean skipBaguette;
    private boolean skipCollectors;
    private boolean skipMetasolver;
    private boolean skipEsbNotification;

    private String tcLoadFile;
    private String tcSaveFile;

    private boolean exitAllowed;
    @Min(1)
    private long exitGracePeriod = 10;
    private int exitCode = 0;

    // control.ssl.** settings
    @NestedConfigurationProperty
    private KeystoreAndCertificateProperties ssl;

    @Data
    public static class Preload {
        private String camelModel;
        private String cpModel;
    }
}
