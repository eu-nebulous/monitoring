/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gr.iccs.imu.ems.baguette.client.install.api.INodeRegistration;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsService;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsSet;
import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import jakarta.jms.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask.TASK_TYPE;

/**
 * Installation Event Listener
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientInstallationRequestListener implements InitializingBean {
    private final ClientInstallationProperties properties;
    private final InstructionsService instructionsService;
    private final INodeRegistration nodeRegistration;
    private final BrokerCepService brokerCepService;
    private final ClientInstaller clientInstaller;
    private final BaguetteServer baguetteServer;
    private final ObjectMapper objectMapper;

    private Map<String,List<InstructionsSet>> instructionsSetMap;

    @Override
    public void afterPropertiesSet() throws JMSException {
        initializeInstructionSet();
        connectToBroker();
    }

    private void initializeInstructionSet() {
        Map<String, List<String>> instructionsSetConfig = properties.getInstructions().entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .filter(entry -> entry.getKey().contains("_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        log.debug("InstallationEventListener: Instructions sets configuration: {}", instructionsSetConfig);
        if (instructionsSetConfig.isEmpty())
            log.warn("InstallationEventListener: No instructions sets found");

        instructionsSetMap = new HashMap<>();
        instructionsSetConfig.forEach((name, value) -> {
            if (value == null || value.isEmpty()) {
                log.warn("InstallationEventListener: Instructions sets map is empty: {}", name);
            } else {
                try {
                    for (String fileName : value) {
                        if (StringUtils.isBlank(fileName)) continue;
                        InstructionsSet instructionsSet = instructionsService.loadInstructionsFile(fileName);
                        instructionsSetMap.computeIfAbsent(name, k -> new ArrayList<>()).add(instructionsSet);
                    }
                } catch (Exception e) {
                    log.error("InstallationEventListener: ERROR: while loading instructions set: {}", name);
                }
            }
        });
        log.debug("InstallationEventListener: Instructions sets loaded: {}", instructionsSetMap);
    }

    private void connectToBroker() throws JMSException {
        log.debug("InstallationEventListener: Connecting to local broker");
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                brokerCepService.getBrokerCepProperties().getBrokerUrlForConsumer());
        ActiveMQConnection connection = (ActiveMQConnection) (StringUtils.isNotBlank(brokerCepService.getBrokerUsername())
                ? connectionFactory.createConnection(brokerCepService.getBrokerUsername(), brokerCepService.getBrokerPassword())
                : connectionFactory.createConnection());
        Session session = connection.createSession(true, 0);

        List<String> topics = Arrays.asList(
                properties.getClientInstallationRequestsTopic(),
                properties.getClientInfoRequestsTopic()
        );
        log.debug("InstallationEventListener: Will subscribe to topics: {}", topics);

        MessageListener listener = getMessageListener();
        for (String topic : topics) {
            MessageConsumer consumer = session.createConsumer(
                    new ActiveMQTopic( topic ));
            consumer.setMessageListener(listener);
            log.debug("InstallationEventListener: Subscribed to topic: {}", topic);
        }

        connection.start();
        log.debug("InstallationEventListener: STARTED");
    }

    private MessageListener getMessageListener() {
        return message -> {
            Map<String, String> request = null;
            TASK_TYPE requestType = TASK_TYPE.OTHER;
            try {
                // Extract request from JMS message
                request = extractRequest(message);
                log.debug("InstallationEventListener: Got a client installation request: {}", request);
                if (request==null) {
                    clientInstaller.sendErrorClientInstallationReport(TASK_TYPE.OTHER,
                            null, "ERROR: Invalid request. Could not extract request data");
                    return;
                }

                // Get request type
                String requestTypeStr = request.get("requestType");
                if (StringUtils.isBlank(requestTypeStr)) {
                    clientInstaller.sendErrorClientInstallationReport(TASK_TYPE.OTHER,
                            request, "ERROR: Invalid request. Missing requestType field");
                    return;
                }
                try {
                    if ("VM".equalsIgnoreCase(requestTypeStr)) requestTypeStr = TASK_TYPE.INSTALL.name();
                    if ("REMOVE".equalsIgnoreCase(requestTypeStr)) requestTypeStr = TASK_TYPE.UNINSTALL.name();
                    if ("UPDATE".equalsIgnoreCase(requestTypeStr)) requestTypeStr = TASK_TYPE.INFO.name();
                    requestType = TASK_TYPE.valueOf(requestTypeStr.trim().toUpperCase());
                } catch (Exception e) {
                    clientInstaller.sendErrorClientInstallationReport(TASK_TYPE.OTHER,
                            request, "ERROR: Invalid request. Invalid requestType field value: "+requestTypeStr);
                    return;
                }

                // If not an INFO or NODE_DETAILS request run extra checks
                if (TASK_TYPE.INFO != requestType && TASK_TYPE.NODE_DETAILS != requestType) {
                    // Check incoming request
                    List<String> errors = new ArrayList<>();
                    if (StringUtils.isBlank(request.get("requestId")) &&
                            (TASK_TYPE.DIAGNOSTICS==requestType || TASK_TYPE.INSTALL==requestType))
                        errors.add("requestId");
                    if (StringUtils.isBlank(request.get("deviceOs"))) errors.add("deviceOs");
                    if (StringUtils.isBlank(request.get("deviceIpAddress"))) errors.add("deviceIpAddress");
                    if (StringUtils.isBlank(request.get("deviceUsername"))) errors.add("deviceUsername");
                    if (StringUtils.isBlank(request.get("devicePassword"))
                            && StringUtils.isBlank(request.get("devicePublicKey"))) errors.add("Both devicePublicKey and devicePublicKey");
                    if (!errors.isEmpty()) {
                        String errorMessage = "Missing fields: " + String.join(", ", errors);
                        clientInstaller.sendErrorClientInstallationReport(requestType, request, "ERROR: "+errorMessage);
                        return;
                    }
                }

                // Process request based on its type
                switch (requestType) {
                    case DIAGNOSTICS -> processDiagnosticsRequest(request);
                    case INSTALL -> processOnboardingRequest(request);
                    case REINSTALL -> processReinstallRequest(request);
                    case UNINSTALL -> processRemoveRequest(request);
                    case NODE_DETAILS -> processNodeDetailsRequest(request);
                    case INFO -> processInfoRequest(request);
                    default -> throw new IllegalArgumentException("Unsupported request type: "+requestType);
                };

            } catch (Throwable e) {
                log.error("InstallationEventListener: ERROR: ", e);
                try {
                    clientInstaller.sendErrorClientInstallationReport(
                            requestType, request, "ERROR: "+e.getMessage()+"\n"+message);
                } catch (Throwable t) {
                    log.info("InstallationEventListener: EXCEPTION while sending Client installation report for incoming request: request={}, Exception: ", message, t);
                }
            }
        };
    }

    private void processDiagnosticsRequest(Map<String, String> request) {
        String requestId = request.get("requestId").trim();
        log.info("InstallationEventListener: New node DIAGNOSTICS request with Id: {}", requestId);

        // Get instructions set for device
        String normalizedDeviceOs = request.get("deviceOs").trim().toUpperCase();
        String deviceOsFamily = properties.getOsFamilies().entrySet()
                .stream().filter(osFamily ->
                        osFamily.getKey().equals(normalizedDeviceOs)
                                || osFamily.getValue().contains(normalizedDeviceOs))
                .map(Map.Entry::getKey)
                .findAny().orElse(null);
        if (StringUtils.isBlank(deviceOsFamily))
            throw new IllegalArgumentException("Could not resolve node's OS family: requestId="+requestId+", deviceOs="+nodeRegistration);

        String instructionsSetsName = request.get("requestType").trim().toUpperCase() + "_" + deviceOsFamily;
        List<InstructionsSet> instructionsSetsList = instructionsSetMap.get(instructionsSetsName);
        log.debug("InstallationEventListener: instructionsSetsName={}, instructionsSetsList={}", instructionsSetsName, instructionsSetsList);
        if (instructionsSetsList==null || instructionsSetsList.isEmpty()) {
            log.warn("InstallationEventListener: No instructions sets found for request: id={}, instructionsSetsName={}",
                    request.get("requestId"), instructionsSetsName);
            return;
        }

        // Create client installation task
        ClientInstallationTask newTask = ClientInstallationTask.builder()
                .id(request.get("requestId"))
                .taskType(ClientInstallationTask.TASK_TYPE.DIAGNOSTICS)
                .requestId(request.get("requestId"))
                .type(request.get("requestType"))
                .nodeId(request.get("deviceId"))
                .name(request.get("deviceName"))
                .os(request.get("deviceOs"))
                .address(request.get("deviceIpAddress"))
                .nodeRegistryEntry(new NodeRegistryEntry(
                        request.get("deviceIpAddress"), request.get("requestId"), baguetteServer))
                .ssh(SshConfig.builder()
                        .host(request.get("deviceIpAddress"))
                        .port(Integer.parseInt(request.getOrDefault("devicePort", "22")))
                        .username(request.get("deviceUsername"))
                        .password(request.get("devicePassword"))
                        .privateKey(request.get("devicePublicKey"))
                        .build())
                .instructionSets(instructionsSetsList)
                .nodeMustBeInRegistry(false)
                .translationContext(new TranslationContext(requestId))
                .build();

        log.debug("InstallationEventListener: New client installation task: {}", newTask);
        clientInstaller.addTask(newTask);
    }

    private Map<String, String> extractRequest(Message message) throws JMSException, JsonProcessingException {
        if (message instanceof ActiveMQTextMessage textMessage) {
            log.debug("InstallationEventListener: Message payload: {}", textMessage.getText());
            TypeReference<Map<String,String>> typeRef = new TypeReference<>() { };
            return objectMapper.readerFor(typeRef).readValue(textMessage.getText());
        }
        log.warn("InstallationEventListener: IGNORING non-text message: {}", message);
        return null;
    }

    private void processOnboardingRequest(Map<String,String> request) throws Exception {
        String requestId = request.getOrDefault("requestId", "").trim();
        log.info("InstallationEventListener: New node ONBOARDING request with Id: {}", requestId);
        if (StringUtils.isBlank(requestId)) {
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.INSTALL, request, "INVALID REQUEST. MISSING REQUEST ID");
            return;
        }

        try {
            log.debug("InstallationEventListener: Registering node due to ONBOARDING request with Id: {}", requestId);
            nodeRegistration.registerNode(null, convertToNodeInfoMap(request), new TranslationContext(requestId));
        } catch (Exception e) {
            log.warn("InstallationEventListener: EXCEPTION while executing ONBOARDING request with Id: {}\n", requestId, e);
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.INSTALL, request, "ERROR: "+e.getMessage());
        }
    }

    private void processReinstallRequest(Map<String,String> request) throws Exception {
        String requestId = request.getOrDefault("requestId", "").trim();
        String deviceId = request.getOrDefault("deviceId", "").trim();
        String ipAddress = request.getOrDefault("deviceIpAddress", "").trim();
        log.info("InstallationEventListener: REINSTALL request with device Id: {}, ip-address={}", deviceId, ipAddress);
        if (StringUtils.isBlank(deviceId)) {
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.REINSTALL, request, "INVALID REQUEST. MISSING DEVICE ID");
            return;
        }
        if (StringUtils.isBlank(ipAddress)) {
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.REINSTALL, request, "INVALID REQUEST. MISSING DEVICE IP ADDRESS");
            return;
        }

        try {
            log.debug("InstallationEventListener: Reinstalling node due to REINSTALL request with Id: {}", deviceId);
            nodeRegistration.reinstallNode(ipAddress, new TranslationContext(requestId));
        } catch (Exception e) {
            log.warn("InstallationEventListener: EXCEPTION while executing REINSTALL request with Id: {}\n", deviceId, e);
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.REINSTALL, request, "ERROR: "+e.getMessage());
        }
    }

    private void processRemoveRequest(Map<String,String> request) throws Exception {
        String requestId = request.getOrDefault("requestId", "").trim();
        String deviceId = request.getOrDefault("deviceId", "").trim();
        String nodeAddress = request.getOrDefault("deviceIpAddress", "").trim();
        log.info("InstallationEventListener: New node REMOVE request with Id: {}, address={}", deviceId, nodeAddress);
        if (StringUtils.isBlank(deviceId)) {
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.UNINSTALL, request, "INVALID REQUEST. MISSING DEVICE ID");
            return;
        }
        if (StringUtils.isBlank(nodeAddress)) {
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.UNINSTALL, request, "INVALID REQUEST. MISSING IP ADDRESS");
            return;
        }

        try {
            log.debug("InstallationEventListener: Off-boarding node due to REMOVE request with Id: {}, requestId={}", deviceId, requestId);
            nodeRegistration.unregisterNode(nodeAddress, new TranslationContext(requestId));
        } catch (Exception e) {
            log.warn("InstallationEventListener: EXCEPTION while executing REMOVE request with Id: {}\n", deviceId, e);
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.UNINSTALL, request, "ERROR: "+e.getMessage());
        }
    }

    private void processNodeDetailsRequest(Map<String,String> request) throws Exception {
        String nodeAddress = request.getOrDefault("deviceIpAddress", "").trim();
        log.info("InstallationEventListener: New node NODE_DETAILS request with: address={}", nodeAddress);
        if (StringUtils.isBlank(nodeAddress)) {
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.NODE_DETAILS, request, "INVALID REQUEST. MISSING IP ADDRESS");
            return;
        }

        log.info("InstallationEventListener: Processing NODE_DETAILS request");
        try {
            log.debug("InstallationEventListener: Requesting NODE_DETAILS");
            NodeRegistryEntry entry = nodeRegistration.requestNodeDetails(nodeAddress);
            log.trace("InstallationEventListener: NODE_DETAILS: entry={}", entry);

            if (entry!=null) {
                // Get node details from NodeRegistry
                Map<String, Object> response = clientInstaller.createReportEventFromNodeData(
                        -1, TASK_TYPE.NODE_DETAILS, "", "",
                        entry.getIpAddress(), entry.getReference(), entry.getPreregistration(), "SUCCESS");
                log.debug("InstallationEventListener: NODE_DETAILS response (1): {}", response);

                // ...make response map mutable
                response = new LinkedHashMap<>(response);

                // ...include additional fields
                Map<String, String> preregData = entry.getPreregistration();
                response.put("os", preregData.getOrDefault("NODE_OPERATINGSYSTEM", ""));
                response.put("name", preregData.getOrDefault("NODE_NAME", ""));
                response.put("username", preregData.getOrDefault("NODE_SSH_USERNAME", ""));
                response.put("password", preregData.getOrDefault("NODE_SSH_PASSWORD", ""));
                response.put("key", preregData.getOrDefault("NODE_SSH_KEY", ""));

                response.put("requestId", "");
                response.put("state", entry.getState()!=null ? entry.getState().name() : "");
                log.debug("InstallationEventListener: NODE_DETAILS response (2): {}", response);

                // Send NODE_DETAILS response
                log.trace("InstallationEventListener: Sending NODE_DETAILS response: {}", response);
                clientInstaller.publishReport(new LinkedHashMap<>(response));

                log.debug("InstallationEventListener: Sent NODE_DETAILS response: {}", response);
            } else {
                clientInstaller.sendErrorClientInstallationReport(
                        TASK_TYPE.NODE_DETAILS, request, "ERROR: No node found in NodeRegistry with IP address: "+nodeAddress);
            }
        } catch (Exception e) {
            log.warn("InstallationEventListener: EXCEPTION while retrieving NODE_DETAILS:\n", e);
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.INFO, request, "ERROR: "+e.getMessage());
        }
    }

    private void processInfoRequest(Map<String,String> request) throws Exception {
        log.info("InstallationEventListener: INFO request");
        try {
            log.debug("InstallationEventListener: Requesting INFO");
            nodeRegistration.requestInfo();
        } catch (Exception e) {
            log.warn("InstallationEventListener: EXCEPTION while executing INFO:\n", e);
            clientInstaller.sendErrorClientInstallationReport(
                    TASK_TYPE.INFO, request, "ERROR: "+e.getMessage());
        }
    }

    private Map<String, Object> convertToNodeInfoMap(Map<String, String> request) {
        log.trace("InstallationEventListener.convertToNodeInfoMap(): BEGIN: request: {}", request);
        String requestId = request.get("requestId");
        String nodeId = request.get("deviceId");
        String nodeOs = request.get("deviceOs");
        String nodeAddress = request.get("deviceIpAddress");
        String nodeType = request.get("deviceType");
        String nodeName = StringUtils.defaultIfBlank(request.get("deviceName"), nodeId+"@"+nodeAddress);
        String nodeProvider =
                StringUtils.defaultIfBlank(request.get("deviceProvider"), "DEFAULT");
        if (StringUtils.isBlank(nodeType)) nodeType = "VM";

        int port = Integer.parseInt(StringUtils.defaultIfBlank(request.get("devicePort"), "22"));
        if (port<1 || port>65535) port = 22;
        String username = request.get("deviceUsername");
        String password = request.get("devicePassword");
        String publicKey = request.get("devicePublicKey");
        String fingerprint = request.get("deviceFingerprint");

        LinkedHashMap<String, Object> nodeMap = new LinkedHashMap<>(Map.of(
                "id", nodeId,
                "requestId", requestId,
                "operatingSystem", nodeOs,
                "address", nodeAddress,
                "type", nodeType,
                "name", nodeName,
                "provider", nodeProvider,
                "timestamp", Instant.now().toString()
        ));
        nodeMap.putAll(Map.of(
                "ssh.address", nodeAddress,
                "ssh.port", String.valueOf(port),
                "ssh.username", username,
                "ssh.password", StringUtils.defaultIfBlank(password, ""),
                "ssh.key", StringUtils.defaultIfBlank(publicKey, ""),
                "ssh.fingerprint", StringUtils.defaultIfBlank(fingerprint, "")
        ));
        log.trace("InstallationEventListener.convertToNodeInfoMap(): END: nodeMap: {}", nodeMap);
        return nodeMap;
    }
}
