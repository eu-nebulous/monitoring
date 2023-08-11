/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.webconf;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final ApplicationContext applicationContext;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(applicationContext.getBean("asyncExecutor", AsyncTaskExecutor.class));
    }

    @Bean(name="asyncExecutor")
    public AsyncTaskExecutor asyncExecutor() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        log.debug("asyncExecutor(): ThreadPoolExecutor: core={}, max={}, size={}, active={}, keep-alive={}",
                executor.getCorePoolSize(), executor.getMaximumPoolSize(), executor.getPoolSize(),
                executor.getActiveCount(), executor.getKeepAliveTime(TimeUnit.SECONDS));
        return new ConcurrentTaskExecutor(executor);
    }

    @Bean
    public Filter contentCachingFilter() {
        log.debug("contentCachingFilter(): Registering content caching request filter");
        return (servletRequest, servletResponse, filterChain) -> {
            log.trace("contentCachingFilter(): request={}", servletRequest);
            HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
            //HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

            ServletRequest contentCachingRequestWrapper = new ContentCachingRequestWrapper(httpRequest);
            //ServletResponse contentCachingResponseWrapper = new ContentCachingResponseWrapper(httpResponse);
            log.trace("contentCachingFilter(): request={}, content-caching-request={}", servletRequest, contentCachingRequestWrapper);
            //log.trace("contentCachingFilter(): response={}, content-caching-response={}", servletResponse, contentCachingResponseWrapper);

            filterChain.doFilter(contentCachingRequestWrapper, servletResponse);
            //filterChain.doFilter(contentCachingRequestWrapper, contentCachingResponseWrapper);
        };
    }
}