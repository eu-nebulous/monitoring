/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.k8s;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

import static gr.iccs.imu.ems.util.EmsConstant.EMS_PROPERTIES_PREFIX;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EMS_PROPERTIES_PREFIX + "k8s")
public class K8sServiceProperties implements InitializingBean {
    private boolean enabled = true;
    private Duration initDelay = Duration.ofSeconds(30);
    private Duration period = Duration.ofSeconds(60);
    private boolean deployEmsClientsOnKubernetesEnabled = true;

    // Pod filters
    private List<String> ignorePodsInNamespaces = List.of(
            "kube-node-lease", "kube-public", "kube-system", "local-path-storage");
    private List<String> ignorePodsWithAppLabel = List.of(
            "netdata");

    @Override
    public void afterPropertiesSet() {
        log.debug("K8sServiceProperties: {}", this);
    }
}