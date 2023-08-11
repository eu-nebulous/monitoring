/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import gr.iccs.imu.ems.util.password.AsterisksPasswordEncoder;
import gr.iccs.imu.ems.util.password.PasswordEncoder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordUtil implements InitializingBean {
    private final static Supplier<PasswordEncoder> passwordEncoderSupplier = AsterisksPasswordEncoder::new;
    private final static AtomicReference<PasswordEncoder> defaultPasswordEncoder = new AtomicReference<>();

    private final static Object LOCK = new Object();
    private static volatile PasswordUtil instance;

    private final PasswordUtilProperties properties;
    private PasswordEncoder passwordEncoder;

    public static PasswordUtil getInstance() {
        if (instance==null) {
            synchronized (LOCK) {
                if (instance==null) {
                    instance = new PasswordUtil(new PasswordUtilProperties());
                    instance.afterPropertiesSet();
                }
            }
        }
        return instance;
    }

    @Override
    public void afterPropertiesSet() {
        String passwordEncoderClassName = properties.getPasswordEncoderClass();
        log.debug("PasswordUtil: password-encoder-class: {}", passwordEncoderClassName);
        this.setPasswordEncoder(StringUtils.trim(passwordEncoderClassName));
        if (passwordEncoder!=null)
            if (defaultPasswordEncoder.compareAndSet(null, passwordEncoder))
                log.info("PasswordUtil: Initialized default Password Encoder: {}", defaultPasswordEncoder.get().getClass().getName());
    }

    public String encodePassword(String password) {
        return getPasswordEncoder().encode(password);
    }

    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder!=null
                ? passwordEncoder : (passwordEncoder=createPasswordEncoder(null));
    }

    public void setPasswordEncoder(PasswordEncoder pe) {
        passwordEncoder = pe;
        log.debug("PasswordUtil.setPasswordEncoder(): PasswordEncoder set to: {}", passwordEncoder.getClass().getName());
    }

    public void setPasswordEncoder(String passwordEncoderClassName) {
        setPasswordEncoder(createPasswordEncoder(passwordEncoderClassName));
    }

    public static PasswordEncoder createPasswordEncoder(String passwordEncoderClassName) {
        if (StringUtils.isBlank(passwordEncoderClassName)) {
            log.debug("Password encoder class name is empty. Default instance of PasswordEncoder will be created");
            return passwordEncoderSupplier.get();
        }

        try {
            Class<?> passwordEncoderClass = Class.forName(passwordEncoderClassName);
            return (PasswordEncoder) passwordEncoderClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
            log.warn("Could not instantiate PasswordEncoder instance of {}. Default instance of PasswordEncoder will be created", passwordEncoderClassName);
            return passwordEncoderSupplier.get();
        }
    }

    public static PasswordEncoder getDefaultPasswordEncoder() {
        return Optional.ofNullable(defaultPasswordEncoder.get())
                .orElse(passwordEncoderSupplier.get());
    }

    @Slf4j
    @Data
    @Configuration
    @ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX)
    public static class PasswordUtilProperties implements InitializingBean {
        private String passwordEncoderClass;

        @Override
        public void afterPropertiesSet() throws Exception {
            log.debug("PasswordUtilProperties: {}", this);
        }
    }
}
