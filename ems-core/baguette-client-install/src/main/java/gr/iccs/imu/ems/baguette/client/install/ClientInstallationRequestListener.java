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

import javax.jms.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
        MessageConsumer consumer = session.createConsumer(
                new ActiveMQTopic(properties.getClientInstallationRequestsTopic()));
        consumer.setMessageListener(getMessageListener());
        connection.start();
        log.debug("InstallationEventListener: STARTED");
    }

    private MessageListener getMessageListener() {
        return message -> {
            String requestId = null;
            try {
                // Extract request from JMS message
                Map<String, String> request = extractRequest(message);
                log.debug("InstallationEventListener: Got a client installation request: {}", request);
                if (request==null)
                    throw new IllegalArgumentException("Could not extract request data");
                requestId = request.get("requestId").trim();

                // Check incoming request
                List<String> errors = new ArrayList<>();
                if (StringUtils.isBlank(request.get("requestId"))) errors.add("requestId");
                if (StringUtils.isBlank(request.get("requestType"))) errors.add("requestType");
                if (StringUtils.isBlank(request.get("deviceOs"))) errors.add("deviceOs");
                if (StringUtils.isBlank(request.get("deviceIpAddress"))) errors.add("deviceIpAddress");
                if (StringUtils.isBlank(request.get("deviceUsername"))) errors.add("deviceUsername");
                if (StringUtils.isBlank(request.get("devicePassword")) && StringUtils.isBlank(request.get("devicePublicKey")))
                    errors.add("Both devicePublicKey and devicePublicKey");
                if (! errors.isEmpty()) {
                    String errorMessage = "Missing fields: " + String.join(", ", errors);
                    throw new IllegalArgumentException(errorMessage);
                }

                // Process request based on its type
                String requestType = request.get("requestType").trim();
                switch (requestType) {
                    case "DIAGNOSTICS" -> processDiagnosticsRequest(request);
                    case "VM" -> processOnboardingRequest(request);
                    default -> throw new IllegalArgumentException("Unsupported request type: "+requestType);
                };

            } catch (Throwable e) {
                log.error("InstallationEventListener: ERROR: ", e);
                try {
                    if (StringUtils.isNotBlank(requestId))
                        clientInstaller.sendClientInstallationReport(requestId, "ERROR: "+e.getMessage());
                    else
                        clientInstaller.sendClientInstallationReport("UNKNOWN-REQUEST-ID", "ERROR: "+e.getMessage()+"\n"+message);
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
        String requestId = request.get("requestId").trim();
        log.info("InstallationEventListener: New node ONBOARDING request with Id: {}", requestId);

        log.debug("InstallationEventListener: Registering node due to ONBOARDING request with Id: {}", requestId);
        nodeRegistration.registerNode(null, convertToNodeInfoMap(request), new TranslationContext(requestId));
    }

    private Map<String, Object> convertToNodeInfoMap(Map<String, String> request) {
        log.trace("InstallationEventListener.convertToNodeInfoMap(): BEGIN: request: {}", request);
        String requestId = request.get("requestId");
        String nodeId = request.get("requestId");
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
