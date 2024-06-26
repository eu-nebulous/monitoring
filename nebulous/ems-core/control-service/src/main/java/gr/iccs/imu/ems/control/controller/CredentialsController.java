/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.baguette.server.properties.BaguetteServerProperties;
import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import gr.iccs.imu.ems.control.webconf.WebSecurityConfig;
import gr.iccs.imu.ems.util.CredentialsMap;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CredentialsController {

    private final static String ROLES_ALLOWED_JWT_TOKEN_OR_API_KEY =
            "hasAnyRole('"+WebSecurityConfig.ROLE_JWT_TOKEN+"','"+WebSecurityConfig.ROLE_API_KEY+"')";

    private final ControlServiceProperties properties;
    private final ControlServiceCoordinator coordinator;
    private final CredentialsCoordinator credentialsCoordinator;
    private final WebSecurityConfig webSecurityConfig;
    private final PasswordUtil passwordUtil;

    // ------------------------------------------------------------------------------------------------------------
    // Credentials methods
    // ------------------------------------------------------------------------------------------------------------

//    @PreAuthorize(ROLES_ALLOWED_JWT_TOKEN_OR_API_KEY)
    @GetMapping(value = "/broker/credentials")
    public HttpEntity<Map> getBrokerCredentials(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.info("CredentialsController.getBrokerCredentials(): BEGIN");
        log.trace("CredentialsController.getBrokerCredentials(): JWT token: {}", jwtToken);

        // Retrieve sensor information
        String brokerClientsUrl = coordinator.getBrokerCep().getBrokerCepProperties().getBrokerUrlForClients();
        String brokerUsername = coordinator.getBrokerCep().getBrokerUsername();
        String brokerPassword = coordinator.getBrokerCep().getBrokerPassword();
        String brokerCertificatePem = coordinator.getBrokerCep().getBrokerCertificate();

        // Prepare response
        Map<String,String> response = new HashMap<>();
        response.put("url", brokerClientsUrl);
        response.put("username", brokerUsername);
        response.put("password", brokerPassword);
        response.put("certificate", brokerCertificatePem);
        HttpEntity<Map> entity = coordinator.createHttpEntity(Map.class, response, jwtToken);
        log.debug("CredentialsController.getBrokerCredentials(): Response: {}",
                encodeMapFields(response, "password", "certificate"));

        //return response;
        return entity;
    }

//    @PreAuthorize(ROLES_ALLOWED_JWT_TOKEN_OR_API_KEY)
    @GetMapping(value = "/baguette/connectionInfo")
    public HttpEntity<Map> getBaguetteConnectionInfo(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.info("CredentialsController.getBaguetteConnectionInfo(): BEGIN");
        log.trace("CredentialsController.getBaguetteConnectionInfo(): JWT token: {}", jwtToken);

        // Retrieve sensor information
        Map<String,String> response = coordinator.getBaguetteServer().getServerConnectionInfo();

        // Prepare response
        HttpEntity<Map> entity = coordinator.createHttpEntity(Map.class, response, jwtToken);
        log.debug("CredentialsController.getBaguetteConnectionInfo(): Response: {}",
                encodeMapFields(response, "BAGUETTE_SERVER_PASSWORD", "BAGUETTE_SERVER_PUBKEY"));

        return entity;
    }

//    @PreAuthorize(ROLES_ALLOWED_JWT_TOKEN_OR_API_KEY)
    @GetMapping(value = "/baguette/ref/{ref}", produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpEntity<Map> getNodeCredentials(@PathVariable String ref,
                                              @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.info("CredentialsController.getNodeCredentials(): BEGIN: ref={}", ref);
        log.trace("CredentialsController.getNodeCredentials(): JWT token: {}", jwtToken);

        if (StringUtils.isBlank(ref))
            throw new IllegalArgumentException("The 'ref' parameter is mandatory");

        // Check if it is EMS server ref
        if (credentialsCoordinator.getReference().equals(ref)) {
            if (coordinator.getBaguetteServer()==null || !coordinator.getBaguetteServer().isServerRunning()) {
                log.warn("CredentialsController.getNodeCredentials(): Baguette Server is not started");
                return null;
            }

            BaguetteServerProperties config = coordinator.getBaguetteServer().getConfiguration();
            String address = config.getServerAddress();
            int port = config.getServerPort();
            String username = null;
            String password = null;
            CredentialsMap credentials = config.getCredentials();
            if (!credentials.isEmpty()) {
                username = credentials.keySet().stream().findFirst().orElse(null);
                password = credentials.get(username);
            }
            String key = coordinator.getBaguetteServer().getServerPubkey();

            log.debug("CredentialsController.getNodeCredentials(): Retrieved EMS server connection info by reference: ref={}", ref);

            // Prepare response
            Map<String,String> response = new HashMap<>();
            response.put("hostname", address);
            response.put("port", ""+port);
            response.put("username", username);
            response.put("password", password);
            response.put("private-key", key);
            HttpEntity<Map> entity = coordinator.createHttpEntity(Map.class, response, jwtToken);
            log.debug("CredentialsController.getNodeCredentials(): Response: ** Not shown because it contains credentials **");

            return entity;
        }

        // Retrieve node credentials
        NodeRegistryEntry entry = coordinator.getBaguetteServer().getNodeRegistry().getNodeByReference(ref);
        if (entry==null) {
            throw new IllegalArgumentException("Not found Node with reference: "+ref);
        }
        log.debug("CredentialsController.getNodeCredentials(): Retrieved node by reference: ref={}", ref);

        // Prepare response
        Map<String,String> response = new HashMap<>();
        response.put("hostname", entry.getIpAddress());
        response.put("port", entry.getPreregistration().getOrDefault("ssh.port", "22"));
        response.put("username", entry.getPreregistration().get("ssh.username"));
        response.put("password", entry.getPreregistration().get("ssh.password"));
        response.put("private-key", entry.getPreregistration().get("ssh.key"));
        HttpEntity<Map> entity = coordinator.createHttpEntity(Map.class, response, jwtToken);
        //log.debug("CredentialsController.getNodeCredentials(): Response: ** Not shown because it contains credentials **");
        log.debug("CredentialsController.getNodeCredentials(): Response: {}", encodeMapFields(response, "password", "private-key"));

        return entity;
    }

    private HashMap<String, String> encodeMapFields(Map<String, String> response, String...keys) {
        HashMap<String, String> map = new HashMap<>(response);
        for (String k : keys) {
            map.put(k, passwordUtil.encodePassword(map.getOrDefault(k, "")));
        }
        return map;
    }


    // ------------------------------------------------------------------------------------------------------------
    // EMS One-Time-Password (OTP) endpoints
    // ------------------------------------------------------------------------------------------------------------

    @GetMapping(value = "/ems/otp/new")
    public String newOtp() {
        log.info("CredentialsController.newOtp(): BEGIN");
        String newOtp = webSecurityConfig.otpCreate();
        log.debug("CredentialsController.newOtp(): New OTP: {}", passwordUtil.encodePassword(newOtp));
        return newOtp;
    }

    @GetMapping(value = "/ems/otp/remove/{otp}")
    public String removeOtp(@PathVariable String otp) {
        log.info("CredentialsController.removeOtp(): BEGIN");
        if ("*".equals(otp))
            webSecurityConfig.otpClearCache();
        else
            webSecurityConfig.otpRemove(otp);
        log.debug("CredentialsController.removeOtp(): Removed OTP: {}", passwordUtil.encodePassword(otp));
        return "OK";
    }
}
