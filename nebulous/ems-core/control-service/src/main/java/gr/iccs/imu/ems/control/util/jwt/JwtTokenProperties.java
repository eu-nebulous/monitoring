/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util.jwt;

import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "jwt")
public class JwtTokenProperties implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("JwtTokenProperties: {}", this);
    }

    @NotBlank
    @ToString.Exclude
    private String secret;
    @NotNull
    private Long expirationTime = Duration.ofDays(1).toMillis();
    @NotNull
    private Long refreshTokenExpirationTime = Duration.ofDays(1).toMillis();
}