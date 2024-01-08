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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "web.security")
public class WebSecurityProperties implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("WebSecurityProperties: {}", this);
    }

    // JWT Token authentication
    @Valid
    @NotNull
    private JwtAuthentication jwtAuthentication = new JwtAuthentication();

    @Data
    public static class JwtAuthentication {
        private boolean enabled = true;
        private String requestParameter;
        private boolean printSampleToken;
    }

    // API-Key authentication
    @Valid
    @NotNull
    private ApiKeyAuthentication apiKeyAuthentication = new ApiKeyAuthentication();

    @Data
    public static class ApiKeyAuthentication {
        private boolean enabled = true;
        private String requestHeader = "EMS-API-KEY";
        private String requestParameter = "ems-api-key";
        private String value;
    }

    // OTP authentication
    @Valid
    @NotNull
    private OtpAuthentication otpAuthentication = new OtpAuthentication();

    @Data
    public static class OtpAuthentication {
        private boolean enabled = true;
        @Min(1)
        private long duration = 3600000;
        private String requestHeader = "EMS-OTP";
        private String requestParameter = "ems-otp";
    }

    // User form authentication
    @Valid
    @NotNull
    private FormAuthentication formAuthentication = new FormAuthentication();

    @Data
    public static class FormAuthentication {
        private boolean enabled = true;
        private String username = "admin";
        private String password;

        private String loginPage = "/admin/login.html";
        private String loginUrl = "/login";
        private String loginSuccessUrl = "/";
        private String loginFailureUrl = "/admin/login.html?error=Invalid+username+or+password";
        private String logoutUrl = "/logout";
        private String logoutSuccessUrl = "/admin/login.html?message=Signed+out";
    }

    // Permitted URLs
    private List<String> permittedUrls = Arrays.asList(
            "/login*", "/logout*", "/admin/login.html", "/favicon.ico", "/admin/favicon.ico",
            "/admin/assets/**", "/resources/*", "/health");
}
