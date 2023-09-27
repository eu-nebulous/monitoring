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
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.common.plugin.PluginManager;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private BrokerCepService brokerCepService;
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
            String resultStr = "";
            String errorStr = "";

            // Execute task
            try {
                log.info("ClientInstaller: Executing Client installation Task #{}: task-id={}, node-id={}, name={}, type={}, address={}",
                        taskCnt, task.getId(), task.getNodeId(), task.getName(), task.getType(), task.getAddress());
                long startTm = System.currentTimeMillis();
                boolean result = executeTask(task, taskCnt);
                long endTm = System.currentTimeMillis();
                resultStr = result ? "SUCCESS" : "FAILED";
                log.info("ClientInstaller: Client installation Task #{}: result={}, duration={}ms",
                        taskCnt, resultStr, endTm - startTm);
            } catch (Throwable t) {
                log.info("ClientInstaller: Exception caught in Client installation Task #{}: Exception: ", taskCnt, t);
                errorStr = t.getMessage();
            }

            // Send execution report to local broker
            try {
                resultStr = StringUtils.defaultIfBlank(resultStr, "ERROR: " + errorStr);
                sendClientInstallationReport(taskCnt, task, resultStr);
            } catch (Throwable t) {
                log.info("ClientInstaller: Exception caught while sending Client installation report for Task #{}: Exception: ", taskCnt, t);
            }
        });
    }

    private boolean executeTask(ClientInstallationTask task, long taskCounter) {
        if ("VM".equalsIgnoreCase(task.getType()) || "baremetal".equalsIgnoreCase(task.getType())) {
            if (baguetteServer.getNodeRegistry().getCoordinator()==null)
                throw new IllegalStateException("Baguette Server Coordinator has not yet been initialized");

            return executeVmOrBaremetalTask(task, taskCounter);
        } else
        if ("DIAGNOSTICS".equalsIgnoreCase(task.getType())) {
            return executeDiagnosticsTask(task, taskCounter);
        } else {
            log.error("ClientInstaller: UNSUPPORTED TASK TYPE: {}", task.getType());
        }
        return false;
    }

    private boolean executeVmOrBaremetalTask(ClientInstallationTask task, long taskCounter) {
        NodeRegistryEntry entry = baguetteServer.getNodeRegistry().getNodeByAddress(task.getAddress());
        if (entry==null)
            throw new IllegalStateException("Node entry has been removed from Node Registry before installation: Node IP address: "+ task.getAddress());
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
    }

    private boolean executeDiagnosticsTask(ClientInstallationTask task, long taskCounter) {
        log.debug("ClientInstaller: DIAGNOSTICS: Executing diagnostics task: task-counter={}, task={}", taskCounter, task);
        boolean success = executeVmTask(task, taskCounter);
        log.debug("ClientInstaller: DIAGNOSTICS: After diagnostics execution: \n{}", task);

        log.debug("ClientInstaller: DIAGNOSTICS: Outcome: {}", success ? "Success" : "Error");
        return success;
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

    public void sendClientInstallationReport(long taskCnt, @NonNull ClientInstallationTask task, String resultStr) throws JMSException {
        log.trace("ClientInstaller: Preparing execution report event for Task #{}: result={}, task={}", taskCnt, resultStr, task);
        LinkedHashMap<String, Object> executionReport = new LinkedHashMap<>(
                createReportEventFromExecutionResults(taskCnt, task, resultStr));
        log.info("ClientInstaller: Sending execution report for Task #{}: destination={}, report={}",
                taskCnt, properties.getClientInstallationReportsTopic(), executionReport);
        brokerCepService.publishSerializable(
                null, properties.getClientInstallationReportsTopic(), executionReport, true);
    }

    public void sendClientInstallationReport(String requestId, String resultStr) throws JMSException {
        log.trace("ClientInstaller: Preparing execution report event for request: result={}, requestId={}", resultStr, requestId);
        LinkedHashMap<String, Object> executionReport = new LinkedHashMap<>(
                createReportEvent(requestId, resultStr, Collections.emptyMap()));
        log.info("ClientInstaller: Sending execution report for request: destination={}, report={}",
                properties.getClientInstallationReportsTopic(), executionReport);
        brokerCepService.publishSerializable(
                null, properties.getClientInstallationReportsTopic(), executionReport, true);
    }

    private Map<String, Object> createReportEventFromExecutionResults(long taskCnt, @NonNull ClientInstallationTask task, String resultStr) {
        Map<String, String> data = task.getNodeRegistryEntry().getPreregistration();
        log.trace("ClientInstaller: createReportEventFromExecutionResults: Task #{}: Execution data:\n{}", taskCnt, data);
        Map<String, Object> nodeInfoMap = new LinkedHashMap<>();
        properties.getClientInstallationReportNodeInfoPatterns().forEach(pattern -> {
            log.trace("ClientInstaller: createReportEventFromExecutionResults: Task #{}:Applying pattern: {}", taskCnt, pattern);
            data.keySet().stream()
                    //.peek(key->log.trace("                ---->  Checking:  key={}, match={}", key, pattern.matcher(key).matches()))
                    .filter(key -> pattern.matcher(key).matches())
                    .forEach(key -> nodeInfoMap.put(key, data.get(key)));
        });
        log.debug("ClientInstaller: createReportEventFromExecutionResults: Task #{}: Node info collected: {}", taskCnt, nodeInfoMap);
        return createReportEvent(task.getId(), resultStr, nodeInfoMap);
    }

    private static Map<String, Object> createReportEvent(@NonNull String requestId, @NonNull String statusStr, Map<String, Object> nodeInfoMap) {
        return Map.of(
                "requestId", requestId,
                "status", statusStr,
                "nodeInfo", nodeInfoMap,
                "timestamp", Instant.now().toEpochMilli()
        );
    }
}
