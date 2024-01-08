/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.selfhealing;

import gr.iccs.imu.ems.baguette.client.install.ClientInstallationProperties;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.common.recovery.RecoveryContext;
import gr.iccs.imu.ems.common.selfhealing.SelfHealingManager;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Data
@Service
public class SelfHealingManagerImpl implements SelfHealingManager<NodeRegistryEntry>, InitializingBean {
    private final ClientInstallationProperties clientInstallationProperties;
    private final ServerSelfHealingProperties properties;
    private final RecoveryContext recoveryContext;

    private boolean enabled;
    private MODE mode;
    private Map<String, NodeRegistryEntry> nodes = new LinkedHashMap<>();
    private Map<String, NODE_STATE> nodeStates = new LinkedHashMap<>();
    private Map<String, String> nodeStateTexts = new LinkedHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Self-Healing Manager initialized");
        setEnabled( properties.isEnabled() );
        setMode( properties.getMode() );

        // Initialize recovery context
        recoveryContext.initialize(clientInstallationProperties, properties);
        log.info("Recovery context: {}", recoveryContext);
    }

    private void check() {
        if (!enabled) throw new IllegalStateException("SelfHealingManager is not enabled");
    }

    @Override
    public Collection<NodeRegistryEntry> getNodes() {
        check();
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Override
    public boolean containsNode(@NonNull NodeRegistryEntry node) {
        check();
        return nodes.containsKey(node.getIpAddress());
    }

    @Override
    public boolean containsAny(@NonNull Collection<NodeRegistryEntry> nodes) {
        check();
        return Collections.disjoint(this.nodes.values(), nodes);
    }

    @Override
    public boolean isMonitored(@NonNull NodeRegistryEntry node) {
        check();
        return mode==MODE.ALL ||
                mode==MODE.INCLUDED && containsNode(node) ||
                mode==MODE.EXCLUDED && ! containsNode(node);
    }

    @Override
    public void addNode(@NonNull NodeRegistryEntry node) {
        check();
        nodes.put(node.getIpAddress(), node);
    }

    @Override
    public void addAllNodes(@NonNull Collection<NodeRegistryEntry> nodes) {
        check();
        this.nodes.putAll(nodes.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(NodeRegistryEntry::getIpAddress, Function.identity())));
    }

    @Override
    public void removeNode(@NonNull NodeRegistryEntry node) {
        check();
        nodes.remove(node.getIpAddress());
        nodeStates.remove(node.getIpAddress());
        nodeStateTexts.remove(node.getIpAddress());
    }

    @Override
    public void removeAllNodes(Collection<NodeRegistryEntry> nodes) {
        check();
        nodes.stream()
                .filter(Objects::nonNull)
                .forEach(this::removeNode);
    }

    @Override
    public void clear() {
        check();
        nodes.clear();
    }

    @Override
    public NODE_STATE getNodeSelfHealingState(@NonNull NodeRegistryEntry node) {
        check();
        if (mode!=MODE.EXCLUDED && ! nodes.containsKey(node.getIpAddress()))
            return NODE_STATE.NOT_MONITORED;
        if (mode==MODE.EXCLUDED && nodes.containsKey(node.getIpAddress()))
            return NODE_STATE.NOT_MONITORED;
        return nodeStates.get(node.getIpAddress());
    }

    @Override
    public String getNodeSelfHealingStateText(@NonNull NodeRegistryEntry node) {
        check();
        if (mode!=MODE.EXCLUDED && ! nodes.containsKey(node.getIpAddress()))
            return null;
        if (mode==MODE.EXCLUDED && nodes.containsKey(node.getIpAddress()))
            return null;
        return nodeStateTexts.get(node.getIpAddress());
    }

    @Override
    public void setNodeSelfHealingState(@NonNull NodeRegistryEntry node, @NonNull NODE_STATE state, String text) {
        check();
        if (!isMonitored(node)) return;
        if (state==NODE_STATE.NOT_MONITORED)
            throw new IllegalArgumentException("Node self-healing state cannot be set to NOT_MONITORED. Remove/Exclude node from self-healing instead");
        nodeStates.put(node.getIpAddress(), state);
        nodeStateTexts.put(node.getIpAddress(), text);
    }
}
