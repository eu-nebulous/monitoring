/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.selfhealing;

import java.util.Collection;

public interface SelfHealingManager<T> {
    enum MODE { ALL, INCLUDED, EXCLUDED }
    enum NODE_STATE {NOT_MONITORED, UNKNOWN, OK, UP, ERROR, DOWN, RECOVERING }

    boolean isEnabled();
    void setEnabled(boolean b);

    MODE getMode();
    void setMode(MODE mode);

    Collection<T> getNodes();
    boolean containsNode(T node);
    boolean containsAny(Collection<T> nodes);
    boolean isMonitored(T node);
    void addNode(T node);
    void addAllNodes(Collection<T> nodes);
    void removeNode(T node);
    void removeAllNodes(Collection<T> nodes);
    void clear();

    NODE_STATE getNodeSelfHealingState(T node);
    String getNodeSelfHealingStateText(T node);
    default void setNodeSelfHealingState(T node, NODE_STATE state) {
        setNodeSelfHealingState(node, state, null);
    }
    void setNodeSelfHealingState(T node, NODE_STATE state, String text);
}
