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

import java.time.Instant;
import java.util.*;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class NodeRegistryEntry {
    public enum STATE {
        PREREGISTERED, IGNORE_NODE,
        INSTALLING, NOT_INSTALLED, INSTALLED, INSTALL_ERROR,
        WAITING_REGISTRATION, REGISTERING, REGISTERED, REGISTRATION_ERROR,
        DISCONNECTED, EXITING, EXITED, NODE_FAILED,
        REMOVING, REMOVED, REMOVE_ERROR, ARCHIVED
    };
    @Getter private final String ipAddress;
    @Getter private final String clientId;
    @JsonIgnore
    private final transient BaguetteServer baguetteServer;
    @Getter private String hostname;
    @Getter private STATE state = null;
    @Getter private Instant stateLastUpdate;
    @Getter private String reference = UUID.randomUUID().toString();
    @Getter private List<Object> errors = new LinkedList<>();
    @JsonIgnore
    @Getter private transient Map<String, String> preregistration = new LinkedHashMap<>();
    @JsonIgnore
    @Getter private transient Map<String, String> installation = new LinkedHashMap<>();
    @JsonIgnore
    @Getter private transient Map<String, String> registration = new LinkedHashMap<>();
    @JsonIgnore
    @Getter private transient Map<String, String> removal = new LinkedHashMap<>();
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
        stateLastUpdate = Instant.now();
    }

    public void refreshReference() { reference = UUID.randomUUID().toString(); }

    public boolean canRecover() {
        return state != null && switch (state) {
            case PREREGISTERED, IGNORE_NODE -> false;
            case REMOVING, REMOVED, REMOVE_ERROR, ARCHIVED -> false;
            default -> true;
        };
    }

    public boolean isArchived() {
        return state!=null && state==STATE.ARCHIVED;
    }

    public boolean canChangeStateTo(@NonNull STATE newState) {
        return ! isArchived();
    }

    private void _canUpdateEntry(@NonNull STATE newState) {
        if (! canChangeStateTo(newState)) {
            throw new IllegalStateException("Cannot change NodeRegistryEntry state from %s to %s: client-id=%s, client-address=%s"
                    .formatted(state, newState, clientId, ipAddress));
        }
    }

    private NodeRegistryEntry _updateEntry(@NonNull STATE newState, @NonNull Map<String, String> map, boolean clear, Map<String,Object> nodeInfo) {
        _canUpdateEntry(newState);
        if (clear) map.clear();
        map.putAll(StrUtil.deepFlattenMap(nodeInfo));
        setState(newState);
        return this;
    }

    private NodeRegistryEntry _updateEntry(@NonNull STATE newState, @NonNull Map<String, String> map, boolean clear, @NonNull String key, Object val, String defVal) {
        _canUpdateEntry(newState);
        if (clear) map.clear();
        map.put(key, val!=null ? val.toString() : defVal);
        setState(newState);
        return this;
    }

    public NodeRegistryEntry nodePreregistration(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.PREREGISTERED, preregistration, true, nodeInfo);
    }

    public NodeRegistryEntry nodeIgnore(Object nodeInfo) {
        return _updateEntry(STATE.IGNORE_NODE, installation, true, "ignore-node", nodeInfo, null);
    }

    public NodeRegistryEntry nodeInstalling(Object nodeInfo) {
        return _updateEntry(STATE.INSTALLING, installation, true, "installation-task", nodeInfo, "INSTALLING");
    }

    public NodeRegistryEntry nodeNotInstalled(Object nodeInfo) {
        return _updateEntry(STATE.NOT_INSTALLED, installation, true, "installation-task-result", nodeInfo, "NOT_INSTALLED");
    }

    public NodeRegistryEntry nodeInstallationComplete(Object nodeInfo) {
        return _updateEntry(STATE.INSTALLED, installation, false, "installation-task-result", nodeInfo, "SUCCESS");
    }

    public NodeRegistryEntry nodeInstallationError(Object nodeInfo) {
        return _updateEntry(STATE.INSTALL_ERROR, installation, false, "installation-task-result", nodeInfo, "ERROR");
    }

    public NodeRegistryEntry nodeRegistering(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.REGISTERING, registration, true, nodeInfo);
    }

    public NodeRegistryEntry nodeRegistered(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.REGISTERED, registration, false, nodeInfo);
    }

    public NodeRegistryEntry nodeRegistrationError(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.REGISTRATION_ERROR, registration, false, nodeInfo);
    }

    public NodeRegistryEntry nodeRegistrationError(Throwable t) {
        return _updateEntry(STATE.REGISTRATION_ERROR, registration, false, Collections.singletonMap("exception", t));
    }

    public NodeRegistryEntry nodeDisconnected(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.DISCONNECTED, registration, false, nodeInfo);
    }

    public NodeRegistryEntry nodeDisconnected(Throwable t) {
        return _updateEntry(STATE.DISCONNECTED, registration, false, Collections.singletonMap("exception", t));
    }

    public NodeRegistryEntry nodeExiting(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.EXITING, registration, false, nodeInfo);
    }

    public NodeRegistryEntry nodeExited(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.EXITED, registration, false, nodeInfo);
    }

    public NodeRegistryEntry nodeFailed(Map<String,Object> failInfo) {
        return _updateEntry(STATE.NODE_FAILED, registration, false, failInfo);
    }

    public NodeRegistryEntry nodeRemoving(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.REMOVING, removal, false, nodeInfo);
    }

    public NodeRegistryEntry nodeRemoved(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.REMOVED, removal, false, nodeInfo);
    }

    public NodeRegistryEntry nodeRemoveError(Map<String,Object> failInfo) {
        return _updateEntry(STATE.REMOVE_ERROR, removal, false, failInfo);
    }

    public NodeRegistryEntry nodeArchived(Map<String,Object> nodeInfo) {
        return _updateEntry(STATE.ARCHIVED, removal, false, nodeInfo);
    }
}
