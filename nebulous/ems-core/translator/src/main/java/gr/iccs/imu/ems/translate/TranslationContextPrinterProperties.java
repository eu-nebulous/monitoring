/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate;

import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "translator")
public class TranslationContextPrinterProperties implements InitializingBean {
    private boolean printResults = true;
    private Dag dag = new Dag();

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("TranslationContextPrinterProperties: {}", this);
    }

    @Data
    public static class Dag {
        // Graph rendering/export
        private boolean printDotEnabled = true;
        private boolean exportToFileEnabled = true;

        // Graph rendering parameters
        private String exportPath;
        private String[] exportFormats;
        private int exportImageWidth = -1;
    }
}
