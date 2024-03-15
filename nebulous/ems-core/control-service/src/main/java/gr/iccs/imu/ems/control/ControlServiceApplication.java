/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control;

import com.ulisesbocchio.jasyptspringboot.environment.StandardEncryptableEnvironment;
import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import gr.iccs.imu.ems.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.Banner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@EnableAsync
@EnableScheduling
@Configuration
@SpringBootApplication(
        scanBasePackages = {
                "gr.iccs.imu.ems.baguette.server", "gr.iccs.imu.ems.baguette.client.install",
                "gr.iccs.imu.ems.baguette.client.selfhealing", "gr.iccs.imu.ems.brokercep",
                "gr.iccs.imu.ems.control", "gr.iccs.imu.ems.translate",
                "gr.iccs.imu.ems.common", "gr.iccs.imu.ems.util",
                "gr.iccs.imu.ems.brokerclient",
                "${scan.packages}"
        },
        exclude = { SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class } )
@RequiredArgsConstructor
public class ControlServiceApplication {
    private static ConfigurableApplicationContext applicationContext;
    private static Timer exitTimer;

    private final ControlServiceProperties properties;
    private final PasswordUtil passwordUtil;

    public static void main(String[] args) {
        System.out.println(EmsRelease.EMS_DESCRIPTION);

        long initStartTime = System.currentTimeMillis();

        // Start EMS server
        SpringApplication springApplication = new SpringApplicationBuilder()
                .environment(new StandardEncryptableEnvironment())
                .sources(ControlServiceApplication.class)
                .build();
        //SpringApplication springApplication = new SpringApplication(ControlServiceApplication.class);
        springApplication.setBannerMode(Banner.Mode.LOG);
        springApplication.addListeners(new ApplicationPidFileWriter("./ems.pid"));
        applicationContext = springApplication.run(args);

        // Load configured plugins
        /*BeanDefinitionRegistry beanFactory = (BeanDefinitionRegistry) applicationContext.getAutowireCapableBeanFactory();
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(
                beanFactory, true, applicationContext.getEnvironment());
        scanner.scan("gr.iccs.imu.ems");
        */

        long initEndTime = System.currentTimeMillis();
        log.info("EMS server initialized in {}ms", initEndTime-initStartTime);
        StrUtil.castToEventBusStringObjectObject(applicationContext.getBean(EventBus.class))
                .send(ControlServiceCoordinator.COORDINATOR_STATUS_TOPIC, Map.of(
                        "state", "EMS STARTED",
                        "message", "EMS server initialized in "+(initEndTime-initStartTime)+"ms",
                        "timestamp", System.currentTimeMillis()
                ), applicationContext.getBean(ControlServiceApplication.class));
    }

    @Bean
    public ServletWebServerFactory servletWebServerFactory() {
        return new TomcatServletWebServerFactory() {
            protected void customizeConnector(Connector connector) {
                if (this.getSsl() != null && this.getSsl().isEnabled()) {
                    try {
                        log.debug("TomcatServletWebServerFactory: ControlServiceProperties: {}", properties);
                        log.debug("TomcatServletWebServerFactory: Keystore password: {}", passwordUtil.encodePassword(properties.getSsl().getKeystorePassword()));
                        log.debug("TomcatServletWebServerFactory: Truststore password: {}", passwordUtil.encodePassword(properties.getSsl().getTruststorePassword()));

                        log.debug("TomcatServletWebServerFactory: Initializing HTTPS keystore, truststore and certificate...");
                        KeystoreUtil.initializeKeystoresAndCertificate(properties.getSsl(), passwordUtil);
                        log.debug("TomcatServletWebServerFactory: Initializing HTTPS keystore, truststore and certificate... done");
                    } catch (Exception e) {
                        log.error("TomcatServletWebServerFactory: EXCEPTION while initializing HTTPS keystore, truststore and certificate:\n", e);
                    }
                }
                super.customizeConnector(connector);
            }
        };
    }

    public synchronized static void exitApp(int exitCode, long gracePeriod) {
        if (exitTimer==null) {
            // Wait for 'gracePeriod' seconds before forcing JVM to exit
            log.info("ControlServiceApplication.exitApp(): Wait for {}sec before exit", gracePeriod);
            exitTimer = new Timer("exit-timer", true);
            exitTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    log.info("ControlServiceApplication.exitApp(): exit-timer: Exiting with code: {}", exitCode);
                    System.exit(exitCode);
                    log.info("ControlServiceApplication.exitApp(): exit-timer: Bye");
                }
            }, gracePeriod * 1000);

            // Close SpringBoot application
            log.info("ControlServiceApplication.exitApp(): Closing application context...");
            ExitCodeGenerator exitCodeGenerator = () -> {
                log.info("ControlServiceApplication.exitApp(): exitCodeGenerator: Exit code: {}", exitCode);
                return exitCode;
            };
            SpringApplication.exit(applicationContext, exitCodeGenerator);
            log.info("ControlServiceApplication.exitApp(): Exiting with code: {}", exitCode);
            System.exit(exitCode);

        } else {
            log.warn("ControlServiceApplication.exitApp(): Exit timer has already started: {}", exitTimer);
        }
    }
}