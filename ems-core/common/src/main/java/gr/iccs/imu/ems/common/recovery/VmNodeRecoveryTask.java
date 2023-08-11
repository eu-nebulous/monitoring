/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.recovery;

import gr.iccs.imu.ems.common.client.SshClient;
import gr.iccs.imu.ems.common.client.SshClientProperties;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.util.EventBus;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static gr.iccs.imu.ems.common.recovery.RecoveryConstant.SELF_HEALING_RECOVERY_COMPLETED;

/**
 * VM-node Self-Healing using an SSH connection
 */
@Slf4j
@Component
public class VmNodeRecoveryTask <P extends SshClientProperties> extends AbstractRecoveryTask {
    @NonNull private final CollectorContext<P> collectorContext;

    private P sshClientProperties;

    public VmNodeRecoveryTask(EventBus<String,Object,Object> eventBus, PasswordUtil passwordUtil, TaskScheduler taskScheduler, SelfHealingProperties selfHealingProperties, CollectorContext<P> collectorContext) {
        super(eventBus, passwordUtil, taskScheduler, selfHealingProperties);
        this.collectorContext = collectorContext;
    }

    public void setNodeInfo(@NonNull Map nodeInfo) {
        super.setNodeInfo(nodeInfo);
        this.sshClientProperties = createSshClientProperties();
    }

    @SneakyThrows
    public List<RECOVERY_COMMAND> getRecoveryCommands() {
        throw new Exception("Method not implemented. Use 'runNodeRecovery(List<RECOVERY_COMMAND>)' instead");
    }

    public void runNodeRecovery(RecoveryContext recoveryContext) throws Exception {
        throw new Exception("Method not implemented. Use 'runNodeRecovery(List<RECOVERY_COMMAND>)' instead");
    }

    public void runNodeRecovery(List<RECOVERY_COMMAND> recoveryCommands, RecoveryContext recoveryContext) throws Exception {
        log.debug("VmNodeRecoveryTask: runNodeRecovery(): BEGIN: recovery-command: {}", recoveryCommands);

        // Connect to Node (VM)
        SshClient<? extends SshClientProperties> sshc = connectToNode();

        // Redirect SSH output to standard output
        final AtomicBoolean closed = new AtomicBoolean(false);
        redirectSshOutput(sshc.getIn(), "OUT", closed);

        // Carrying out recovery commands
        log.info("VmNodeRecoveryTask: runNodeRecovery(): Executing {} recovery commands", recoveryCommands.size());
        for (RECOVERY_COMMAND command : recoveryCommands) {
            if (command==null || StringUtils.isBlank(command.getCommand())) continue;

            waitFor(command.getWaitBefore(), command.getName());

            // Send command to node for execution
            String commandString = prepareCommandString(command.getCommand(), recoveryContext);
            log.warn("##############  {}...", command.getName());
            log.warn("##############  Command: {}", commandString);
            sshc.getOut().println(commandString);
            waitFor(command.getWaitAfter(), command.getName());
        }
        log.info("VmNodeRecoveryTask: runNodeRecovery(): Executed {} recovery commands", recoveryCommands.size());

        // Disconnect from node
        disconnectFromNode(sshc, closed);

        // Send recovery complete event
        eventBus.send(SELF_HEALING_RECOVERY_COMPLETED, sshClientProperties.getServerAddress());
    }

    private String str(Object o) {
        if (o==null) return "";
        return o.toString();
    }

    private P createSshClientProperties() {
        log.debug("VmNodeRecoveryTask: createSshClientProperties(): BEGIN:");

        // Extract connection info and credentials
        String os = str(nodeInfo.get("operatingSystem"));
        String address = str(nodeInfo.get("address"));
        String type = str(nodeInfo.get("type"));
        String portStr = str(nodeInfo.get("ssh.port"));
        String username = str(nodeInfo.get("ssh.username"));
        String password = str(nodeInfo.get("ssh.password"));
        String key = str(nodeInfo.get("ssh.key"));
        String fingerprint = str(nodeInfo.get("ssh.fingerprint"));
        String keyAlgorithm = str(nodeInfo.get("ssh.key-algorithm"));
        String keyFormat = str(nodeInfo.get("ssh.key-format"));
        int port = 22;
        try {
            if (StringUtils.isNotBlank(portStr))
                port = Integer.parseInt(portStr);
            if (port<1 || port>65535)
                port = 22;
        } catch (Exception ignored) {}

        log.debug("VmNodeRecoveryTask: createSshClientProperties(): os={}, address={}, type={}", os, address, type);
        log.debug("VmNodeRecoveryTask: createSshClientProperties(): username={}, password={}", username, passwordUtil.encodePassword(password));
        log.debug("VmNodeRecoveryTask: createSshClientProperties(): fingerprint={}, key={}", fingerprint, passwordUtil.encodePassword(key));

        // Connect to node and restart EMS client
        P config = collectorContext.getSshClientProperties();
        config.setServerAddress(address);
        config.setServerPort(port);
        config.setServerUsername(username);
        if (!password.isEmpty()) {
            config.setServerPassword(password);
        }
        if (!key.isEmpty()) {
            config.setServerPubkey(key);
            config.setServerPubkeyFingerprint(fingerprint);
            config.setServerPubkeyAlgorithm(keyAlgorithm);
            config.setServerPubkeyFormat(keyFormat);
        }

        //XXX:TODO: Make recovery authTimeout configurable
        config.setAuthTimeout(60000);

        return config;
    }

    private SshClient<P> connectToNode() throws IOException {
        SshClient<P> sshc = collectorContext.getSshClient();
        sshc.setConfiguration(sshClientProperties);
        //XXX:TODO: Try enabling server key verification
        sshc.setUseServerKeyVerifier(false);
        log.info("VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address={}, port={}, username={}",
                sshClientProperties.getServerAddress(), sshClientProperties.getServerPort(), sshClientProperties.getServerUsername());
        sshc.start();
        log.debug("VmNodeRecoveryTask: connectToNode(): Connected to node: address={}, port={}, username={}",
                sshClientProperties.getServerAddress(), sshClientProperties.getServerPort(), sshClientProperties.getServerUsername());
        return sshc;
    }

    private void disconnectFromNode(SshClient sshc, AtomicBoolean closed) throws IOException {
        log.info("VmNodeRecoveryTask: disconnectFromNode(): Disconnecting from node: address={}, port={}, username={}",
                sshClientProperties.getServerAddress(), sshClientProperties.getServerPort(), sshClientProperties.getServerUsername());
        closed.set(true);
        sshc.stop();
        log.debug("VmNodeRecoveryTask: disconnectFromNode(): Disconnected from node: address={}, port={}, username={}",
                sshClientProperties.getServerAddress(), sshClientProperties.getServerPort(), sshClientProperties.getServerUsername());
    }

    private void redirectSshOutput(InputStream in, String id, AtomicBoolean closed) {
        redirectOutput(in, id, closed,
                "VmNodeRecoveryTask: redirectSshOutput(): Connection closed: id={}",
                "VmNodeRecoveryTask: redirectSshOutput(): Exception while copying SSH IN stream: id={}\n");
        //IoUtils.copy(sshc.getIn(), System.out);
    }
}
