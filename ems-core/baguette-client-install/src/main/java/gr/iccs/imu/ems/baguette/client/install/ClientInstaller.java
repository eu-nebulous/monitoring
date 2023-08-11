/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install;

import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.common.plugin.PluginManager;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client installer
 */
@Slf4j
@Service
@NoArgsConstructor
public class ClientInstaller implements InitializingBean {
    private static ClientInstaller singleton;

    @Autowired
    private ClientInstallationProperties properties;
    @Autowired
    private BaguetteServer baguetteServer;
    @Autowired
    private PluginManager pluginManager;

    private final AtomicLong taskCounter = new AtomicLong();
    private ExecutorService executorService;

    @Override
    public void afterPropertiesSet() {
        singleton = this;
        executorService = Executors.newFixedThreadPool(properties.getWorkers());
        properties.getInstallationContextProcessorPlugins().forEach(pluginClass -> {
            log.debug("ClientInstaller: Initializing plugin: {}", pluginClass);
            pluginManager.initializePlugin(pluginClass);
        });
    }

    public static ClientInstaller instance() { return singleton; }

    public void addTask(@NotNull ClientInstallationTask task) {
        executorService.submit(() -> {
            long taskCnt = taskCounter.getAndIncrement();
            try {
                log.info("ClientInstaller: Executing Client installation Task #{}: task-id={}, node-id={}, name={}, type={}, address={}",
                        taskCnt, task.getId(), task.getNodeId(), task.getName(), task.getType(), task.getAddress());
                long startTm = System.currentTimeMillis();
                boolean result = executeTask(task, taskCnt);
                long endTm = System.currentTimeMillis();
                log.info("ClientInstaller: Client installation Task #{}: result={}, duration={}ms",
                        taskCnt, result ? "SUCCESS" : "FAILED", endTm - startTm);
            } catch (Throwable t) {
                log.info("ClientInstaller: Exception caught in Client installation Task #{}: Exception: ", taskCnt, t);
            }
        });
    }

    private boolean executeTask(ClientInstallationTask task, long taskCounter) {
        if (baguetteServer.getNodeRegistry().getCoordinator()==null)
            throw new IllegalStateException("Baguette Server Coordinator has not yet been initialized");

        if ("VM".equalsIgnoreCase(task.getType()) || "baremetal".equalsIgnoreCase(task.getType())) {
            NodeRegistryEntry entry = baguetteServer.getNodeRegistry().getNodeByAddress(task.getAddress());
            if (entry==null)
                throw new IllegalStateException("Node entry has been removed from Node Registry before installation: Node IP address: "+task.getAddress());
                //baguetteServer.handleNodeSituation(task.getAddress(), INTERNAL_ERROR);
            entry.nodeInstalling(task);

            // Call InstallationContextPlugin's before installation
            log.debug("ClientInstaller: PRE-INSTALLATION: Calling installation context processors: {}", properties.getInstallationContextProcessorPlugins());
            pluginManager.getActivePlugins(InstallationContextProcessorPlugin.class)
                    .forEach(plugin->((InstallationContextProcessorPlugin)plugin).processBeforeInstallation(task, taskCounter));

            log.debug("ClientInstaller: INSTALLATION: Executing installation task: task-counter={}, task={}", taskCounter, task);
            boolean success = executeVmTask(task, taskCounter);
            log.debug("ClientInstaller: NODE_REGISTRY_ENTRY after installation execution: \n{}", task.getNodeRegistryEntry());

            if (entry.getState()==NodeRegistryEntry.STATE.INSTALLING) {
                log.warn("ClientInstaller: NODE_REGISTRY_ENTRY status is still INSTALLING after executing client installation. Changing to INSTALL_ERROR");
                entry.nodeInstallationError(null);
            }

            // Call InstallationContextPlugin's after installation
            log.debug("ClientInstaller: POST-INSTALLATION: Calling installation context processors: {}", properties.getInstallationContextProcessorPlugins());
            pluginManager.getActivePlugins(InstallationContextProcessorPlugin.class)
                    .forEach(plugin->((InstallationContextProcessorPlugin)plugin).processAfterInstallation(task, taskCounter, success));

            // Pre-register Node to baguette Server Coordinator
            log.debug("ClientInstaller: POST-INSTALLATION: Node is being pre-registered: {}", entry);
            baguetteServer.getNodeRegistry().getCoordinator().preregister(entry);

            log.debug("ClientInstaller: Installation outcome: {}", success ? "Success" : "Error");
            return success;
        } else {
            log.error("ClientInstaller: UNSUPPORTED TASK TYPE: {}", task.getType());
        }
        return false;
    }

    private boolean executeVmTask(ClientInstallationTask task, long taskCounter) {
        // Select the appropriate client installer plugin to run installation task.
        // Currently, two installer plugins are available: SshClientInstaller, and SshJsClientInstaller
        boolean result;
        if (properties.getInstallerType()==ClientInstallationProperties.INSTALLER_TYPE.JS_INSTALLER) {
            log.info("ClientInstaller: Using SshJsClientInstaller for task #{}", taskCounter);
            result = SshJsClientInstaller.jsBuilder()
                    .task(task)
                    .taskCounter(taskCounter)
                    .properties(properties)
                    .build()
                    .execute();
        } else {
            log.info("ClientInstaller: Using SshClientInstaller (default) for task #{}", taskCounter);
            result = SshClientInstaller.builder()
                    .task(task)
                    .taskCounter(taskCounter)
                    /*.maxRetries(properties.getMaxRetries())
                    .authenticationTimeout(properties.getAuthenticateTimeout())
                    .connectTimeout(properties.getConnectTimeout())
                    .heartbeatInterval(properties.getHeartbeatInterval())
                    .simulateConnection(properties.isSimulateConnection())
                    .simulateExecution(properties.isSimulateExecution())
                    .commandExecutionTimeout(properties.getCommandExecutionTimeout())*/
                    .properties(properties)
                    .build()
                    .execute();
        }
        log.info("ClientInstaller: Task execution result #{}: success={}", taskCounter, result);
        return result;
    }
}
