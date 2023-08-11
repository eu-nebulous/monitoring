/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server;

import gr.iccs.imu.ems.baguette.server.coordinator.cluster.ClusteringCoordinator;
import gr.iccs.imu.ems.baguette.server.properties.BaguetteServerProperties;
import gr.iccs.imu.ems.util.EventBus;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.mina.MinaServiceFactoryFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Custom SSH server
 */
@Slf4j
public class Sshd {
    @Getter private ServerCoordinator coordinator;
    private BaguetteServerProperties configuration;
    private SshServer sshd;
    private String serverPubkey;
    private String serverPubkeyFingerprint;
    private String serverPubkeyAlgorithm;
    private String serverPubkeyFormat;
    private KeyPairProvider serverKeyProvider;

    private boolean heartbeatOn;
    private long heartbeatPeriod;

    private EventBus<String,Object,Object> eventBus;
    @Getter @Setter
    private NodeRegistry nodeRegistry;

    public void start(BaguetteServerProperties configuration, ServerCoordinator coordinator, EventBus<String,Object,Object> eventBus, NodeRegistry registry) throws IOException {
        log.info("** SSH server **");
        this.coordinator = coordinator;
        this.configuration = configuration;
        this.eventBus = eventBus;
        this.nodeRegistry = registry;

        // Configure SSH server
        int port = configuration.getServerPort();
        String serverKeyFilePath = configuration.getServerKeyFile();
        log.info("SSH server: Public IP address: {}", configuration.getServerAddress());
        log.info("SSH server:  Starting on port: {}", port);
        log.info("SSH server:   Server key file: {}", new File(serverKeyFilePath).getAbsolutePath());

        // Create SSHD and set port
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);

        // Setup server's key provider
        _loadPubkeyAndFingerprint();
        sshd.setKeyPairProvider(this.serverKeyProvider);

        // Setup server's shell factory (for custom Shell commands)
        sshd.setShellFactory(channelSession -> {
            ClientShellCommand csc = new ClientShellCommand(coordinator, configuration.isClientAddressOverrideAllowed(), eventBus, nodeRegistry);
            //csc.setId( "#-"+System.currentTimeMillis() );
            log.debug("SSH server: Shell Factory: create invoked : New ClientShellCommand id: {}", csc.getId());
            return csc;
        });

        // Setup password authenticator
        sshd.setPasswordAuthenticator((username, password, session) -> {
            //public boolean authenticate(String username, String password, ServerSession session)
            String pwd = Optional.ofNullable(configuration.getCredentials().get(username.trim())).orElse("");
            return pwd.equals(password);
        });

