/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate;

import gr.iccs.imu.ems.translate.Grouping;
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
public class NebulousEmsTranslatorProperties implements InitializingBean {
    @Override
    public void afterPropertiesSet() {
        log.debug("NebulousEmsTranslatorProperties: {}", this);
    }

    // Model validation settings
    private boolean skipModelValidation;

    // Translator parameters
    private String modelsDir = "models";

    // Sensor processing parameters
    private long sensorMinInterval;
    private long sensorDefaultInterval;

    // TC processing settings
    private Grouping leafNodeGrouping = Grouping.PER_INSTANCE;
    private boolean pruneMvv = true;
    private boolean addTopLevelMetrics = true;
    private String fullNamePattern = "{ELEM}";

    // Busy-Status metric settings
    private String busyStatusDestinationNameFormatter = "busy.%s";

    // Orphan metrics
    private boolean includeOrphanMetrics = true;
    private String orphanMetricsParentName = "_ORPHANED_METRICS_ROOT_VAR_";

    // DAG post-processing
    private boolean updateNodesWithRequiringComponents = true;
    private boolean useCommonOrphansParent;
}
