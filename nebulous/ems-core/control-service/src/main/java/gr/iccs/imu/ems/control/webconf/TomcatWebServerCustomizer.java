/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.webconf;

import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import gr.iccs.imu.ems.util.KeystoreUtil;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TomcatWebServerCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final ControlServiceProperties properties;
    private final PasswordUtil passwordUtil;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (factory.getSsl() != null && factory.getSsl().isEnabled()) {
            try {
                log.debug("TomcatWebServerCustomizer: ControlServiceProperties: {}", properties);
                log.debug("TomcatWebServerCustomizer: Keystore password: {}", passwordUtil.encodePassword(properties.getSsl().getKeystorePassword()));
                log.debug("TomcatWebServerCustomizer: Truststore password: {}", passwordUtil.encodePassword(properties.getSsl().getTruststorePassword()));

                log.debug("TomcatWebServerCustomizer: Initializing HTTPS keystore, truststore and certificate...");
                KeystoreUtil.initializeKeystoresAndCertificate(properties.getSsl(), passwordUtil);
                log.debug("TomcatWebServerCustomizer: Initializing HTTPS keystore, truststore and certificate... done");
            } catch (Exception e) {
                log.error("TomcatWebServerCustomizer: EXCEPTION while initializing HTTPS keystore, truststore and certificate:\n", e);
                throw new RuntimeException(e);
            }
        }
    }

    /*
    import org.apache.catalina.connector.Connector;
    import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
    import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
    import org.springframework.context.annotation.Bean;

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
    }*/
}