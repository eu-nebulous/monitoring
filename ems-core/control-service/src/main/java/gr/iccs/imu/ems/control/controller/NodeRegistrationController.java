/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Slf4j
@RestController
@RequiredArgsConstructor
public class NodeRegistrationController {
    private final ControlServiceProperties properties;
    private final ControlServiceCoordinator coordinator;
    private final NodeRegistrationCoordinator nodeRegistrationCoordinator;

    // ------------------------------------------------------------------------------------------------------------
    // Baguette control methods
    // ------------------------------------------------------------------------------------------------------------

    @RequestMapping(value = "/baguette/stopServer", method = {GET, POST})
    public String baguetteStopServer() {
        log.info("NodeRegistrationController.baguetteStopServer(): Request received");

        // Dispatch Baguette stop operation in a worker thread
        nodeRegistrationCoordinator.stopBaguette();
        log.info("NodeRegistrationController.baguetteStopServer(): Baguette stop operation dispatched to a worker thread");

        return "OK";
    }

    @RequestMapping(value = "/baguette/registerNode", method = POST,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public String baguetteRegisterNode(@RequestBody String jsonNode, HttpServletRequest request) throws Exception {
        log.info("NodeRegistrationController.baguetteRegisterNode(): Invoked");
        log.debug("NodeRegistrationController.baguetteRegisterNode(): Node json:\n{}", jsonNode);

        // Extract node information from json
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String,Object> nodeMap = new Gson().fromJson(jsonNode, type);
        String nodeId = (String) nodeMap.get("id");
        log.info("NodeRegistrationController.baguetteRegisterNode(): node-id: {}", nodeId);
        log.debug("NodeRegistrationController.baguetteRegisterNode(): Node information: map={}", nodeMap);

        String response = nodeRegistrationCoordinator.registerNode(request, nodeMap,
                coordinator.getTranslationContextOfAppModel(coordinator.getCurrentAppModelId()));

        log.info("NodeRegistrationController.baguetteRegisterNode(): Node registered: node-id: {}", nodeId);
        log.debug("NodeRegistrationController.baguetteRegisterNode(): node: {}, json: {}", nodeId, response);
        return response;
    }

    @RequestMapping(value = "/baguette/node/list", method = GET)
    public Collection<String> baguetteNodeList() throws Exception {
        log.info("NodeRegistrationController.baguetteNodeList(): Invoked");

        Collection<String> addresses = coordinator.getBaguetteServer().getNodeRegistry().getNodeAddresses();

        log.info("NodeRegistrationController.baguetteNodeList(): {}", addresses);
        return addresses;
    }

    @RequestMapping(value = "/baguette/node/reinstall/{ipAddress:.+}", method = {GET, POST},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String baguetteNodeReinstall(@PathVariable String ipAddress) throws Exception {
        log.info("NodeRegistrationController.baguetteNodeReinstall(): Invoked");
        log.info("NodeRegistrationController.baguetteNodeReinstall(): Node IP address: {}", ipAddress);

        // Get node info using IP address
        BaguetteServer baguette = coordinator.getBaguetteServer();
        NodeRegistryEntry nodeInfo = baguette.getNodeRegistry().getNodeByAddress(ipAddress);
        log.info("NodeRegistrationController.baguetteNodeReinstall(): Info for node at: ip-address={}, Node Info:\n{}",
                ipAddress, nodeInfo);
        if (nodeInfo==null) {
            log.warn("NodeRegistrationController.baguetteNodeReinstall(): Not found pre-registered node with ip-address: {}", ipAddress);
            return "NODE NOT FOUND: "+ipAddress;
        }

        // Continue processing according to ExecutionWare type
        String response;
        log.info("NodeRegistrationController.baguetteNodeReinstall(): ExecutionWare: {}", properties.getExecutionware());
        if (properties.getExecutionware() == ControlServiceProperties.ExecutionWare.CLOUDIATOR) {
            response = nodeRegistrationCoordinator.getClientInstallationInstructions(nodeInfo);
        } else {
            response = nodeRegistrationCoordinator.createClientInstallationTask(nodeInfo,
                    coordinator.getTranslationContextOfAppModel(coordinator.getCurrentAppModelId()));
        }

        log.info("NodeRegistrationController.baguetteNodeReinstall(): node ip-address: {}, response: {}", ipAddress, response);
        return response;
    }

    @RequestMapping(value = "/baguette/getNodeInfoByAddress/{ipAddress:.+}", method = {GET, POST},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public NodeRegistryEntry baguetteGetNodeInfoByAddress(@PathVariable String ipAddress) throws Exception {
        log.info("NodeRegistrationController.baguetteGetNodeInfoByAddress(): ip-address={}", ipAddress);

        BaguetteServer baguette = coordinator.getBaguetteServer();
        NodeRegistryEntry nodeInfo = baguette.getNodeRegistry().getNodeByAddress(ipAddress);

        log.info("NodeRegistrationController.baguetteGetNodeInfoByAddress(): Info for node at: ip-address={}, Node Info:\n{}",
                ipAddress, nodeInfo);
        return nodeInfo;
    }

    @RequestMapping(value = "/baguette/getNodeNameByAddress/{ipAddress:.+}", method = {GET, POST},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String baguetteGetNodeNameByAddress(@PathVariable String ipAddress) throws Exception {
        log.info("NodeRegistrationController.baguetteGetNodeNameByAddress(): ip-address={}", ipAddress);

        BaguetteServer baguette = coordinator.getBaguetteServer();
        NodeRegistryEntry nodeInfo = baguette.getNodeRegistry().getNodeByAddress(ipAddress);
        String nodeName = nodeInfo!=null ? nodeInfo.getPreregistration().get("name") : null;

        log.info("NodeRegistrationController.baguetteGetNodeNameByAddress(): Name of node at: ip-address={}, Node name: {}",
                ipAddress, nodeName);
        return nodeName;
    }
}
