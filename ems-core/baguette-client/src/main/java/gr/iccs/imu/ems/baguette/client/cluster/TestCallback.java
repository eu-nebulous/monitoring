/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.cluster;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.Member;
import io.atomix.utils.net.Address;

public class TestCallback extends AbstractLogBase implements BrokerUtil.NodeCallback {
    private String address;
    private String state = "L1";

    public TestCallback(Address localAddress) {
        address = localAddress.toString();
    }

    public void joinedCluster() { }
    public void leftCluster() { }

    public void initialize() {
        if ("L2".equals(state)) {
            log_warn("__TestNode at {}: Already initialized: {}", address, state);
            return;
        }
        state = "initializing L2";
        out_print("__TestNode at {}: Initializing", address);
        for (int i = 0; i < (int) (Math.random() * 5 + 5); i++) {
            out_print(".");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
        out_println();
        if ("initializing L2".equals(state)) {
            state = "L2";
            log_info("__TestNode at {}: Node is now a Broker: {}", address, state);
        }
    }

    public void stepDown() {
        if ("L1".equals(state)) {
            log_warn("__TestNode at {}: Already a non-broker node: {}", address, state);
            return;
        }
        state = "clearing L2";
        out_print("__TestNode at {}: Stepping down", address);
        for (int i = 0; i < (int) (Math.random() * 4 + 2); i++) {
            out_print(".");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
        out_println();
        if ("clearing L2".equals(state)) {
            state = "L1";
            log_info("__TestNode at {}: Node is now a non-broker node: {}", address, state);
        }
    }

    public void statusChanged(BrokerUtil.NODE_STATUS oldStatus, BrokerUtil.NODE_STATUS newStatus) {
        log_info("__TestNode at {}: Status changed: {} --> {}", address, oldStatus, newStatus);
    }

    public void clusterChanged(ClusterMembershipEvent event) {
        log_info("__TestNode at {}: Cluster changed: {}: {}", address, event.type(), event.subject().id().id());
    }

    public String getConfiguration(Member local) {
        return String.format("ssl://%s:61617", local.address().host());
    }

    public void setConfiguration(String newConfig) {
        log_info("__TestNode at {}: New configuration: {}", address, newConfig);
    }
}
