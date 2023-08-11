/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.dag;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultEdge;

import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public class DAGEdge extends DefaultEdge {
    private final static AtomicLong edgeCounter = new AtomicLong();

    @Getter
    private final long id;

    public DAGEdge() {
        id = edgeCounter.getAndIncrement();
    }

    public DAGNode getSource() {
        return (DAGNode) super.getSource();
    }

    public DAGNode getTarget() {
        return (DAGNode) super.getTarget();
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object o) {
        return (o instanceof DAGEdge) && (toString().equals(o.toString()));
    }

    public String toString() {
        return "EDGE #" + id;
    }
}
