/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.ClientInstaller;
import gr.iccs.imu.ems.baguette.client.install.helper.InstallationHelperFactory;
import gr.iccs.imu.ems.baguette.client.selfhealing.ClientRecoveryPlugin;
import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import gr.iccs.imu.ems.control.properties.StaticResourceProperties;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.util.EventBus;
import gr.iccs.imu.ems.util.NetUtil;
import gr.iccs.imu.ems.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRegistrationCoordinator implements InitializingBean {
    private final ControlServiceProperties properties;
    @Getter
    private final BaguetteServer baguetteServer;
    private final StaticResourceProperties staticResourceProperties;
    private final AtomicBoolean inUse = new AtomicBoolean();

    // Used for retrieving server port and SSL settings
    private final ServerProperties serverProperties;
    private final ServletWebServerApplicationContext webServerAppCtxt;

    private final EventBus<String,Object,Object> eventBus;

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Baguette control methods
    // ------------------------------------------------------------------------------------------------------------

    @Async
    public void stopBaguette() {
        // Acquire lock of this coordinator
        if (!inUse.compareAndSet(false, true)) {
            log.warn("NodeRegistrationCoordinator.stopBaguette(): ERROR: Coordinator is in use. Method exits immediately");
            return;
        }

        try {
            // Stop Baguette server
            log.info("NodeRegistrationCoordinator.stopBaguette(): Stopping Baguette server...");
            baguetteServer.stopServer();
            log.info("NodeRegistrationCoordinator.stopBaguette(): Stopping Baguette server... done");
        } catch (Exception ex) {
            log.error("NodeRegistrationCoordinator.stopBaguette(): EXCEPTION while stopping Baguette server: ", ex);
        } finally {
            // Release lock of this coordinator
            inUse.compareAndSet(true, false);
        }
    }


    // ------------------------------------------------------------------------------------------------------------
    // Node registration methods
    // ------------------------------------------------------------------------------------------------------------

    public String registerNode(HttpServletRequest request, Map<String,Object> nodeMap, TranslationContext translationContext) throws Exception {
        // Get web server base URL
        String baseUrl = calculateBaseUrl(request);
        log.debug("NodeRegistrationCoordinator.registerNode(): baseUrl={}", baseUrl);

        return registerNode(baseUrl, nodeMap, translationContext);
    }

    public String registerNode(String baseUrl, Map<String,Object> nodeMap, TranslationContext translationContext) throws Exception {
        // Pre-process node data passed from SAL (before registering node)
        Map<String, String> nodeMapFlattened = StrUtil.deepFlattenMap(nodeMap);
        log.trace("NodeRegistrationCoordinator.registerNode(): Flattened node info map: {}", nodeMapFlattened);

        // Try to guess base URL
        if (StringUtils.isBlank(baseUrl)) {
            baseUrl = guessBaseUrl();
            log.debug("NodeRegistrationCoordinator.registerNode(): Guessed baseUrl={}", baseUrl);
        }

        // Update node registration info with OS name, BASE_URL, IP_SETTING, and CLIENT_ID
        updateRegistrationInfo(nodeMapFlattened, baseUrl);
        log.trace("NodeRegistrationCoordinator.registerNode(): updated flattened node info map: \n{}", nodeMapFlattened);

        // Update node registration info with APP_ID, APP_NAME, MODEL_NAME, and MODEL_FILE_NAME
        updateTranslationInfo(nodeMapFlattened, translationContext);
        log.trace("NodeRegistrationCoordinator.registerNode(): updated flattened node info map: \n{}", nodeMapFlattened);

        // Register node to Baguette server
        NodeRegistryEntry entry;
        try {
            entry = baguetteServer.registerClient(nodeMapFlattened);
        } catch (Exception e) {
            log.error("NodeRegistrationCoordinator.registerNode(): EXCEPTION while registering node: map={}\n", nodeMap, e);
            //return "ERROR "+e.getMessage();
            throw e;
        }

        // Continue processing according to ExecutionWare type
        String response;
        log.info("NodeRegistrationCoordinator.registerNode(): ExecutionWare: {}", properties.getExecutionware());
        if (properties.getExecutionware()==ControlServiceProperties.ExecutionWare.CLOUDIATOR) {
            response = getClientInstallationInstructions(entry);
        } else {
            response = createClientInstallationTask(entry, translationContext);
        }

        return response;
    }

    public String unregisterNode(@NonNull String ipAddress, TranslationContext translationContext) throws Exception {
        log.debug("NodeRegistrationCoordinator.unregisterNode(): BEGIN: ip-address={}", ipAddress);
        if (StringUtils.isBlank(ipAddress)) {
            log.error("NodeRegistrationCoordinator.unregisterNode(): Blank IP address provided");
            throw new IllegalArgumentException("Blank IP address provided");
        }

        // Retrieve node entry from registry
        NodeRegistryEntry entry = baguetteServer.getNodeRegistry().getNodeByAddress(ipAddress);
        if (entry==null) {
            log.error("NodeRegistrationCoordinator.unregisterNode(): Node not found in registry: address={}", ipAddress);
            throw new IllegalArgumentException("Node not found in registry: address="+ipAddress);
        }

        // Set node state to REMOVING in order to prevent self-healing
        entry.nodeRemoving(null);

        // Signal self-healing plugin to cancel any pending recovery tasks
        log.debug("NodeRegistrationCoordinator.unregisterNode(): Notifying Self-healing to remove any pending recovery tasks: address={}", ipAddress);
        eventBus.send(ClientRecoveryPlugin.CLIENT_REMOVED_TOPIC, entry);

        // Close CSC connection
        ClientShellCommand csc = ClientShellCommand.getActiveByIpAddress(entry.getIpAddress());
        if (csc!=null) {
            log.info("NodeRegistrationCoordinator.unregisterNode(): Closing connection to EMS client: address={}", ipAddress);
            csc.stop("REMOVING NODE");
        } else
            log.warn("NodeRegistrationCoordinator.unregisterNode(): CSC is null. Cannot close connection to EMS client. Probably connection has already closed: address={}", ipAddress);

        // Continue processing according to ExecutionWare type
        String response;
        log.info("NodeRegistrationCoordinator.unregisterNode(): ExecutionWare: {}", properties.getExecutionware());
        if (properties.getExecutionware()==ControlServiceProperties.ExecutionWare.CLOUDIATOR) {
            response = "NOT SUPPORTED";
        } else {
            response = createClientUninstallTask(entry, translationContext, () -> {
                // Unregister and Archive node
                try {
                    entry.nodeRemoved(null);
                    baguetteServer.unregisterClient(entry);
                    return "OK";
                } catch (Exception e) {
                    log.error("NodeRegistrationCoordinator.unregisterNode(): EXCEPTION while unregistering node: address={}, entry={}\n", ipAddress, entry, e);
                    entry.nodeRemoveError(Map.of(
                            "error", e.getMessage()
                    ));
                    return "ERROR " + e.getMessage();
                }
            });
        }

        return response;
    }

    public String reinstallNode(String ipAddress, TranslationContext translationContext) throws Exception {
        // Get node info using IP address
        NodeRegistryEntry nodeInfo = baguetteServer.getNodeRegistry().getNodeByAddress(ipAddress);
        log.info("NodeRegistrationCoordinator.reinstallNode(): Info for node at: ip-address={}, Node Info:\n{}",
                ipAddress, nodeInfo);
        if (nodeInfo==null) {
            log.warn("NodeRegistrationCoordinator.reinstallNode(): Not found pre-registered node with ip-address: {}", ipAddress);
            return "NODE NOT FOUND: "+ipAddress;
        }

        // Continue processing according to ExecutionWare type
        String response;
        log.info("NodeRegistrationCoordinator.reinstallNode(): ExecutionWare: {}", properties.getExecutionware());
        if (properties.getExecutionware() == ControlServiceProperties.ExecutionWare.CLOUDIATOR) {
            response = getClientInstallationInstructions(nodeInfo);
        } else {
            response = createClientReinstallTask(nodeInfo, translationContext);
        }
        return response;
    }

    void updateRegistrationInfo(Map<String, String> nodeMap, String baseUrl) {
        // Set OS info
        String os = StringUtils.isNotBlank(nodeMap.get("operatingSystem.name"))
                ? nodeMap.get("operatingSystem.name")
                : nodeMap.get("operatingSystem");
        nodeMap.put("operatingSystem", os);

        // Get IP Setting and Client ID
        String ipSetting = properties.getIpSetting().toString();
        String clientId = getBaguetteServer().generateClientIdFromNodeInfo(nodeMap);

        // Add to context
        nodeMap.put("BASE_URL", baseUrl);
        nodeMap.put("CLIENT_ID", clientId);
        nodeMap.put("IP_SETTING", ipSetting);
    }

    void updateTranslationInfo(Map<String, String> nodeMap, TranslationContext translationContext) {
        // Add model and application info
        nodeMap.put("TC_MODEL_NAME", StringUtils.defaultIfBlank(translationContext.getModelName(), ""));
        nodeMap.put("TC_MODEL_FILE_NAME", StringUtils.defaultIfBlank(translationContext.getModelFileName(), ""));
        nodeMap.put("TC_APP_ID", StringUtils.defaultIfBlank(translationContext.getAppId(), ""));
        nodeMap.put("TC_APP_NAME", StringUtils.defaultIfBlank(translationContext.getAppName(), ""));
    }

    public String getServerIpAddress() {
        return (properties.getIpSetting() == ControlServiceProperties.IpSetting.DEFAULT_IP)
                ? NetUtil.getDefaultIpAddress()
                : NetUtil.getPublicIpAddress();
    }

    private String getStaticResourcePath() {
        String staticResourceContext = staticResourceProperties.getResourceContext();
        staticResourceContext =  StringUtils.substringBeforeLast(staticResourceContext,"/**");
        staticResourceContext =  StringUtils.substringBeforeLast(staticResourceContext,"/*");
        if (!staticResourceContext.startsWith("/")) staticResourceContext = "/"+staticResourceContext;
        return staticResourceContext;
    }

    public String calculateBaseUrl(HttpServletRequest request) {
        String staticResourceContext = getStaticResourcePath();
        /*String baseUrl =
                request.getScheme()+"://"+ coordinator.getServerIpAddress() +":"+request.getServerPort()+staticResourceContext;*/
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .host(getServerIpAddress())
                .replacePath(staticResourceContext)
                .build().toUriString();
    }

    public String guessBaseUrl() {
        int serverPort = webServerAppCtxt.getWebServer().getPort();
        String staticResourceContext = getStaticResourcePath();
        boolean serverSslEnabled = serverProperties.getSsl().isEnabled();
        String serverSslKeystore = serverProperties.getSsl().getKeyStore();
        String serverSslKeyAlias = serverProperties.getSsl().getKeyAlias();
        log.trace("NodeRegistrationCoordinator.guessBaseUrl: Server: port={}, context={} -- SSL enabled={}, keystore={}, alias={}",
                serverPort, staticResourceContext, serverSslEnabled, serverSslKeystore, serverSslKeyAlias);
        String baseUrl = ServletUriComponentsBuilder.newInstance()
                .scheme( serverSslEnabled ? "https" : "http" )
                .host(getServerIpAddress())
                .port(serverPort)
                .path(staticResourceContext)
                .build().toString();
        log.trace("NodeRegistrationCoordinator.guessBaseUrl: baseUrl={}", baseUrl);
        return baseUrl;
    }

    // Retained for backward compatibility with Cloudiator
    @SneakyThrows
    public String getClientInstallationInstructions(NodeRegistryEntry entry) throws IOException {
        // Prepare Baguette Client installation instructions for node
        final String CLOUDIATOR_HELPER_CLASS = "gr.iccs.imu.ems.extra.cloudiator.CloudiatorInstallationHelper";
        String json = InstallationHelperFactory.getInstance()
                .createInstallationHelperBean(CLOUDIATOR_HELPER_CLASS, entry)
                .getInstallationInstructionsForOs(entry)
                .orElse(Collections.emptyList())
                .stream().findFirst()
                .orElse(null);
        if (json==null) {
            log.warn("NodeRegistrationCoordinator.getClientInstallationInstructions(): No instruction sets: node-map={}", entry.getPreregistration());
            return null;
        }
        log.debug("NodeRegistrationCoordinator.getClientInstallationInstructions(): instructionsSet: {}", json);

        log.trace("NodeRegistrationCoordinator.getClientInstallationInstructions(): instructionsSet: node-map={}, json:\n{}", entry.getPreregistration(), json);
        return json;
    }

    public String createClientInstallationTask(NodeRegistryEntry entry, TranslationContext translationContext) throws Exception {
        return createClientInstallationTask(entry, translationContext, null);
    }

    public String createClientInstallationTask(NodeRegistryEntry entry, TranslationContext translationContext, Callable<String> callback) throws Exception {
        ClientInstallationTask installationTask = InstallationHelperFactory.getInstance()
                .createInstallationHelper(entry)
                .createClientInstallationTask(entry, translationContext);
        installationTask.setCallback(callback);
        ClientInstaller.instance().addTask(installationTask);
        log.debug("NodeRegistrationCoordinator.createClientInstallationTask(): New installation-task: {}", installationTask);

        return "OK";
    }

    public String createClientReinstallTask(NodeRegistryEntry entry, TranslationContext translationContext) throws Exception {
        return createClientReinstallTask(entry, translationContext, null);
    }

    public String createClientReinstallTask(NodeRegistryEntry entry, TranslationContext translationContext, Callable<String> callback) throws Exception {
        ClientInstallationTask reinstallTask = InstallationHelperFactory.getInstance()
                .createInstallationHelper(entry)
                .createClientReinstallTask(entry, translationContext);
        reinstallTask.setCallback(callback);
        ClientInstaller.instance().addTask(reinstallTask);
        log.debug("NodeRegistrationCoordinator.createClientReinstallTask(): New reinstall-task: {}", reinstallTask);

        return "OK";
    }

    public String createClientUninstallTask(NodeRegistryEntry entry, TranslationContext translationContext) throws Exception {
        return createClientUninstallTask(entry, translationContext, null);
    }

    public String createClientUninstallTask(NodeRegistryEntry entry, TranslationContext translationContext, Callable<String> callback) throws Exception {
        ClientInstallationTask uninstallTask = InstallationHelperFactory.getInstance()
                .createInstallationHelper(entry)
                .createClientUninstallTask(entry, translationContext);
        uninstallTask.setCallback(callback);
        ClientInstaller.instance().addTask(uninstallTask);
        log.debug("NodeRegistrationCoordinator.createClientUninstallTask(): New uninstall-task: {}", uninstallTask);

        return "OK";
    }
}
