/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

@Component
public class NetUtilPostProcessor implements EnvironmentPostProcessor {
    private static final DeferredLog log = new DeferredLog();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        PropertySource<String> ps = new NetUtilPropertySource();
        environment.getPropertySources().addFirst(ps);
        log.info("NetUtilPostProcessor:  NetUtilPropertySource registered (deferred log)");

        application.addInitializers(ctx -> log.replayTo(NetUtilPostProcessor.class));
    }

    @Getter @Setter
    public static class NetUtilPropertySource extends PropertySource<String> {
        private String defaultDefaultIp = "127.0.0.1";
        private String defaultPublicIp = "127.0.0.1";

        public NetUtilPropertySource() {
            super("ems-net-util-property-source");
        }

        public NetUtilPropertySource(String name) {
            super(name);
        }

        @Override
        public String getProperty(String s) {
            String address = null;
            if ("DEFAULT_IP".equals(s)) {
                address = NetUtil.getDefaultIpAddress();
                if (address==null) address = defaultDefaultIp;
            }
            if ("PUBLIC_IP".equals(s)) {
                address = NetUtil.getPublicIpAddress();
                if (address==null) address = defaultPublicIp;
            }
            return address;
        }
    }
}
