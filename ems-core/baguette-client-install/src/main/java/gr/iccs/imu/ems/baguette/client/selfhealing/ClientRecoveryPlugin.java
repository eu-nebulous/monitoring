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
import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.SshClientInstaller;
import gr.iccs.imu.ems.baguette.client.install.helper.InstallationHelperFactory;
import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import gr.iccs.imu.ems.baguette.server.NodeRegistry;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.common.selfhealing.SelfHealingManager;
import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@ConditionalOnProperty(name = "enabled", prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "self.healing", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ClientRecoveryPlugin implements InitializingBean, EventBus.EventConsumer<String,Object,Object> {
    private final EventBus<String,Object,Object> eventBus;
    private final NodeRegistry nodeRegistry;
    private final TaskScheduler taskScheduler;
    private final ClientInstallationProperties clientInstallationProperties;
    private final ServerSelfHealingProperties selfHealingProperties;
    private final BaguetteServer baguetteServer;

    private final HashMap<NodeRegistryEntry, ScheduledFuture<?>> pendingTasks = new HashMap<>();

    private long clientRecoveryDelay;
    private String recoveryInstructionsFile;

    private final static String CLIENT_EXIT_TOPIC = "BAGUETTE_SERVER_CLIENT_EXITED";
    private final static String CLIENT_REGISTERED_TOPIC = "BAGUETTE_SERVER_CLIENT_REGISTERED";

    @Override
    public void afterPropertiesSet() throws Exception {
        clientRecoveryDelay = selfHealingProperties.getRecovery().getDelay();
        recoveryInstructionsFile = selfHealingProperties.getRecovery().getFile().getOrDefault("baguette", "");
        log.debug("ClientRecoveryPlugin: recovery-delay={}, recovery-instructions-file (for baguette)={}", clientRecoveryDelay, recoveryInstructionsFile);

        eventBus.subscribe(CLIENT_EXIT_TOPIC, this);
        log.debug("ClientRecoveryPlugin: Subscribed for BAGUETTE_SERVER_CLIENT_EXITED events");
        eventBus.subscribe(CLIENT_REGISTERED_TOPIC, this);
        log.debug("ClientRecoveryPlugin: Subscribed for BAGUETTE_SERVER_CLIENT_REGISTERED events");

        log.trace("ClientRecoveryPlugin: clientInstallationProperties: {}", clientInstallationProperties);
        log.trace("ClientRecoveryPlugin: baguetteServer: {}", baguetteServer);

        log.debug("ClientRecoveryPlugin: Recovery Delay: {}", clientRecoveryDelay);
        log.debug("ClientRecoveryPlugin: Recovery Instructions File: {}", recoveryInstructionsFile);
    }

    @Override
    public void onMessage(String topic, Object message, Object sender) {
        log.debug("ClientRecoveryPlugin: onMessage(): BEGIN: topic={}, message={}, sender={}", topic, message, sender);

        // Check if Self-Healing is enabled
        if (! baguetteServer.getSelfHealingManager().isEnabled()) {
            log.debug("ClientRecoveryPlugin: onMessage(): Self-Healing manager is disabled: message={}, sender={}", message, sender);
            return;
        }

        // Only process messages of ClientShellCommand type are accepted (sent by CSC instances)
        if (! (message instanceof ClientShellCommand)) {
            log.warn("ClientRecoveryPlugin: onMessage(): Message is not a {} object. Will ignore it.", ClientShellCommand.class.getSimpleName());
            return;
        }

        // Get NodeRegistryEntry from ClientShellCommand passed with event
        ClientShellCommand csc = (ClientShellCommand)message;
        String clientId = csc.getId();
        String address = csc.getClientIpAddress();
        log.debug("ClientRecoveryPlugin: onMessage(): client-id={}, client-address={}", clientId, address);

        NodeRegistryEntry nodeInfo = csc.getNodeRegistryEntry();    //or = nodeRegistry.getNodeByAddress(address);
        log.debug("ClientRecoveryPlugin: onMessage(): client-node-info={}", nodeInfo);
        log.trace("ClientRecoveryPlugin: onMessage(): node-registry.node-addresses={}", nodeRegistry.getNodeAddresses());
        log.trace("ClientRecoveryPlugin: onMessage(): node-registry.nodes={}", nodeRegistry.getNodes());

        // Check if node is monitored by Self-Healing manager
        if (! baguetteServer.getSelfHealingManager().isMonitored(nodeInfo)) {
            log.warn("ClientRecoveryPlugin: processExitEvent(): Node is not monitored by Self-Healing manager: client-id={}, client-address={}", clientId, address);
            return;
        }

        // Process event
        if (CLIENT_EXIT_TOPIC.equals(topic)) {
            log.debug("ClientRecoveryPlugin: onMessage(): CLIENT EXITED: message={}", message);
            processExitEvent(nodeInfo);
        }
        if (CLIENT_REGISTERED_TOPIC.equals(topic)) {
            log.debug("ClientRecoveryPlugin: onMessage(): CLIENT REGISTERED_TOPIC: message={}", message);
            processRegisteredEvent(nodeInfo);
        }
    }

    private void processExitEvent(NodeRegistryEntry nodeInfo) {
        log.debug("ClientRecoveryPlugin: processExitEvent(): BEGIN: client-id={}, client-address={}", nodeInfo.getClientId(), nodeInfo.getIpAddress());

        // Set node state to DOWN
        baguetteServer.getSelfHealingManager().setNodeSelfHealingState(nodeInfo, SelfHealingManager.NODE_STATE.DOWN);

        // Schedule a recovery task for node
        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            try {
                // Set node state to RECOVERING
                baguetteServer.getSelfHealingManager().setNodeSelfHealingState(nodeInfo, SelfHealingManager.NODE_STATE.RECOVERING);
                // Run recovery task
                runClientRecovery(nodeInfo);
            } catch (Exception e) {
                log.error("ClientRecoveryPlugin: processExitEvent(): EXCEPTION: while recovering node: node-info={} -- Exception: ", nodeInfo, e);
            }
        }, Instant.now().plusMillis(clientRecoveryDelay));

        // Register the recovery task's future in pending list
        ScheduledFuture<?> old = pendingTasks.put(nodeInfo, future);
        log.info("ClientRecoveryPlugin: processExitEvent(): Added recovery task in the queue: client-id={}, client-address={}", nodeInfo.getClientId(), nodeInfo.getIpAddress());

        // Cancel any previous recovery task (for the node) that is still pending
        if (old!=null && ! old.isDone() && ! old.isCancelled()) {
            log.warn("ClientRecoveryPlugin: processExitEvent(): Cancelled previous recovery task: client-id={}, client-address={}", nodeInfo.getClientId(), nodeInfo.getIpAddress());
            old.cancel(false);
        }
    }

    private void processRegisteredEvent(NodeRegistryEntry nodeInfo) {
        log.debug("ClientRecoveryPlugin: processRegisteredEvent(): BEGIN: client-id={}, client-address={}", nodeInfo.getClientId(), nodeInfo.getIpAddress());

        // Cancel any pending recovery task (for the node)
        ScheduledFuture<?> future = pendingTasks.remove(nodeInfo);
        if (future!=null && ! future.isDone() && ! future.isCancelled()) {
            log.warn("ClientRecoveryPlugin: processRegisteredEvent(): Cancelled recovery task: client-id={}, client-address={}", nodeInfo.getClientId(), nodeInfo.getIpAddress());
            future.cancel(false);
        }

        // Set node state to UP
        baguetteServer.getSelfHealingManager().setNodeSelfHealingState(nodeInfo, SelfHealingManager.NODE_STATE.UP);
    }

    public void runClientRecovery(NodeRegistryEntry entry) throws Exception {
        log.debug("ClientRecoveryPlugin: runClientRecovery(): node-info={}", entry);
        if (entry==null) return;

        log.trace("ClientRecoveryPlugin: runClientRecovery(): recoveryInstructionsFile={}", recoveryInstructionsFile);
        entry.getPreregistration().put("instruction-files", recoveryInstructionsFile);

        ClientInstallationTask task = InstallationHelperFactory.getInstance()
                .createInstallationHelper(entry)
                .createClientInstallationTask(entry);
        log.debug("ClientRecoveryPlugin: runClientRecovery(): Client recovery task: {}", task);
        SshClientInstaller installer = SshClientInstaller.builder()
                .task(task)
                .properties(clientInstallationProperties)
                .build();

        log.info("ClientRecoveryPlugin: runClientRecovery(): Starting client recovery: client-id={}, client-address={}", entry.getClientId(), entry.getIpAddress());
        log.debug("ClientRecoveryPlugin: runClientRecovery(): Starting client recovery: node-info={}", entry);
        boolean result = installer.execute();
        pendingTasks.remove(entry);
        log.info("ClientRecoveryPlugin: runClientRecovery(): Client recovery completed: result={}, client-id={}, client-address={}", result, entry.getClientId(), entry.getIpAddress());
        log.debug("ClientRecoveryPlugin: runClientRecovery(): Client recovery completed: result={}, node-info={}", result, entry);
    }
}
