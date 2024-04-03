/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
public class ConfigWriteService {
    private final Map<String,Configuration> configurations = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static volatile ConfigWriteService instance;

    public static synchronized ConfigWriteService getInstance() {
        if (instance==null) {
            synchronized (ConfigWriteService.class) {
                if (instance == null) new ConfigWriteService();
            }
        }
        return instance;
    }

    private ConfigWriteService() {
        if (instance!=null)
            throw new IllegalStateException("ConfigWriteService has already been initialized");
        instance = this;
    }

    public Configuration createConfigFile(@NonNull String fileName, String format) {
        if (configurations.containsKey(fileName))
            throw new IllegalArgumentException("Config. file already exists: "+fileName);
        return getOrCreateConfigFile(fileName, format);
    }

    public Configuration getConfigFile(@NonNull String fileName) {
        return configurations.get(fileName);
    }

    public Configuration getOrCreateConfigFile(@NonNull String fileName, String format) {
        if (StringUtils.isBlank(format) && StringUtils.endsWithIgnoreCase(fileName, ".json")) format = "json";
        final Format fmt = EnumUtils.getEnumIgnoreCase(Format.class, format, Format.PROPERTIES);
        return configurations.computeIfAbsent(fileName, s -> new Configuration(Paths.get(fileName), fmt));
    }

    public boolean removeConfigFile(@NonNull String fileName, boolean alsoRemoveFile) {
        Configuration c = configurations.remove(fileName);
        if (alsoRemoveFile && c!=null) {
            if (! c.getConfigPath().toFile().delete()) {
                log.warn("removeConfigFile: Failed to remove config. file from the disk: {}", c.getConfigPath());
            }
            return true;
        }
        return false;
    }

    enum Format { PROPERTIES, JSON }

    @Data
    @RequiredArgsConstructor
    public class Configuration {
        @NonNull private final Path configPath;
        private final Format format;
        private final Map<String,String> contentMap = new LinkedHashMap<>();

        public String get(@NonNull String key) {
            return contentMap.get(key);
        }

        public String getOrDefault(@NonNull String key, String defaultValue) {
            return contentMap.getOrDefault(key, defaultValue);
        }

        public Configuration put(@NonNull String key, String value) throws IOException {
            contentMap.put(key, value);
            write();
            return this;
        }

        public Configuration putAll(@NonNull Map<String,String> map) throws IOException {
            contentMap.putAll(map);
            write();
            return this;
        }

        public void write() throws IOException {
            String content;
            if (format==Format.JSON) content = asJson();
            else content = asProperties();
            Files.writeString(configPath, content);
        }

        private String asProperties() throws IOException {
            try (StringWriter writer = new StringWriter()) {
                Properties p = new Properties();
                p.putAll(contentMap);
                p.store(writer, null);
                return writer.toString();
            }
        }

        private String asJson() {
            return gson.toJson(contentMap);
        }
    }
}
