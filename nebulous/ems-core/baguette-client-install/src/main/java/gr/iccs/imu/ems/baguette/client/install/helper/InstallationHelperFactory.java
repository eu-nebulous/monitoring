/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.helper;

import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Installation helper factory
 */
@Slf4j
@Service
public class InstallationHelperFactory implements InitializingBean {
    private static InstallationHelperFactory instance;

    public synchronized static InstallationHelperFactory getInstance() { return instance; }

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void afterPropertiesSet() {
        InstallationHelperFactory.instance = this;
    }

    public InstallationHelper createInstallationHelper(NodeRegistryEntry entry) {
        String nodeType = entry.getPreregistration().get("type");
        if ("VM".equalsIgnoreCase(nodeType) || "baremetal".equalsIgnoreCase(nodeType)) {
            return createVmInstallationHelper(entry);
        }
        throw new IllegalArgumentException("Unsupported or missing Node type: "+nodeType);
    }

    public InstallationHelper createInstallationHelperBean(String className, NodeRegistryEntry entry) throws ClassNotFoundException {
        Class<?> clzz = Class.forName(className);
        return (InstallationHelper) applicationContext.getBean(clzz);
    }

    public InstallationHelper createInstallationHelperInstance(String className, Map<String,Object> nodeMap)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        Class<?> clzz = Class.forName(className);
        return (InstallationHelper) clzz.getDeclaredMethod("getInstance").invoke(null);
    }

    private InstallationHelper createVmInstallationHelper(NodeRegistryEntry entry) {
        return VmInstallationHelper.getInstance();
    }
}
