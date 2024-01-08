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
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class NodeRegistrationController {
    private final ControlServiceCoordinator coordinator;
    private final NodeRegistrationCoordinator nodeRegistrationCoordinator;

    // ------------------------------------------------------------------------------------------------------------
    // Baguette control methods
    // ------------------------------------------------------------------------------------------------------------

    @GetMapping("/baguette/stopServer")
    public String baguetteStopServer() {
        log.info("NodeRegistrationController.baguetteStopServer(): Request received");

        // Dispatch Baguette stop operation in a worker thread
        nodeRegistrationCoordinator.stopBaguette();
        log.info("NodeRegistrationController.baguetteStopServer(): Baguette stop operation dispatched to a worker thread");

        return "OK";
    }

    @PostMapping(value = { "/baguette/registerNode", "/baguette/node/register" },
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public String baguetteRegisterNode(@RequestBody String jsonNode, HttpServletRequest request) {
        log.info("NodeRegistrationController.baguetteRegisterNode(): Invoked");
        log.debug("NodeRegistrationController.baguetteRegisterNode(): Node json:\n{}", jsonNode);

        // Extract node information from json
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String,Object> nodeMap = new Gson().fromJson(jsonNode, type);
        String nodeId = (String) nodeMap.get("id");
        log.info("NodeRegistrationController.baguetteRegisterNode(): node-id: {}", nodeId);
        log.debug("NodeRegistrationController.baguetteRegisterNode(): Node information: map={}", nodeMap);

        String response;
        try {
            response = nodeRegistrationCoordinator.registerNode(request, nodeMap,
                    coordinator.getTranslationContextOfAppModel(coordinator.getCurrentAppModelId()));
        } catch (Exception e) {
            log.error("NodeRegistrationController.baguetteRegisterNode(): EXCEPTION while registering node: map={}\n",
                    nodeMap, e);
            response = "ERROR "+e.getMessage();
        }

        log.info("NodeRegistrationController.baguetteRegisterNode(): Node registered: node-id: {}", nodeId);
        log.debug("NodeRegistrationController.baguetteRegisterNode(): node: {}, json: {}", nodeId, response);
        return response;
    }

    @GetMapping(value = "/baguette/node/unregister/{ipAddress:.+}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String baguetteUnregisterNode(@PathVariable String ipAddress, HttpServletRequest request) {
        log.info("NodeRegistrationController.baguetteUnregisterNode(): Invoked");
        log.debug("NodeRegistrationController.baguetteUnregisterNode(): Node IP address:\n{}", ipAddress);

        String response;
        try {
            response = nodeRegistrationCoordinator.unregisterNode(ipAddress,
                    coordinator.getTranslationContextOfAppModel(coordinator.getCurrentAppModelId()));
        } catch (Exception e) {
            log.error("NodeRegistrationController.baguetteUnregisterNode(): EXCEPTION while unregistering node: address={}\n", ipAddress, e);
            response = "ERROR "+e.getMessage();
        }

        log.info("NodeRegistrationController.baguetteUnregisterNode(): Node unregistered: node-address={}", ipAddress);
        log.debug("NodeRegistrationController.baguetteUnregisterNode(): address={}, json={}", ipAddress, response);
        return response;
    }

    @GetMapping("/baguette/node/list")
    public Collection<String> baguetteNodeList() {
        log.info("NodeRegistrationController.baguetteNodeList(): Invoked");

        Collection<String> addresses = coordinator.getBaguetteServer().getNodeRegistry().getNodeAddresses();

        log.info("NodeRegistrationController.baguetteNodeList(): {}", addresses);
        return addresses;
    }

    @GetMapping(value = "/baguette/node/reinstall/{ipAddress:.+}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String baguetteNodeReinstall(@PathVariable String ipAddress) throws Exception {
        log.info("NodeRegistrationController.baguetteNodeReinstall(): Invoked");
        log.info("NodeRegistrationController.baguetteNodeReinstall(): Node IP address: {}", ipAddress);

        if (StringUtils.isBlank(ipAddress)) {
            log.warn("NodeRegistrationController.baguetteNodeReinstall(): IP address is blank: {}", ipAddress);
            return "ERROR: Blank IP address";
        }

        String response = nodeRegistrationCoordinator.reinstallNode(ipAddress,
                coordinator.getTranslationContextOfAppModel(coordinator.getCurrentAppModelId()));

        log.info("NodeRegistrationController.baguetteNodeReinstall(): Node reinstall response: ip-address: {}, response: {}", ipAddress, response);
        return response;
    }

    @GetMapping(value = "/baguette/getNodeInfoByAddress/{ipAddress:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public NodeRegistryEntry baguetteGetNodeInfoByAddress(@PathVariable String ipAddress) {
        log.info("NodeRegistrationController.baguetteGetNodeInfoByAddress(): ip-address={}", ipAddress);

        BaguetteServer baguette = coordinator.getBaguetteServer();
        NodeRegistryEntry nodeInfo = baguette.getNodeRegistry().getNodeByAddress(ipAddress);

        log.info("NodeRegistrationController.baguetteGetNodeInfoByAddress(): Info for node at: ip-address={}, Node Info:\n{}",
                ipAddress, nodeInfo);
        return nodeInfo;
    }

    @GetMapping(value = "/baguette/getNodeNameByAddress/{ipAddress:.+}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String baguetteGetNodeNameByAddress(@PathVariable String ipAddress) {
        log.info("NodeRegistrationController.baguetteGetNodeNameByAddress(): ip-address={}", ipAddress);

        BaguetteServer baguette = coordinator.getBaguetteServer();
        NodeRegistryEntry nodeInfo = baguette.getNodeRegistry().getNodeByAddress(ipAddress);
        String nodeName = nodeInfo!=null ? nodeInfo.getPreregistration().get("name") : null;

        log.info("NodeRegistrationController.baguetteGetNodeNameByAddress(): Name of node at: ip-address={}, Node name: {}",
                ipAddress, nodeName);
        return nodeName;
    }
}
