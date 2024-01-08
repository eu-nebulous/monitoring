/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control;

import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import gr.iccs.imu.ems.control.util.WebClientUtil;
import gr.iccs.imu.ems.util.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ApplicationContext {
    private final ControlServiceProperties properties;

    @Bean
    @SneakyThrows
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public WebClient webClient() {
        return new WebClientUtil().createInstance(properties.getSsl());
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public EventBus<String,Object,Object> eventBus() {
        return EventBus.<String,Object,Object>builder().build();
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setDaemon(true);
        log.debug("ApplicationContext: taskScheduler: NEW INSTANCE CREATED: {}", taskExecutor);
        return taskExecutor;
    }
}
