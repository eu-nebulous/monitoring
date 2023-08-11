/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.plugin.recovery;

import com.google.gson.Gson;
import gr.iccs.imu.ems.baguette.client.CommandExecutor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Node Info helper -- Retrieves node info from EMS server and caches them
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeInfoHelper {
    private final CommandExecutor commandExecutor;
    private final HashMap<String,Map> nodeInfoCache = new HashMap<>();
    private final Gson gson = new Gson();

    @SneakyThrows
    public Map getNodeInfo(String nodeId, @NonNull String nodeAddress) {
        log.debug("NodeInfoHelper: getNodeInfo(): BEGIN: node-id={}, node-address={}", nodeId, nodeAddress);

        // Get cached node info
        Map nodeInfo = nodeInfoCache.get(nodeAddress);

        if (nodeInfo==null) {
            // Get node info from EMS server
            try {
                log.debug("NodeInfoHelper: getNodeInfo(): Querying EMS server for Node Info: id={}, address={}", nodeId, nodeAddress);
                commandExecutor.executeCommand("SEND SERVER-GET-NODE-SSH-CREDENTIALS " + nodeAddress);
                String response = commandExecutor.getLastInputLine();
                log.debug("NodeInfoHelper: getNodeInfo(): Node Info from EMS server: id={}, address={}\n{}", nodeId, nodeAddress, response);
                if (StringUtils.isNotBlank(response)) {
                    nodeInfo = gson.fromJson(response, Map.class);
                }
                nodeInfoCache.put(nodeAddress, nodeInfo);
            } catch (Exception ex) {
                log.error("NodeInfoHelper: getNodeInfo(): Exception while querying for node info: node-id={}, node-address={}\n", nodeId, nodeAddress, ex);
                throw ex;
            }
        }
        //log.debug("NodeInfoHelper: getNodeInfo(): Node info: {}", nodeInfo);
        return nodeInfo;
    }

    public void remove(String nodeId, @NonNull String nodeAddress) {
        log.debug("NodeInfoHelper: remove(): node-id={}, node-address={}", nodeId, nodeAddress);
        Map nodeInfo = nodeInfoCache.remove(nodeAddress);
        log.trace("NodeInfoHelper: remove(): Removed: node-id={}, node-address={}", nodeId, nodeAddress);
    }
}
