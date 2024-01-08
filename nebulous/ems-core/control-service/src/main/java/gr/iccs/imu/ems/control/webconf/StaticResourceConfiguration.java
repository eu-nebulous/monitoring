/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.webconf;

import gr.iccs.imu.ems.control.properties.StaticResourceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StaticResourceConfiguration implements WebMvcConfigurer, InitializingBean {
    private final StaticResourceProperties properties;

    public void afterPropertiesSet() {
        log.debug("StaticResourceConfiguration: afterPropertiesSet: {}", properties);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        /*String faviconContext = properties.getFaviconContext();
        String faviconPath = properties.getFaviconPath();
        if(StringUtils.isNotBlank(faviconPath)) {
            log.debug("Serving favicon.ico from: {} --> {}", faviconContext, faviconPath);
            registry
                    .addResourceHandler(faviconContext)
                    .addResourceLocations(faviconPath);
        }*/

        String resourceContext = properties.getResourceContext();
        List<String> resourcePath = properties.getResourcePath();
        if (resourcePath != null && resourcePath.size() > 0) {
            log.debug("Serving static content from: {} --> {}", resourceContext, resourcePath);
            registry
                    .addResourceHandler(resourceContext)
                    .addResourceLocations(resourcePath.toArray(new String[0]));
        }

        String logsContext = properties.getLogsContext();
        List<String> logsPath = properties.getLogsPath();
        if (logsPath != null && logsPath.size() > 0) {
            log.debug("Serving logs from: {} --> {}", logsContext, logsPath);
            registry
                    .addResourceHandler(logsContext)
                    .addResourceLocations(logsPath.toArray(new String[0]));
        }

        WebMvcConfigurer.super.addResourceHandlers(registry);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Remains for backward compatibility (of properties file)
        String resourceRedirect = properties.getRedirect();
        if (StringUtils.isNotBlank(resourceRedirect)) {
            log.debug("Redirecting / to: {}", resourceRedirect);
            registry
                    .addViewController("/")
                    .setViewName("redirect:" + resourceRedirect);
        }

        Map<String,String> resourceRedirects = properties.getRedirects();
        log.debug("Configured resource redirects: {}", resourceRedirects);
        if (resourceRedirects!=null) {
            resourceRedirects.forEach((context, redirect) -> {
                if (StringUtils.isNotBlank(context) && StringUtils.isNotBlank(redirect)) {
                    context = context.trim();
                    redirect = redirect.trim();
                    log.debug("Redirecting {} to: {}", context, redirect);
                    registry
                            .addViewController(context)
                            .setViewName("redirect:" + redirect);
                }
            });
        }

        WebMvcConfigurer.super.addViewControllers(registry);
    }

    @ConditionalOnProperty(name="control.log-requests", matchIfMissing = true)
    @Bean
    public CommonsRequestLoggingFilter logFilter() {
        CommonsRequestLoggingFilter filter
                = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(10000);
        filter.setIncludeHeaders(true);
        filter.setIncludeClientInfo(true);

        filter.setBeforeMessagePrefix("REQUEST DATA BEFORE: >>");
        filter.setBeforeMessageSuffix("<< REQUEST DATA BEFORE");
        filter.setAfterMessagePrefix("REQUEST DATA AFTER: >>");
        filter.setAfterMessageSuffix("<< REQUEST DATA AFTER");
        return filter;
    }
}
