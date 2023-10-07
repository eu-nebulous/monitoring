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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.jms.JMSException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

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

    @RequestMapping(value = "/client/list", method = GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> listClients() {
        List<String> clients = coordinator.clientList();
        log.info("ManagementController.listClients(): {}", clients);
        return clients;
    }

    @RequestMapping(value = "/client/list/map", method = GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Map<String, String>> listClientMaps() {
        Map<String, Map<String, String>> clients = coordinator.clientMap();
        log.info("ManagementController.listClientMaps(): {}", clients);
        return clients;
    }

    @RequestMapping(value = "/client/command/{clientId}/{command:.+}", method = GET)
    public String clientCommand(@PathVariable String clientId, @PathVariable String command) {
        log.info("ManagementController.clientCommand(): PARAMS: client={}, command={}", clientId, command);
        return coordinator.clientCommandSend(clientId, command);
    }

    @RequestMapping(value = "/cluster/command/{clusterId}/{command:.+}", method = GET)
    public String clusterCommand(@PathVariable String clusterId, @PathVariable String command) {
        log.info("ManagementController.clusterCommand(): PARAMS: cluster={}, command={}", clusterId, command);
        return coordinator.clusterCommandSend(clusterId, command);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Event Generation and Debugging methods
    // ------------------------------------------------------------------------------------------------------------

    @RequestMapping(value = "/event/generate-start/{clientId}/{topicName}/{interval}/{lowerValue}/{upperValue}", method = GET)
    public String startEventGeneration(@PathVariable String clientId, @PathVariable String topicName, @PathVariable long interval, @PathVariable double lowerValue, @PathVariable double upperValue) {
        log.info("ManagementController.startEventGeneration(): PARAMS: client={}, topic={}, interval={}, value-range=[{},{}]", clientId, topicName, interval, lowerValue, upperValue);
        return coordinator.eventGenerationStart(clientId, topicName, interval, lowerValue, upperValue);
    }

    @RequestMapping(value = "/event/generate-stop/{clientId}/{topicName}", method = GET)
    public String stopEventGeneration(@PathVariable String clientId, @PathVariable String topicName) {
        log.info("ManagementController.stopEventGeneration(): PARAMS: client={}, topic={}", clientId, topicName);
        return coordinator.eventGenerationStop(clientId, topicName);
    }

    @RequestMapping(value = "/event/send/{clientId}/{topicName}/{value}", method = GET)
    public String sendEvent(@PathVariable String clientId, @PathVariable String topicName, @PathVariable double value) {
        log.info("ManagementController.sendEvent(): PARAMS: client={}, topic={}, value={}", clientId, topicName, value);
        return coordinator.eventLocalSend(clientId, topicName, value);
    }

    // ------------------------------------------------------------------------------------------------------------
    // EMS shutdown and exit methods
    // ------------------------------------------------------------------------------------------------------------

    @RequestMapping(value = "/ems/shutdown", method = {GET, POST})
    public String emsShutdown() {
        log.info("ManagementController.emsShutdown(): Not implemented");
        coordinator.emsShutdownServices();
        return "OK";
    }

    @RequestMapping(value = { "/ems/exit", "/ems/exit/{exitCode}" }, method = {GET, POST})
    public String emsExit(@PathVariable Optional<Integer> exitCode) {
        if (properties.isExitAllowed()) {
            int _exitCode = exitCode.orElse(properties.getExitCode());
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

    @RequestMapping(value = "/ems/status", method = {GET, POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> emsStatus() {
        log.info("ManagementController.emsStatus(): Not implemented");
        return Collections.emptyMap();
    }

    @RequestMapping(value = "/ems/topology", method = {GET, POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> emsTopology() {
        log.info("ManagementController.emsTopology(): Not implemented");
        return Collections.emptyMap();
    }

    @RequestMapping(value = { "/beacon", "/beacon/transmit" }, method = GET)
    public void beaconTransmit() throws JMSException {
        log.info("ManagementController.beaconTransmit(): Invoked");
        topicBeacon.transmitInfo();
    }

    // ------------------------------------------------------------------------------------------------------------

    @RequestMapping(value = "/health", method = GET)
    public String health() {
        return "OK";
    }
}
