/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gr.iccs.imu.ems.baguette.server.coordinator.cluster.IClusterZone;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class NodeRegistryEntry {
    public enum STATE { PREREGISTERED, IGNORE_NODE, INSTALLING, NOT_INSTALLED, INSTALLED, INSTALL_ERROR,
        WAITING_REGISTRATION, REGISTERING, REGISTERED, REGISTRATION_ERROR, DISCONNECTED, EXITING, EXITED, NODE_FAILED
    };
    @Getter private final String ipAddress;
    @Getter private final String clientId;
    @JsonIgnore
    private final transient BaguetteServer baguetteServer;
    @Getter private String hostname;
    @Getter private STATE state = null;
    @Getter private Date stateLastUpdate;
    @Getter private String reference = UUID.randomUUID().toString();
    @Getter private List<Object> errors = new LinkedList<>();
    @JsonIgnore
    @Getter private transient Map<String, String> preregistration = new LinkedHashMap<>();
    @JsonIgnore
    @Getter private transient Map<String, String> installation = new LinkedHashMap<>();
    @JsonIgnore
    @Getter private transient Map<String, String> registration = new LinkedHashMap<>();
    @JsonIgnore
    @Getter @Setter private transient IClusterZone clusterZone;

    @JsonIgnore
    public BaguetteServer getBaguetteServer() {
        return baguetteServer;
    }

    public String getNodeId() {
        return getPreregistration().get("id");
    }

    public String getNodeAddress() {
        return ipAddress!=null ? ipAddress : getPreregistration().get("address");
    }

    public String getNodeIdOrAddress() {
        return StringUtils.isNotBlank(getNodeId()) ? getNodeId() : getNodeAddress();
    }

    public String getNodeIdAndAddress() {
        return getNodeId()+" @ "+getNodeAddress();
    }

    private void setState(@NonNull STATE s) {
        state = s;
        stateLastUpdate = new Date();
    }

    public void refreshReference() { reference = UUID.randomUUID().toString(); }

    public NodeRegistryEntry nodePreregistration(Map<String,Object> nodeInfo) {
        preregistration.clear();
        preregistration.putAll(StrUtil.deepFlattenMap(nodeInfo));
        setState(STATE.PREREGISTERED);
        return this;
    }

    public NodeRegistryEntry nodeIgnore(Object nodeInfo) {
        installation.clear();
        installation.put("ignore-node", nodeInfo!=null ? nodeInfo.toString() : null);
        setState(STATE.IGNORE_NODE);
        return this;
    }

    public NodeRegistryEntry nodeInstalling(Object nodeInfo) {
        installation.clear();
        installation.put("installation-task", nodeInfo!=null ? nodeInfo.toString() : "INSTALLING");
        setState(STATE.INSTALLING);
        return this;
    }

    public NodeRegistryEntry nodeNotInstalled(Object nodeInfo) {
        installation.clear();
        installation.put("installation-task-result", nodeInfo!=null ? nodeInfo.toString() : "NOT_INSTALLED");
        setState(STATE.NOT_INSTALLED);
        return this;
    }

    public NodeRegistryEntry nodeInstallationComplete(Object nodeInfo) {
        installation.put("installation-task-result", nodeInfo!=null ? nodeInfo.toString() : "SUCCESS");
        setState(STATE.INSTALLED);
        return this;
    }

    public NodeRegistryEntry nodeInstallationError(Object nodeInfo) {
        installation.put("installation-task-result", nodeInfo!=null ? nodeInfo.toString() : "ERROR");
        setState(STATE.INSTALL_ERROR);
        return this;
    }

    public NodeRegistryEntry nodeRegistering(Map<String,Object> nodeInfo) {
        registration.clear();
        registration.putAll(StrUtil.deepFlattenMap(nodeInfo));
        setState(STATE.REGISTERING);
        return this;
    }

    public NodeRegistryEntry nodeRegistered(Map<String,Object> nodeInfo) {
        //registration.clear();
        registration.putAll(StrUtil.deepFlattenMap(nodeInfo));
        setState(STATE.REGISTERED);
        return this;
    }

    public NodeRegistryEntry nodeRegistrationError(Map<String,Object> nodeInfo) {
        registration.putAll(StrUtil.deepFlattenMap(nodeInfo));
        setState(STATE.REGISTRATION_ERROR);
        return this;
    }

    public NodeRegistryEntry nodeRegistrationError(Throwable t) {
        registration.putAll(StrUtil.deepFlattenMap(Collections.singletonMap("exception", t)));
        setState(STATE.REGISTRATION_ERROR);
        return this;
    }

    public NodeRegistryEntry nodeDisconnected(Map<String,Object> nodeInfo) {
        registration.putAll(StrUtil.deepFlattenMap(nodeInfo));
        setState(STATE.DISCONNECTED);
        return this;
    }

    public NodeRegistryEntry nodeDisconnected(Throwable t) {
        registration.putAll(StrUtil.deepFlattenMap(Collections.singletonMap("exception", t)));
        setState(STATE.DISCONNECTED);
        return this;
    }

    public NodeRegistryEntry nodeExiting(Map<String,Object> nodeInfo) {
        registration.putAll(StrUtil.deepFlattenMap(nodeInfo));
        setState(STATE.EXITING);
        return this;
    }

    public NodeRegistryEntry nodeExited(Map<String,Object> nodeInfo) {
        registration.putAll(StrUtil.deepFlattenMap(nodeInfo));
        setState(STATE.EXITED);
        return this;
    }

    public NodeRegistryEntry nodeFailed(Map<String,Object> failInfo) {
        if (failInfo!=null)
            registration.putAll(StrUtil.deepFlattenMap(failInfo));
        setState(STATE.NODE_FAILED);
        return this;
    }
}
