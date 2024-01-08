/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.jms.JMSException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManagementController {

    private final ControlServiceProperties properties;
    private final ManagementCoordinator coordinator;
    private final TopicBeacon topicBeacon;

    // ------------------------------------------------------------------------------------------------------------
    // Client and Cluster info and control methods
    // ------------------------------------------------------------------------------------------------------------

    @GetMapping(value = "/client/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> listClients() {
        List<String> clients = coordinator.clientList();
        log.info("ManagementController.listClients(): {}", clients);
        return clients;
    }

    @GetMapping(value = "/client/list/map", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Map<String, String>> listClientMaps() {
        Map<String, Map<String, String>> clients = coordinator.clientMap();
        log.info("ManagementController.listClientMaps(): {}", clients);
        return clients;
    }

    @GetMapping("/client/command/{clientId}/{command:.+}")
    public String clientCommand(@PathVariable String clientId, @PathVariable String command) {
        log.info("ManagementController.clientCommand(): PARAMS: client={}, command={}", clientId, command);
        return coordinator.clientCommandSend(clientId, command);
    }

    @GetMapping({ "/client/stats", "/client/stats/{clientId}" })
    public String clientStats(@PathVariable String clientIdOpt) {
        String clientId = StringUtils.defaultIfBlank(clientIdOpt, "*");
        log.info("ManagementController.clientStats(): PARAMS: client={}", clientId);
        return coordinator.clientStats(clientId);
    }

    @GetMapping("/cluster/command/{clusterId}/{command:.+}")
    public String clusterCommand(@PathVariable String clusterId, @PathVariable String command) {
        log.info("ManagementController.clusterCommand(): PARAMS: cluster={}, command={}", clusterId, command);
        return coordinator.clusterCommandSend(clusterId, command);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Event Generation and Debugging methods
    // ------------------------------------------------------------------------------------------------------------

    @GetMapping("/event/generate-start/{clientId}/{topicName}/{interval}/{lowerValue}/{upperValue}")
    public String startEventGeneration(@PathVariable String clientId, @PathVariable String topicName,
                                       @PathVariable long interval, @PathVariable double lowerValue,
                                       @PathVariable double upperValue)
    {
        log.info("ManagementController.startEventGeneration(): PARAMS: client={}, topic={}, interval={}, value-range=[{},{}]",
                clientId, topicName, interval, lowerValue, upperValue);
        return coordinator.eventGenerationStart(clientId, topicName, interval, lowerValue, upperValue);
    }

    @GetMapping("/event/generate-stop/{clientId}/{topicName}")
    public String stopEventGeneration(@PathVariable String clientId, @PathVariable String topicName) {
        log.info("ManagementController.stopEventGeneration(): PARAMS: client={}, topic={}", clientId, topicName);
        return coordinator.eventGenerationStop(clientId, topicName);
    }

    @GetMapping("/event/send/{clientId}/{topicName}/{value}")
    public String sendEvent(@PathVariable String clientId, @PathVariable String topicName, @PathVariable double value) {
        log.info("ManagementController.sendEvent(): PARAMS: client={}, topic={}, value={}", clientId, topicName, value);
        return coordinator.eventLocalSend(clientId, topicName, value);
    }

    // ------------------------------------------------------------------------------------------------------------
    // EMS shutdown and exit methods
    // ------------------------------------------------------------------------------------------------------------

    @GetMapping(value = "/ems/shutdown")
    public String emsShutdown() {
        log.info("ManagementController.emsShutdown(): Not implemented");
        coordinator.emsShutdownServices();
        return "OK";
    }

    @GetMapping(value = { "/ems/exit", "/ems/exit/{exitCode}" })
    public String emsExit(@PathVariable Integer exitCode) {
        if (properties.isExitAllowed()) {
            int _exitCode = exitCode!=null ? exitCode : properties.getExitCode();
            log.info("ManagementController.emsExit(): exitCode={}", _exitCode);
            coordinator.emsShutdownServices();
            coordinator.emsExit(_exitCode);
            return "OK";
        } else {
            log.info("ManagementController.emsExit(): Exiting EMS is not allowed");
            return "NOT ALLOWED";
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // EMS status and information query methods
    // ------------------------------------------------------------------------------------------------------------

    @GetMapping(value = "/ems/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> emsStatus() {
        log.info("ManagementController.emsStatus(): Not implemented");
        return Collections.emptyMap();
    }

    @GetMapping(value = "/ems/topology", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> emsTopology() {
        log.info("ManagementController.emsTopology(): Not implemented");
        return Collections.emptyMap();
    }

    @GetMapping({ "/beacon", "/beacon/transmit" })
    public void beaconTransmit() throws JMSException {
        log.info("ManagementController.beaconTransmit(): Invoked");
        coordinator.clientStats("*");
        topicBeacon.transmitInfo();
    }

    // ------------------------------------------------------------------------------------------------------------

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