        // Set session timeout
        sshd.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, Duration.ofMillis(configuration.getHeartbeatPeriod()));
        //PropertyResolverUtils.updateProperty(sshd, CoreModuleProperties.HEARTBEAT_INTERVAL.getName(), configuration.getHeartbeatPeriod());
        PropertyResolverUtils.updateProperty(sshd, CoreModuleProperties.IDLE_TIMEOUT.getName(), Long.MAX_VALUE);
        PropertyResolverUtils.updateProperty(sshd, CoreModuleProperties.SOCKET_KEEPALIVE.getName(), true);
        log.debug("SSH server: Set IDLE_TIMEOUT to MAX, and KEEP-ALIVE to true, and HEARTBEAT to {}", configuration.getHeartbeatPeriod());

        // Explicitly set IO service factory factory to prevent conflict between MINA and Netty options
        sshd.setIoServiceFactoryFactory(new MinaServiceFactoryFactory());

        // Start SSH server and accept connections
        sshd.start();
        log.info("SSH server: Ready");

        // Start application-level heartbeat service (additional to the SSH and Socket heartbeats)
        if (configuration.isHeartbeatEnabled()) {
            long heartbeatPeriod = configuration.getHeartbeatPeriod();
            startHeartbeat(heartbeatPeriod);
        }

        // Start coordinator
        coordinator.start();
    }

    public void stop() throws IOException {
        // Stop coordinator
        coordinator.stop();

        // Don't accept new connections
        log.info("SSH server: Stopping SSH server...");
        sshd.setShellFactory(null);

        // Signal heartbeat service to stop
        stopHeartbeat();

        // Close active client connections
        for (ClientShellCommand csc : ClientShellCommand.getActive()) {
            csc.stop("Server exits");
        }

        sshd.stop();
        log.info("SSH server: Stopped");
    }

    public void startHeartbeat(long period) {
        heartbeatOn = true;
        Thread heartbeat = new Thread(
                new Runnable() {
                    private long period;

                    public void run() {
                        log.info("--> Heartbeat: Started: period={}ms", period);
                        while (heartbeatOn && period > 0) {
                            try {
                                Thread.sleep(period);
                            } catch (InterruptedException ex) {
                            }
                            String msg = String.format("Heartbeat %d", System.currentTimeMillis());
                            log.debug("--> Heartbeat: {}", msg);
                            for (ClientShellCommand csc : ClientShellCommand.getActive()) {
                                csc.sendToClient(msg, Level.DEBUG);
                            }
                        }
                        log.info("--> Heartbeat: Stopped");
                    }

                    public Runnable setPeriod(long period) {
                        this.period = period;
                        return this;
                    }
                }
                        .setPeriod(period)
        );
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    public void stopHeartbeat() {
        heartbeatOn = false;
    }

    protected void broadcastToClients(String msg) {
        for (ClientShellCommand csc : ClientShellCommand.getActive()) {
            log.info("SSH server: Sending to {} : {}", csc.getId(), msg);
            csc.sendToClient(msg);
        }
    }

    public void sendToActiveClients(String command) {
        for (ClientShellCommand csc : ClientShellCommand.getActive()) {
            log.info("SSH server: Sending to client {} : {}", csc.getId(), command);
            csc.sendToClient(command);
        }
    }

    public void sendToClient(String clientId, String command) {
        for (ClientShellCommand csc : ClientShellCommand.getActive()) {
            if (csc.getId().equals(clientId)) {
                log.info("SSH server: Sending to client {} : {}", csc.getId(), command);
                csc.sendToClient(command);
            }
        }
    }

    public void sendToActiveClusters(String command) {
        if (!(coordinator instanceof ClusteringCoordinator)) return;
        ((ClusteringCoordinator)coordinator).getClusters().forEach(cluster -> {
            log.info("SSH server: Sending to cluster {} : {}", cluster.getId(), command);
            sendToCluster(cluster.getId(), command);
        });
    }

    public void sendToCluster(String clusterId, String command) {
        if (!(coordinator instanceof ClusteringCoordinator)) return;
        ((ClusteringCoordinator)coordinator).getCluster(clusterId).getNodes().forEach(csc -> {
            log.info("SSH server: Sending to client {} : {}", csc.getId(), command);
            csc.sendToClient(command);
        });
    }

    public Object readFromClient(String clientId, String command, Level logLevel) {
        log.trace("SSH server: Sending and Reading to/from client {}: {}", clientId, command);
        for (ClientShellCommand csc : ClientShellCommand.getActive()) {
            log.trace("SSH server: Check CSC: csc-id={}, client={}", csc.getId(), clientId);
            if (csc.getId().equals(clientId)) {
                log.debug("SSH server: Sending and Reading to/from client {} : {}", csc.getId(), command);
                return csc.readFromClient(command, logLevel);
            }
        }
        return null;
    }

    public List<String> getActiveClients() {
        return ClientShellCommand.getActive().stream()
                .map(c -> String.format("%s %s %s:%d", c.getId(),
                        c.getClientIpAddress(),
                        c.getClientClusterNodeHostname(),
                        c.getClientClusterNodePort()))
                .sorted()
                .collect(Collectors.toList());
    }

    public Map<String, Map<String, String>> getActiveClientsMap() {
        return ClientShellCommand.getActive().stream()
                //.sorted((final ClientShellCommand c1, final ClientShellCommand c2) -> c1.getId().compareTo(c2.getId()))
                .collect(Collectors.toMap(ClientShellCommand::getId, c -> {
                    Map<String,String> properties = new LinkedHashMap<>();
                    //properties.put("id", c.getId());
                    properties.put("ip-address", c.getClientIpAddress());
                    properties.put("node-hostname", c.getClientClusterNodeHostname());
                    properties.put("node-port", Integer.toString(c.getClientClusterNodePort()));
                    return properties;
                }));
    }

    public void sendConstants(Map<String, Double> constants) {
        for (ClientShellCommand csc : ClientShellCommand.getActive()) {
            log.info("SSH server: Sending constants to client {} : {}", csc.getId(), constants);
            csc.sendConstants(constants);
        }
    }

    public String getPublicKey() {
        if (serverPubkey==null) _loadPubkeyAndFingerprint();
        return serverPubkey;
    }

    public String getPublicKeyFingerprint() {
        if (serverPubkeyFingerprint==null) _loadPubkeyAndFingerprint();
        return serverPubkeyFingerprint;
    }

    public String getPublicKeyAlgorithm() {
        if (serverPubkey==null) _loadPubkeyAndFingerprint();
        return serverPubkeyAlgorithm;
    }

    public String getPublicKeyFormat() {
        if (serverPubkey==null) _loadPubkeyAndFingerprint();
        return serverPubkeyFormat;
    }

    @SneakyThrows
    private synchronized void _loadPubkeyAndFingerprint() {
        if (serverPubkey!=null) return;

        String serverKeyFilePath = configuration.getServerKeyFile();
        log.debug("_loadPubkeyAndFingerprint(): Server Key file: {}", serverKeyFilePath);
        File serverKeyFile = new File(serverKeyFilePath);

        // Create and configure a new SimpleGeneratorHostKeyProvider instance
        SimpleGeneratorHostKeyProvider simpleGeneratorHostKeyProvider =
                new SimpleGeneratorHostKeyProvider(serverKeyFile.toPath());
        //simpleGeneratorHostKeyProvider.setStrictFilePermissions(true);    // 'true' by default

        //  Create or load the Baguette server key pair
        List<KeyPair> keys = simpleGeneratorHostKeyProvider.loadKeys(null);
        if (keys.size()!=1)
            throw new IllegalArgumentException("Server key file contains 0 or >1 keys: #keys="+keys.size()+", file="+serverKeyFilePath);
        KeyPair serverKey = keys.get(0);
        PublicKey publicKey = serverKey.getPublic();

        // Write Baguette server public key as PEM string
        StringWriter writer = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
        pemWriter.writeObject(publicKey);
        pemWriter.flush();

        // Store public key PEM and fingerprint for future use
        this.serverPubkey = StringEscapeUtils.escapeJson(writer.toString().trim());
        this.serverPubkeyFormat = publicKey.getFormat();
        this.serverPubkeyAlgorithm = publicKey.getAlgorithm();
        this.serverPubkeyFingerprint = KeyUtils.getFingerPrint(publicKey);
        this.serverKeyProvider = simpleGeneratorHostKeyProvider;
        log.debug("_loadPubkeyAndFingerprint(): Server public key: \n{}", serverPubkey);
        log.debug("_loadPubkeyAndFingerprint():       Fingerprint: {}", serverPubkeyFingerprint);
        log.debug("_loadPubkeyAndFingerprint():         Algorithm: {}", serverPubkeyAlgorithm);
        log.debug("_loadPubkeyAndFingerprint():            Format: {}", serverPubkeyFormat);
    }
}
