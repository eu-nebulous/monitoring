/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.InfoProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.StreamSupport;

@Slf4j
@Component
public class BuildInfoProvider implements ApplicationContextAware, IEmsInfoProvider {
    @Autowired
    private ControlServiceProperties properties;
    @Autowired
    private BuildProperties buildProperties;

    private Map<String,Object> infoMap;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.infoMap = new HashMap<>();
        collectBuildInfo(applicationContext, infoMap);
    }

    @Override
    public Map<String,Object> getMetricValues() { return infoMap; }

    @SneakyThrows
    protected void collectBuildInfo(ApplicationContext applicationContext, Map<String, Object> infoMap) {
        // Collect info from 'BuildProperties'
        print("\n--------------------------------------------------------------------------------");
        print("===== Build Properties =====");
        final Map<String,Object> map = new LinkedHashMap<>();
        StreamSupport.stream(Spliterators.spliteratorUnknownSize(buildProperties.iterator(), Spliterator.ORDERED), false)
                .sorted(Comparator.comparing(InfoProperties.Entry::getKey))
                .forEach(e->{
                    print(" - {} = {}", e.getKey(), e.getValue());
                    map.put(e.getKey(), e.getValue());
                });
        infoMap.put("buildProperties", map);
        print("\n--------------------------------------------------------------------------------");

        // Collect info from bundled files
        infoMap.put("versionInfo",
                collectInfoFromFile(applicationContext, "Version Info", "classpath:/version.txt"));
        print("\n--------------------------------------------------------------------------------");
        infoMap.put("gitInfo",
                collectInfoFromFile(applicationContext, "Git Info", "classpath:/git.properties"));
        print("\n--------------------------------------------------------------------------------");
        infoMap.put("buildInfo",
                collectInfoFromFile(applicationContext, "Build Info", "classpath:/META-INF/build-info.properties"));
        print("\n--------------------------------------------------------------------------------");
    }

    protected Map<String, Object> collectInfoFromFile(ApplicationContext applicationContext, String title, String resourceStr) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        Resource[] resources = applicationContext.getResources(resourceStr);
        if (resources.length>0) {
            Resource r = resources[0];
            String linesStr = StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8);
            String s = StringUtils.repeat("=", title.length()+12);
            print("\n{}\n===== {} =====\n{}\n=== File: {}\n=== URL:  {}\n\n{}\n", s, title, s, r.getFilename(), r.getURL(), linesStr);
            Properties p;
            try (StringReader sr = new StringReader(linesStr)) {
                p = new Properties();
                p.load(sr);
            }
            for (final String name: p.stringPropertyNames())
                map.put(name, p.getProperty(name));
        }
        return map;
    }

    protected void print(String formatter, Object...args) {
        if (!properties.isPrintBuildInfo()) return;
        log.info(formatter, args);
    }
}
