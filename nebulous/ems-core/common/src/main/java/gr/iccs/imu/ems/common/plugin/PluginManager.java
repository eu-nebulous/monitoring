/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.plugin;

import gr.iccs.imu.ems.util.Plugin;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plugin Manager
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginManager implements InitializingBean {
    private List<Plugin> activePlugins = new LinkedList<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("PluginManager: Started");
    }

    @SafeVarargs
    public final void initializePlugins(Class<? extends Plugin>... pluginClasses) {
        initializePlugins(Arrays.asList(pluginClasses));
    }

    public void initializePlugins(@NonNull List<Class<? extends Plugin>> pluginClasses) {
        pluginClasses.forEach(this::initializePlugin);
    }

    @SneakyThrows
    public synchronized void initializePlugin(Class<? extends Plugin> pluginClass) {
        Plugin plugin = pluginClass.getConstructor().newInstance();
        activePlugins.add(plugin);
        plugin.start();
    }

    public synchronized void stopPlugins() {
        activePlugins.forEach(Plugin::stop);
        activePlugins.clear();
    }

    public synchronized void stopPlugin(@NonNull Plugin plugin) {
        if (activePlugins.contains(plugin)) {
            activePlugins.remove(plugin);
            plugin.stop();
        }
    }

    public List<Plugin> getActivePlugins() {
        return Collections.unmodifiableList(activePlugins);
    }

    public List<Plugin> getActivePlugins(@NonNull Class<? extends Plugin> type) {
        return activePlugins.stream()
                .filter(plugin -> type.isAssignableFrom(plugin.getClass()))
                .collect(Collectors.toList());
    }
}
