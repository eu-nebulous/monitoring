/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server.coordinator.cluster;

import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.util.ClientConfiguration;
import gr.iccs.imu.ems.util.KeystoreUtil;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.operator.OperatorCreationException;

import jakarta.validation.constraints.NotBlank;
import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Data
public class ClusterZone implements IClusterZone {
    private final String id;
    private final int startPort;
    private final int endPort;

    @Getter(AccessLevel.NONE)
    private final AtomicInteger currentPort = new AtomicInteger(1200);
    @Getter(AccessLevel.NONE)
    private final Map<String, ClientShellCommand> nodes = new LinkedHashMap<>();
    @Getter(AccessLevel.NONE)
    private final Map<String, Integer> addressPortCache = new HashMap<>();
    @Getter(AccessLevel.NONE)
    private final Map<String, NodeRegistryEntry> nodesWithoutClient = new LinkedHashMap<>();

    private final String clusterId;
    private final String clusterKeystoreBase64;
    private final File clusterKeystoreFile;
    private final String clusterKeystoreType;
    private final String clusterKeystorePassword;
    @Getter @Setter
    private ClientShellCommand aggregator;

    public ClusterZone(@NotBlank String id, int startPort, int endPort, String keystoreFileName)
            throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException, OperatorCreationException
    {
        checkArgs(id, startPort, endPort);
        this.id = id;
        this.startPort = startPort;
        this.endPort = endPort;
        currentPort.set(startPort);

        this.clusterId = RandomStringUtils.randomAlphanumeric(64);
        this.clusterKeystoreFile = new File(keystoreFileName);
        this.clusterKeystoreType = "JKS";
        this.clusterKeystorePassword = RandomStringUtils.randomAlphanumeric(64);
        log.info("New ClusterZone:  zone: {}", id);
        log.info("                  file: {}", clusterKeystoreFile);
        log.info("                  type: {}", clusterKeystoreType);
        log.debug("             password: {}", PasswordUtil.getInstance().encodePassword(clusterKeystorePassword));

        log.trace("ClusterZone.<init>: Cluster Keystore: file={}, type={}, pass={}", clusterKeystoreFile.getCanonicalPath(), clusterKeystoreType, clusterKeystorePassword);
        log.trace("ClusterZone.<init>: Cluster Id: {}", clusterId);
        this.clusterKeystoreBase64 = KeystoreUtil
                .getKeystore(clusterKeystoreFile.getCanonicalPath(), clusterKeystoreType, clusterKeystorePassword)
                .createIfNotExist()
                .createKeyAndCert(clusterId, "CN=" + clusterId, "")
                .readFileAsBase64();
        log.debug("       Base64 content: {}",
                StringUtils.isNotBlank(clusterKeystoreBase64) ? "Not empty" : "!!! Empty !!!");
        if (log.isTraceEnabled())
            log.trace("ClusterZone.<init>: Cluster Keystore: Base64: {}", PasswordUtil.getInstance().encodePassword(clusterKeystoreBase64));
    }

    private void checkArgs(String id, int startPort, int endPort) {
        if (StringUtils.isBlank(id))
            throw new IllegalArgumentException("Zone id cannot be null or blank: zone-id="+id);
        if (startPort<1 || endPort<1 || startPort>65535 || endPort>65535)
            throw new IllegalArgumentException("Zone start/end port must be between 1 and 65535: zone-id="+id+", start="+startPort+", end="+endPort);
        if (startPort > endPort)
            throw new IllegalArgumentException("Zone start port must be less than or equal to end port: zone-id="+id+", start="+startPort+", end="+endPort);
    }

    public int getPortForAddress(String address) {
        return addressPortCache.computeIfAbsent(address, k -> {
            int port = currentPort.incrementAndGet();
            if (port>endPort)
                throw new IllegalStateException("Zone ports exhausted: "+id);
            log.debug("Mapped address-to-port: {} -> {}", address, port);
            return port;
        });
    }

    public void clearAddressToPortCache() {
        addressPortCache.clear();
    }

    // Nodes management
    public void addNode(@NonNull ClientShellCommand csc) {
        synchronized (Objects.requireNonNull(csc)) {
            nodes.put(csc.getClientIpAddress(), csc);
            csc.setClientZone(this);
            csc.getNodeRegistryEntry().setClusterZone(this);
        }
    }

    public void removeNode(@NonNull ClientShellCommand csc) {
        synchronized (Objects.requireNonNull(csc)) {
            nodes.remove(csc.getClientIpAddress());
            if (csc.getClientZone()==this)
                csc.setClientZone(null);
            if (csc.getNodeRegistryEntry()!=null && csc.getNodeRegistryEntry().getClusterZone()==this)
                csc.getNodeRegistryEntry().setClusterZone(null);
            if (aggregator==csc)
                setAggregator(null);
        }
    }

    public Set<String> getNodeAddresses() {
        return new HashSet<>(nodes.keySet());
    }

    public List<ClientShellCommand> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    public ClientShellCommand getNodeByAddress(String address) {
        return nodes.get(address);
    }

    public List<NodeRegistryEntry> findAggregatorCapableNodes() {
        return this.nodes.values().stream()
                .filter(Objects::nonNull)
                .map(ClientShellCommand::getNodeRegistryEntry)
                .filter(Objects::nonNull)
                .filter(entry -> entry.getState()==NodeRegistryEntry.STATE.REGISTERED || entry.getState()==NodeRegistryEntry.STATE.REGISTERING)
                .collect(Collectors.toList());
    }

    // Nodes-without-Clients management
    public void addNodeWithoutClient(@NonNull NodeRegistryEntry entry) {
        synchronized (Objects.requireNonNull(entry)) {
            String address = entry.getIpAddress();
            if (address == null) address = entry.getNodeAddress();
            if (address == null) throw new IllegalArgumentException("Node address not found in Preregistration info");
            nodesWithoutClient.put(address, entry);
            entry.setClusterZone(this);
            sendClientConfigurationToZoneClients();
        }
    }

    public void removeNodeWithoutClient(@NonNull NodeRegistryEntry entry) {
        synchronized (Objects.requireNonNull(entry)) {
            String address = entry.getIpAddress();
            if (address == null) address = entry.getNodeAddress();
            if (address == null) throw new IllegalArgumentException("Node address not found in Preregistration info");
            nodesWithoutClient.remove(address);
            if (entry.getClusterZone() == this)
                entry.setClusterZone(null);
            sendClientConfigurationToZoneClients();
        }
    }

    public Set<String> getNodeWithoutClientAddresses() {
        return new HashSet<>(nodesWithoutClient.keySet());
    }

    public List<NodeRegistryEntry> getNodesWithoutClient() {
        return new ArrayList<>(nodesWithoutClient.values());
    }

    public NodeRegistryEntry getNodeWithoutClientByAddress(String address) {
        return nodesWithoutClient.get(address);
    }

    public ClientConfiguration getClientConfiguration() {
        return ClientConfiguration.builder()
                .nodesWithoutClient(new HashSet<>(nodesWithoutClient.keySet()))
                .build();
    }

    public ClientConfiguration sendClientConfigurationToZoneClients() {
        ClientConfiguration cc = ClientConfiguration.builder()
                .nodesWithoutClient(new HashSet<>(nodesWithoutClient.keySet()))
                .build();
        ClientShellCommand.sendClientConfigurationToClients(cc , getNodes());
        return cc;
    }
}
