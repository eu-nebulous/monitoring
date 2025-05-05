/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.brokerclient.event.EventGenerator;
import gr.iccs.imu.ems.control.ControlServiceApplication;
import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementCoordinator {

    private final ApplicationContext applicationContext;
    private final ControlServiceProperties properties;
    private final ControlServiceCoordinator coordinator;
    private final BrokerCepService brokerCepService;
    private final BaguetteServer baguetteServer;

    private final Map<String, EventGenerator> eventGenerators = new HashMap<>();

    // ------------------------------------------------------------------------------------------------------------
    // Life-Cycle control methods
    // ------------------------------------------------------------------------------------------------------------

    void emsShutdownServices() {
        /*log.info("ManagementCoordinator.emsShutdownServices(): Shutting down EMS...");
        log.info("ManagementCoordinator.emsShutdownServices(): Shutting down EMS... done");*/
        log.warn("ManagementCoordinator.emsShutdownServices(): Not implemented");
    }

    void emsExit() {
        emsExit(properties.getExitCode());
    }

    void emsExit(int exitCode) {
        if (properties.isExitAllowed()) {
            // Signal SpringBootApp to exit
            log.info("ManagementCoordinator.emsExit(): Signaling exit...");
            ControlServiceApplication.exitApp(exitCode, properties.getExitGracePeriod());
            log.info("ManagementCoordinator.emsExit(): Signaling exit... done");
        } else {
            log.warn("ManagementCoordinator.emsExit(): Exit is not allowed");
        }
    }

    public Map<String, Object> getEmsStatus() {
        return Map.of(
                "state", Objects.requireNonNullElse(coordinator.getCurrentEmsState(), "unknown"),
                "message", Objects.requireNonNullElse(coordinator.getCurrentEmsStateMessage(), ""),
                "timestamp", coordinator.getCurrentEmsStateChangeTimestamp()
        );
    }

    // ------------------------------------------------------------------------------------------------------------
    // Event Generation and Debugging methods
    // ------------------------------------------------------------------------------------------------------------

    private final static String EVENT_LOG_OK = "OK";
    private final static String EVENT_LOG_ERROR = "ERROR";
    private final static String BAGUETTE_DISABLED = "BAGUETTE SERVER IS DISABLED";
    private final static String BAGUETTE_NOT_RUNNING = "BAGUETTE SERVER IS NOT RUNNING";

    private String eventLogEnd(String method, String result) {
        log.debug("ManagementCoordinator.{}(): END: result={}", method, result);
        return result;
    }

    private String eventSendCommandToClient(String method, String clientId, String command) {
        // Check status
        if (properties.isSkipBaguette()) return eventLogEnd(method, BAGUETTE_DISABLED);
        if (!baguetteServer.isServerRunning()) return eventLogEnd(method, BAGUETTE_NOT_RUNNING);

        // Send command
        if (clientId.equals("0")) {
            if (command.startsWith("SEND-")) {
                try {
                    String[] part = command.split(" ");
                    String topicName = part[1].trim();
                    String value = part[2].trim();
                    EventMap event = new EventMap(Double.parseDouble(value), 3, System.currentTimeMillis());
                    coordinator.getBrokerCep().publishEvent(null, topicName, event);
                } catch (Exception ex) {
                    log.warn("ManagementCoordinator.{}(): EXCEPTION: command: {}, exception: ", method, command, ex);
                    // Log error
                    return eventLogEnd(method, EVENT_LOG_ERROR+": "+method+": "+ex.getMessage());
                }
            } else if (command.startsWith("GENERATE-EVENTS-START")) {
                String[] args = command.split("[ \t\r\n]+");
                String destination = args[1].trim();
                long interval = Long.parseLong(args[2].trim());
                double lower = Double.parseDouble(args[3].trim());
                double upper = Double.parseDouble(args[4].trim());
                if (eventGenerators.get(destination) == null) {
                    EventGenerator generator = applicationContext.getBean(EventGenerator.class);
                    //generator.setBrokerUrl(null);
                    generator.setBrokerUsername(brokerCepService.getBrokerUsername());
                    generator.setBrokerPassword(brokerCepService.getBrokerPassword());
                    generator.setDestinationName(destination);
                    generator.setLevel(1);
                    generator.setInterval(interval);
                    generator.setLowerValue(lower);
                    generator.setUpperValue(upper);
                    eventGenerators.put(destination, generator);
                    generator.start();
                }

            } else if (command.startsWith("GENERATE-EVENTS-STOP")) {
                String[] args = command.split("[ \t\r\n]+");
                String destination = args[1].trim();
                EventGenerator generator = eventGenerators.remove(destination);
                if (generator != null) {
                    generator.stop();
                }
            } else {
                log.warn("ManagementCoordinator.{}(): ERROR: Unsupported command for client-id=0 : {}", method, command);
                // Log error
                return eventLogEnd(method, EVENT_LOG_ERROR+": "+method+": "+command);
            }
        } else if ("*".equals(clientId))
            baguetteServer.sendToActiveClients(command);
        else
            baguetteServer.sendToClient("#"+clientId, command);

        // Log success
        return eventLogEnd(method, EVENT_LOG_OK);
    }


    // Public API for event debugging
    public String eventGenerationStart(String clientId, String topicName, long interval, double lowerValue, double upperValue) {
        log.debug("ManagementCoordinator.eventGenerationStart(): client={}, topic={}, interval={}, value-range=[{},{}]", clientId, topicName, interval, lowerValue, upperValue);
        String command = String.format(java.util.Locale.ROOT, "GENERATE-EVENTS-START %s %d %f %f", topicName, interval, lowerValue, upperValue);
        return eventSendCommandToClient("eventGenerationStart", clientId, command);
    }

    public String eventGenerationStop(String clientId, String topicName) {
        log.debug("ManagementCoordinator.eventGenerationStop(): client={}, topic={}", clientId, topicName);
        String command = String.format(java.util.Locale.ROOT, "GENERATE-EVENTS-STOP %s", topicName);
        return eventSendCommandToClient("eventGenerationStop", clientId, command);
    }

    public String eventLocalSend(String clientId, String topicName, double value) {
        log.debug("ManagementCoordinator.eventLocalSend(): BEGIN: client={}, topic={}, value={}", clientId, topicName, value);
        String command = String.format(java.util.Locale.ROOT, "SEND-LOCAL-EVENT %s %f", topicName, value);
        return eventSendCommandToClient("eventLocalSend", clientId, command);
    }

    public String eventRemoteSend(String clientId, String brokerUrl, String topicName, double value) {
        log.debug("ManagementCoordinator.eventRemoteSend(): BEGIN: client={}, broker-url={}, topic={}, value={}", clientId, brokerUrl, topicName, value);
        String command = String.format(java.util.Locale.ROOT, "SEND-EVENT %s %s %f", brokerUrl, topicName, value);
        return eventSendCommandToClient("eventRemoteSend", clientId, command);
    }

    // ------------------------------------------------------------------------------------------------------------

    public List<String> clientList() {
        log.debug("ManagementCoordinator.clientList(): BEGIN:");
        return baguetteServer.isServerRunning() ? baguetteServer.getActiveClients() : Collections.emptyList();
    }

    public Map<String, Map<String, String>> clientMap() {
        log.debug("ManagementCoordinator.clientMap(): BEGIN:");
        return baguetteServer.isServerRunning() ? baguetteServer.getActiveClientsMap() : Collections.emptyMap();
    }

    public List<String> passiveClientList() {
        log.debug("ManagementCoordinator.passiveClientList(): BEGIN:");
        return baguetteServer.isServerRunning() ? baguetteServer.getPassiveNodes() : Collections.emptyList();
    }

    public Map<String, Map<String, String>> passiveClientMap() {
        log.debug("ManagementCoordinator.passiveClientMap(): BEGIN:");
        return baguetteServer.isServerRunning() ? baguetteServer.getPassiveNodesMap() : Collections.emptyMap();
    }

    public List<String> allClientList() {
        log.debug("ManagementCoordinator.allClientList(): BEGIN:");
        return baguetteServer.isServerRunning() ? baguetteServer.getAllNodes() : Collections.emptyList();
    }

    public Map<String, Map<String, String>> allClientMap() {
        log.debug("ManagementCoordinator.allClientMap(): BEGIN:");
        return baguetteServer.isServerRunning() ? baguetteServer.getAllNodesMap() : Collections.emptyMap();
    }

    public String clientCommandSend(String clientId, String command) {
        log.debug("ManagementCoordinator.clientCommandSend(): BEGIN: client={}, command={}", clientId, command);
        return eventSendCommandToClient("clientCommandSend", clientId, command);
    }

    public String clusterCommandSend(String clusterId, String command) {
        log.debug("ManagementCoordinator.clusterCommandSend(): BEGIN: cluster={}, command={}", clusterId, command);
        return sendCommandToCluster("clusterCommandSend", clusterId, command);
    }

    private String sendCommandToCluster(String method, String clusterId, String command) {
        // Check status
        if (properties.isSkipBaguette()) return eventLogEnd(method, BAGUETTE_DISABLED);
        if (!baguetteServer.isServerRunning()) return eventLogEnd(method, BAGUETTE_NOT_RUNNING);

        // Send command
        if ("*".equals(clusterId))
            baguetteServer.sendToActiveClusters(command);
        else
            baguetteServer.sendToCluster(clusterId, command);

        // Log success
        return eventLogEnd(method, EVENT_LOG_OK);
    }

    public String clientStats(String clientId) {
        String command = "COLLECT-STATS";
        return clientCommandSend(clientId, command);
    }
}
