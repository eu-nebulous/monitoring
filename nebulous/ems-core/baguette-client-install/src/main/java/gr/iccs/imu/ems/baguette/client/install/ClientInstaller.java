/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install;

import gr.iccs.imu.ems.baguette.client.install.installer.K8sClientInstaller;
import gr.iccs.imu.ems.baguette.client.install.installer.SshClientInstaller;
import gr.iccs.imu.ems.baguette.client.install.installer.SshJsClientInstaller;
import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.common.plugin.PluginManager;
import gr.iccs.imu.ems.util.ConfigWriteService;
import gr.iccs.imu.ems.util.PasswordUtil;
import jakarta.jms.JMSException;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask.TASK_TYPE;

/**
 * Client installer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientInstaller implements InitializingBean {
    private static ClientInstaller singleton;

    private final ClientInstallationProperties properties;
    private final BrokerCepService brokerCepService;
    private final BaguetteServer baguetteServer;
    private final PluginManager pluginManager;

    private final List<ClientInstallerPlugin> clientInstallerPluginList;
    private final ConfigWriteService configWriteService;
    private final PasswordUtil passwordUtil;

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
            String callbackStr = "";
            String errorStr = "";

            // Execute task
            boolean result = false;
            try {
                log.info("ClientInstaller: Executing Client installation Task #{}: task-id={}, node-id={}, name={}, type={}, address={}",
                        taskCnt, task.getId(), task.getNodeId(), task.getName(), task.getType(), task.getAddress());
                long startTm = System.currentTimeMillis();
                result = executeTask(task, taskCnt);
                long endTm = System.currentTimeMillis();
                resultStr = result ? "SUCCESS" : "FAILED";
                log.info("ClientInstaller: Client installation Task #{}: result={}, duration={}ms",
                        taskCnt, resultStr, endTm - startTm);
            } catch (Throwable t) {
                log.error("ClientInstaller: Exception caught in Client installation Task #{}: Exception: ", taskCnt, t);
                errorStr = "EXCEPTION " + t.getMessage();
            }

            // Run callback (if any)
            if (result && task.getCallback()!=null) {
                try {
                    log.debug("ClientInstaller: CALLBACK started: Task #{}: task-id={}", taskCnt, task.getId());
                    long startTm = System.currentTimeMillis();
                    callbackStr = task.getCallback().call();
                    long endTm = System.currentTimeMillis();
                    log.info("ClientInstaller: CALLBACK completed: Task #{}: callback-result={}, duration={}ms", taskCnt, callbackStr, endTm - startTm);
                    if (! "OK".equalsIgnoreCase(callbackStr)) resultStr = "FAILED";
                } catch (Throwable t) {
                    log.error("ClientInstaller: CALLBACK: Exception caught while running callback of Client installation Task #{}: Exception: ", taskCnt, t);
                    callbackStr = "CALLBACK-EXCEPTION " + t.getMessage();
                    resultStr = "FAILED";
                }
            } else {
                if (result)
                    log.debug("ClientInstaller: No CALLBACK found for Task #{}", taskCnt);
                else
                    log.debug("ClientInstaller: Skipped CALLBACK because execution failed for Task #{}", taskCnt);
            }

            // Send execution report to local broker
            try {
                resultStr = StringUtils.defaultIfBlank(resultStr, "ERROR: " + errorStr + " " + callbackStr);
                sendSuccessClientInstallationReport(taskCnt, task, resultStr);
            } catch (Throwable t) {
                log.info("ClientInstaller: EXCEPTION while sending Client installation report for Task #{}: Exception: ", taskCnt, t);
            }
        });
    }

    private boolean executeTask(ClientInstallationTask task, long taskCounter) {
        if ("VM".equalsIgnoreCase(task.getType()) || "baremetal".equalsIgnoreCase(task.getType())) {
            if (baguetteServer.getNodeRegistry().getCoordinator()==null)
                throw new IllegalStateException("Baguette Server Coordinator has not yet been initialized");

            return executeVmOrBaremetalTask(task, taskCounter);
        } else
        if ("K8S".equalsIgnoreCase(task.getType())) {
            if (baguetteServer.getNodeRegistry().getCoordinator()==null)
                throw new IllegalStateException("Baguette Server Coordinator has not yet been initialized");

            return executeKubernetesTask(task, taskCounter);
        } else
        //if ("DIAGNOSTICS".equalsIgnoreCase(task.getType())) {
        if (task.getTaskType()==TASK_TYPE.DIAGNOSTICS) {
            return executeDiagnosticsTask(task, taskCounter);
        } else {
            log.error("ClientInstaller: UNSUPPORTED TASK TYPE: {}", task.getType());
        }
        return false;
    }

    private boolean executeInstaller(ClientInstallationTask task, long taskCounter,
                                     BiFunction<ClientInstallationTask,Long,Boolean> condition,
                                     BiFunction<ClientInstallationTask,Long,Boolean> installerExecution)
    {
        NodeRegistryEntry entry = baguetteServer.getNodeRegistry().getNodeByAddress(task.getAddress());
        if (task.isNodeMustBeInRegistry() && entry==null)
            throw new IllegalStateException("Node entry has been removed from Node Registry before installation: Node IP address: "+ task.getAddress());
        //baguetteServer.handleNodeSituation(task.getAddress(), INTERNAL_ERROR);

        // ClientInstallationRequestListener submits tasks without pre-registered nodes
        if (! task.isNodeMustBeInRegistry())
            entry = task.getNodeRegistryEntry();

        // Check if node is being removed or have been archived
        if (task.getNodeRegistryEntry().isArchived()) {
            log.warn("ClientInstaller: Node is being removed or has been archived: {}", properties.getInstallationContextProcessorPlugins());
            throw new IllegalStateException("Node is being removed or has been archived: Node IP address: "+ task.getAddress());
        }

        boolean success;
        if (condition==null || condition.apply(task, taskCounter)) {
            // Starting installation
            entry.nodeInstalling(task);

            // Call InstallationContextPlugin's before installation
            log.debug("ClientInstaller: PRE-INSTALLATION: Calling installation context processors: {}", properties.getInstallationContextProcessorPlugins());
            pluginManager.getActivePlugins(InstallationContextProcessorPlugin.class)
                    .forEach(plugin -> ((InstallationContextProcessorPlugin) plugin).processBeforeInstallation(task, taskCounter));

            log.debug("ClientInstaller: INSTALLATION: Executing installation task: task-counter={}, task={}", taskCounter, task);
            success = installerExecution.apply(task, taskCounter);
            log.debug("ClientInstaller: NODE_REGISTRY_ENTRY after installation execution: \n{}", task.getNodeRegistryEntry());

            if (entry.getState() == NodeRegistryEntry.STATE.INSTALLING) {
                log.warn("ClientInstaller: NODE_REGISTRY_ENTRY status is still INSTALLING after executing client installation. Changing to INSTALL_ERROR");
                entry.nodeInstallationError(null);
            }

            // Call InstallationContextPlugin's after installation
            log.debug("ClientInstaller: POST-INSTALLATION: Calling installation context processors: {}", properties.getInstallationContextProcessorPlugins());
            pluginManager.getActivePlugins(InstallationContextProcessorPlugin.class)
                    .forEach(plugin -> ((InstallationContextProcessorPlugin) plugin).processAfterInstallation(task, taskCounter, success));
        } else {
            log.debug("ClientInstaller: SKIP INSTALLATION: due to condition. Skipping execution: Node IP address: "+ task.getAddress());
            success = true;
        }

        // Pre-register Node to baguette Server Coordinator
        if (task.getTaskType()==TASK_TYPE.INSTALL) {
            log.debug("ClientInstaller: POST-INSTALLATION: Node is being pre-registered: {}", entry);
            baguetteServer.getNodeRegistry().getCoordinator().preregister(entry);
        }

        // Un-register Node from baguette Server Coordinator
        if (task.getTaskType()==TASK_TYPE.UNINSTALL) {
            ClientShellCommand csc = ClientShellCommand.getActiveByIpAddress(entry.getIpAddress());
            log.debug("ClientInstaller: POST-INSTALLATION: CSC of node to be unregistered: {}", csc);
            if (csc!=null) {
                log.debug("ClientInstaller: POST-INSTALLATION: Node is going to be unregistered: {}", entry);
                baguetteServer.getNodeRegistry().getCoordinator().unregister(csc);
            }
        }

        log.debug("ClientInstaller: Installation outcome: {}", success ? "Success" : "Error");
        return success;
    }

    private boolean executeVmOrBaremetalTask(ClientInstallationTask task, long taskCounter) {
        return executeInstaller(task, taskCounter, (t,c) -> ! t.getInstructionSets().isEmpty(), this::executeVmTask);
    }

    private boolean executeKubernetesTask(ClientInstallationTask task, long taskCounter) {
        return executeInstaller(task, taskCounter, (t,c) -> true, (t,c) -> {
            boolean result;
            log.info("ClientInstaller: Using K8sClientInstaller for task #{}", taskCounter);
            result = K8sClientInstaller.builder()
                    .task(task)
                    .taskCounter(taskCounter)
                    .properties(properties)
                    .configWriteService(configWriteService)
                    .passwordUtil(passwordUtil)
                    .build()
                    .execute();
            log.info("ClientInstaller: Task execution result #{}: success={}", taskCounter, result);
            return result;
        });
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

    public void sendSuccessClientInstallationReport(long taskCnt, @NonNull ClientInstallationTask task, String resultStr) throws JMSException {
        log.trace("ClientInstaller: Preparing SUCCESS execution report event for Task #{}: result={}, task={}", taskCnt, resultStr, task);
        LinkedHashMap<String, Object> executionReport = new LinkedHashMap<>(
                createReportEventFromExecutionResults(taskCnt, task, resultStr));
        log.info("ClientInstaller: Sending SUCCESS execution report for Task #{}: destination={}, report={}",
                taskCnt, properties.getClientInstallationReportsTopic(), executionReport);
        publishReport(executionReport);
    }

    public void sendErrorClientInstallationReport(@NonNull TASK_TYPE requestType, Map<String,String> request, String resultStr) throws JMSException {
        sendErrorClientInstallationReport(requestType, request, null, resultStr);
    }

    public void sendErrorClientInstallationReport(@NonNull TASK_TYPE requestType, Map<String,String> request, String reference, String resultStr) throws JMSException {
        log.trace("ClientInstaller: Preparing ERROR execution report event for request: result={}, request={}", resultStr, request);
        String requestId = getOrDefault(request, "requestId", "MISSING-REQUEST-ID");
        String deviceId  = getOrDefault(request, "deviceId", "MISSING-DEVICE-ID");
        String ipAddress = getOrDefault(request, "deviceIpAddress", "MISSING-DEVICE-ADDRESS");
        LinkedHashMap<String, Object> executionReport = new LinkedHashMap<>(
                createReportEvent(requestType,
                        requestId, deviceId, ipAddress, reference, resultStr, Collections.emptyMap()));
        log.info("ClientInstaller: Sending ERROR execution report: destination={}, report={}",
                properties.getClientInstallationReportsTopic(), executionReport);
        publishReport(executionReport);
    }

    private String getOrDefault(Map<String,String> map, String key, String defaultValue) {
        if (map==null) return defaultValue;
        return map.getOrDefault(key, defaultValue);
    }

    public void publishReport(Serializable report) throws JMSException {
        brokerCepService.publishSerializable(
                null, properties.getClientInstallationReportsTopic(), report, true);
    }

    private Map<String, Object> createReportEventFromExecutionResults(long taskCnt, @NonNull ClientInstallationTask task, String resultStr) {
        log.trace("ClientInstaller: createReportEventFromExecutionResults: Task #{}: ARGS: result={}, task={}", taskCnt, resultStr, task);

        // Get execution results
        Map<String, String> data = task.getNodeRegistryEntry().getPreregistration();
        log.trace("ClientInstaller: createReportEventFromExecutionResults: Task #{}: Execution data:\n{}", taskCnt, data);

        // Create report event
        TASK_TYPE requestType = task.getTaskType();
        String requestId = task.getRequestId();
        String deviceId = task.getNodeId();
        return createReportEventFromNodeData(taskCnt, requestType, requestId, deviceId,
                task.getAddress(), task.getNodeRegistryEntry().getReference(), data, resultStr);
    }

    public Map<String, Object> createReportEventFromNodeData(long taskCnt,
                                                             TASK_TYPE requestType,
                                                             String requestId,
                                                             String deviceId,
                                                             String ipAddress,
                                                             String reference,
                                                             Map<String, String> data,
                                                             String resultStr)
    {
        // Copy node info from execution results
        Map<String, Object> nodeInfoMap = new LinkedHashMap<>();
        properties.getClientInstallationReportNodeInfoPatterns().forEach(pattern -> {
            log.trace("ClientInstaller: createReportEventFromNodeData: Task #{}: Applying pattern: {}", taskCnt, pattern);
            data.keySet().stream()
                    //.peek(key->log.trace("                ---->  Checking:  key={}, match={}", key, pattern.matcher(key).matches()))
                    .filter(key -> pattern.matcher(key).matches())
                    .forEach(key -> nodeInfoMap.put(key, data.get(key)));
        });
        log.debug("ClientInstaller: createReportEventFromNodeData: Task #{}: Node info collected: {}", taskCnt, nodeInfoMap);

        // Create and send report event
        return createReportEvent(
                requestType, requestId, deviceId, ipAddress, reference, resultStr, nodeInfoMap);
    }

    private static Map<String, Object> createReportEvent(@NonNull TASK_TYPE requestType,
                                                         String requestId,
                                                         String deviceId,
                                                         String ipAddress,
                                                         String reference,
                                                         @NonNull String statusStr,
                                                         Map<String, Object> nodeInfoMap)
    {
        return Map.of(
                "requestType", requestType.name(),
                "requestId", Objects.requireNonNullElse(requestId, ""),
                "deviceId", Objects.requireNonNullElse(deviceId, ""),
                "deviceIpAddress", Objects.requireNonNullElse(ipAddress, ""),
                "reference", Objects.requireNonNullElse(reference, ""),
                "status", statusStr,
                "nodeInfo", nodeInfoMap!=null ? nodeInfoMap : Collections.emptyMap(),
                "timestamp", Instant.now().toEpochMilli()
        );
    }
}
