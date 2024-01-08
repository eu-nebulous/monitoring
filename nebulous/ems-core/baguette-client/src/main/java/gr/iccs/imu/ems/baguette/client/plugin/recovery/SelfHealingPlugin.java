/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.plugin.recovery;

import gr.iccs.imu.ems.baguette.client.BaguetteClientProperties;
import gr.iccs.imu.ems.baguette.client.CommandExecutor;
import gr.iccs.imu.ems.baguette.client.collector.netdata.NetdataCollector;
import gr.iccs.imu.ems.common.recovery.*;
import gr.iccs.imu.ems.util.EventBus;
import gr.iccs.imu.ems.util.PasswordUtil;
import gr.iccs.imu.ems.util.Plugin;
import gr.iccs.imu.ems.util.StrUtil;
import io.atomix.cluster.ClusterMembershipEvent;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side Self-Healing plugin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelfHealingPlugin implements Plugin, InitializingBean, EventBus.EventConsumer<String,Object,Object> {
    private final ApplicationContext applicationContext;
    private final BaguetteClientProperties properties;
    private final SelfHealingProperties selfHealingProperties;
    private final CommandExecutor commandExecutor;
    private final EventBus<String,Object,Object> eventBus;
    private final PasswordUtil passwordUtil;
    private final NodeInfoHelper nodeInfoHelper;
    private final RecoveryContext recoveryContext;

    private boolean started;

    private final HashMap<NodeKey,ScheduledFuture<?>> waitingTasks = new HashMap<>();
    private final TaskScheduler taskScheduler;

    @Override
    public void afterPropertiesSet() {
        log.debug("SelfHealingPlugin: properties: {}", properties);
        log.debug("SelfHealingPlugin: selfHealingProperties: {}", selfHealingProperties);

        // Initialize recovery context
        recoveryContext.initialize(properties);
        log.info("SelfHealingPlugin: Recovery context: {}", recoveryContext);
    }

    public synchronized void start() {
        // check if already running
        if (started) {
            log.warn("SelfHealingPlugin: Already started");
            return;
        }

        eventBus.subscribe(CommandExecutor.EVENT_CLUSTER_NODE_ADDED, this);
        eventBus.subscribe(CommandExecutor.EVENT_CLUSTER_NODE_REMOVED, this);
        eventBus.subscribe(NetdataCollector.NETDATA_NODE_OK, this);
        eventBus.subscribe(NetdataCollector.NETDATA_NODE_FAILED, this);
        log.info("SelfHealingPlugin: Started");
    }

    public synchronized void stop() {
        if (!started) {
            log.warn("SelfHealingPlugin: Not started");
            return;
        }

        eventBus.unsubscribe(CommandExecutor.EVENT_CLUSTER_NODE_ADDED, this);
        eventBus.unsubscribe(CommandExecutor.EVENT_CLUSTER_NODE_REMOVED, this);
        eventBus.unsubscribe(NetdataCollector.NETDATA_NODE_OK, this);
        eventBus.unsubscribe(NetdataCollector.NETDATA_NODE_FAILED, this);

        // Cancel all waiting recovery tasks
        waitingTasks.forEach((nodeKey,future) -> {
            future.cancel(true);
        });
        waitingTasks.clear();
        log.info("SelfHealingPlugin: Stopped");
    }

    @Override
    public void onMessage(String topic, Object message, Object sender) {
        log.debug("SelfHealingPlugin: onMessage(): BEGIN: topic={}, message={}, sender={}", topic, message, sender);
        if (!selfHealingProperties.isEnabled()) return;

        // Self-Healing for EMS clients
        if (CommandExecutor.EVENT_CLUSTER_NODE_REMOVED.equals(topic)) {
            log.debug("SelfHealingPlugin: onMessage(): CLUSTER NODE REMOVED: message={}", message);
            processClusterNodeRemovedEvent(message);
        } else
        if (CommandExecutor.EVENT_CLUSTER_NODE_ADDED.equals(topic)) {
            log.debug("SelfHealingPlugin: onMessage(): CLUSTER NODE ADDED: message={}", message);
            processClusterNodeAddedEvent(message);
        } else

        // Self-healing for Netdata agents
        if (NetdataCollector.NETDATA_NODE_FAILED.equals(topic)) {
            log.debug("SelfHealingPlugin: onMessage(): NETDATA NODE PAUSED: message={}", message);
            processNetdataNodeFailedEvent(message);
        } else
        if (NetdataCollector.NETDATA_NODE_OK.equals(topic)) {
            log.debug("SelfHealingPlugin: onMessage(): NETDATA NODE RESUMED: message={}", message);
            processNetdataNodeOkEvent(message);
        } else

        // Unsupported message
        {
            log.debug("SelfHealingPlugin: onMessage(): Unsupported message: topic={}, message={}, sender={}",
                    topic, message, sender);
        }
    }

    // ------------------------------------------------------------------------

    private void processClusterNodeRemovedEvent(Object message) {
        log.debug("SelfHealingPlugin: processClusterNodeRemovedEvent(): BEGIN: message={}", message);
        if (message instanceof ClusterMembershipEvent event) {
            // Get removed node id and address
            String nodeId = event.subject().id().id();
            String nodeAddress = event.subject().address().host();
            log.debug("SelfHealingPlugin: processClusterNodeRemovedEvent(): node-id={}, node-address={}", nodeId, nodeAddress);
            if (StringUtils.isBlank(nodeAddress)) {
                log.warn("SelfHealingPlugin: processClusterNodeRemovedEvent(): Node address is missing. Cannot recover node. Initial message: {}", event);
                return;
            }

            createRecoveryTask(nodeId, nodeAddress, recoveryContext, EmsClientRecoveryTask.class);
        } else {
            log.warn("SelfHealingPlugin: processClusterNodeRemovedEvent(): Message is not a {} object. Will ignore it.", ClusterMembershipEvent.class.getSimpleName());
        }
    }

    private void processClusterNodeAddedEvent(Object message) {
        log.debug("SelfHealingPlugin: processClusterNodeAddedEvent(): BEGIN: message={}", message);
        if (message instanceof ClusterMembershipEvent event) {
            // Get added node id and address
            String nodeId = event.subject().id().id();
            String nodeAddress = event.subject().address().host();
            log.debug("SelfHealingPlugin: processClusterNodeAddedEvent(): node-id={}, node-address={}", nodeId, nodeAddress);
            if (StringUtils.isBlank(nodeAddress)) {
                log.warn("SelfHealingPlugin: processClusterNodeAddedEvent(): Node address is missing. Initial message: {}", event);
                return;
            }

            // Cancel any waiting recovery task
            cancelRecoveryTask(nodeId, nodeAddress, EmsClientRecoveryTask.class, false);
        } else {
            log.warn("SelfHealingPlugin: processClusterNodeAddedEvent(): Message is not a {} object. Will ignore it.", ClusterMembershipEvent.class.getSimpleName());
        }
    }

    // ------------------------------------------------------------------------

    private void processNetdataNodeFailedEvent(Object message) {
        log.debug("SelfHealingPlugin: processNetdataNodeFailedEvent(): BEGIN: message={}", message);
        if (!(message instanceof Map)) {
            log.warn("SelfHealingPlugin: processNetdataNodeFailedEvent(): Message is not a {} object. Will ignore it.", Map.class.getSimpleName());
            return;
        }

        // Get paused node address
        Object addressValue = StrUtil.castToMapStringObject(message).getOrDefault("address", null);
        log.debug("SelfHealingPlugin: processNetdataNodeFailedEvent(): node-address={}", addressValue);
        if (addressValue==null) {
            log.warn("SelfHealingPlugin: processNetdataNodeFailedEvent(): Node address is missing. Cannot recover node. Initial message: {}", message);
            return;
        }
        String nodeAddress = addressValue.toString();

        if (isLocalAddress(nodeAddress)) {
            // We are responsible for recovering our local Netdata agent
            createRecoveryTask(null, "", recoveryContext, NetdataAgentLocalRecoveryTask.class);
        } else {
            // Aggregator is responsible for recovering remote Netdata agents
            createRecoveryTask(null, nodeAddress, recoveryContext, NetdataAgentRecoveryTask.class);
        }
    }

    @SneakyThrows
    private boolean isLocalAddress(String address) {
        if (address.isEmpty()) return true;
        if ("127.0.0.1".equals(address)) return true;
        if ("::1".equals(address)) return true;
        if ("0:0:0:0:0:0:0:1".equals(address)) return true;
        InetAddress ia = InetAddress.getByName(address);
        if (ia.isAnyLocalAddress() || ia.isLoopbackAddress()) return true;
        try {
            return NetworkInterface.getByInetAddress(ia) != null;
        } catch (SocketException se) {
            return false;
        }
    }

    private void processNetdataNodeOkEvent(Object message) {
        log.debug("SelfHealingPlugin: processNetdataNodeOkEvent(): BEGIN: message={}", message);
        if (!(message instanceof Map)) {
            log.warn("SelfHealingPlugin: processNetdataNodeOkEvent(): Message is not a {} object. Will ignore it.", Map.class.getSimpleName());
            return;
        }

        // Get resumed node address
        String nodeAddress = StrUtil.castToMapStringObject(message).getOrDefault("address", "").toString();
        log.debug("SelfHealingPlugin: processNetdataNodeOkEvent(): node-address={}", nodeAddress);
        /*if (StringUtils.isBlank(nodeAddress)) {
            log.warn("SelfHealingPlugin: processNetdataNodeOkEvent(): Node address is missing. Initial message: {}", message);
            return;
        }*/

        // Cancel any waiting recovery task
        @NonNull Class<? extends RecoveryTask> recoverTaskClass =
                StringUtils.isNotBlank(nodeAddress)
                        ? NetdataAgentRecoveryTask.class
                        : NetdataAgentLocalRecoveryTask.class;
        cancelRecoveryTask(null, nodeAddress, recoverTaskClass, false);
    }

    // ------------------------------------------------------------------------

    private void createRecoveryTask(String nodeId, @NonNull String nodeAddress, RecoveryContext recoveryContext, @NonNull Class<? extends RecoveryTask> recoveryTaskClass) {
        // Check if a recovery task has already been scheduled
        NodeKey nodeKey = new NodeKey(nodeAddress, recoveryTaskClass);
        synchronized (waitingTasks) {
            if (waitingTasks.containsKey(nodeKey)) {
                log.warn("SelfHealingPlugin: createRecoveryTask(): Recovery has already been scheduled for Node: id={}, address={}", nodeId, nodeAddress);
                return;
            }
            waitingTasks.put(nodeKey, null);
        }

        // Get node info and credentials from EMS server
        Map nodeInfo = null;
        if (StringUtils.isNotBlank(nodeAddress)) {
            nodeInfo = nodeInfoHelper.getNodeInfo(nodeId, nodeAddress);
            if (nodeInfo == null || nodeInfo.isEmpty()) {
                log.warn("SelfHealingPlugin: createRecoveryTask(): Node info is null or empty. Cannot recover node.");
                return;
            }
            log.trace("SelfHealingPlugin: createRecoveryTask(): Node info retrieved for node: id={}, address={}", nodeId, nodeAddress);
        } else {
            log.debug("SelfHealingPlugin: createRecoveryTask(): Node address is blank. Node info will not be retrieved: id={}, address={}", nodeId, nodeAddress);
        }

        // Schedule node recovery task
        final RecoveryTask recoveryTask = applicationContext.getBean(recoveryTaskClass);
        if (nodeInfo!=null && !nodeInfo.isEmpty())
            recoveryTask.setNodeInfo(nodeInfo);
        final AtomicInteger retries = new AtomicInteger(0);
        Instant firstAttempt;
        Duration retryDelay;
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        log.info("SelfHealingPlugin: Retry #{}: Recovering node: id={}, address={}", retries.get(), nodeId, nodeAddress);
                        recoveryTask.runNodeRecovery(recoveryContext);
                        //NOTE: 'recoveryTask.runNodeRecovery()' must send SELF_HEALING_RECOVERY_COMPLETED or _FAILED event
                    } catch (Exception e) {
                        log.error("SelfHealingPlugin: EXCEPTION while recovering node: node-address={} -- Exception: ", nodeAddress, e);
                        eventBus.send(RecoveryConstant.SELF_HEALING_RECOVERY_FAILED, nodeAddress);
                    }
                    if (retries.getAndIncrement() >= selfHealingProperties.getRecovery().getMaxRetries()) {
                        log.warn("SelfHealingPlugin: Max retries reached. No more recovery retries for node: id={}, address={}", nodeId, nodeAddress);
                        cancelRecoveryTask(nodeId, nodeAddress, recoveryTaskClass, true);
                        eventBus.send(RecoveryConstant.SELF_HEALING_RECOVERY_GIVE_UP, nodeAddress);

                        // Notify EMS server about giving up recovery due to permanent failure
                        commandExecutor.notifyEmsServer("RECOVERY GIVE_UP "+nodeId+" @ "+nodeAddress);
                    }
                },
                firstAttempt = Instant.now().plusMillis(selfHealingProperties.getRecovery().getDelay()),
                retryDelay = Duration.ofMillis(selfHealingProperties.getRecovery().getRetryDelay())
        );
        waitingTasks.put(nodeKey, future);
        log.info("SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id={}, address={}, first-attempt-at={}, retry-delay={}",
                nodeId, nodeAddress, firstAttempt, DurationFormatUtils.formatDurationHMS(retryDelay.toMillis()));
    }

    private void cancelRecoveryTask(String nodeId, @NonNull String nodeAddress, @NonNull Class<? extends RecoveryTask> recoveryTaskClass, boolean retainNodeKey) {
        NodeKey nodeKey = new NodeKey(nodeAddress, recoveryTaskClass);
        synchronized (waitingTasks) {
            ScheduledFuture<?> future = retainNodeKey ? waitingTasks.put(nodeKey, null) : waitingTasks.remove(nodeKey);
            if (future != null) {
                future.cancel(true);
                nodeInfoHelper.remove(nodeId, nodeAddress);
                log.info("SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id={}, address={}", nodeId, nodeAddress);
            } else
                log.debug("SelfHealingPlugin: cancelRecoveryTask(): No recovery task is scheduled for Node: id={}, address={}", nodeId, nodeAddress);
        }
    }

    @Data
    @AllArgsConstructor
    protected static class NodeKey {
        private String address;
        @NonNull private Class<?> recoveryTaskClass;
    }
}
