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
import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import gr.iccs.imu.ems.control.properties.StaticResourceProperties;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.util.NetUtil;
import gr.iccs.imu.ems.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
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

        // Register node to Baguette server
        NodeRegistryEntry entry;
        try {
            entry = baguetteServer.registerClient(nodeMapFlattened);
        } catch (Exception e) {
            log.error("NodeRegistrationCoordinator.registerNode(): EXCEPTION while registering node: map={}\n", nodeMap, e);
            return "ERROR "+e.getMessage();
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
        //log.info("ControlServiceController.baguetteRegisterNodeForProactive(): INPUT: node-map: {}", nodeMap);

        ClientInstallationTask installationTask = InstallationHelperFactory.getInstance()
                .createInstallationHelper(entry)
                .createClientInstallationTask(entry, translationContext);
        ClientInstaller.instance().addTask(installationTask);
        log.debug("NodeRegistrationCoordinator.createClientInstallationTask(): New installation-task: {}", installationTask);

        return "OK";
    }
}
