/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.dag;

import gr.iccs.imu.ems.translate.Grouping;
import gr.iccs.imu.ems.translate.model.NamedElement;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Getter
@RequiredArgsConstructor
public class DAGNode implements Serializable {
    private final static AtomicLong counter = new AtomicLong();

    private final long id;
    private final String name;
    private final NamedElement element;
    private final String elementName;
    private Grouping grouping;
    private String topicName;
    private final Map<String,Object> properties = new LinkedHashMap<>();

    DAGNode() {
        id = counter.getAndIncrement();
        element = null;
        elementName = null;
        name = null;
    }

    public DAGNode(@NonNull NamedElement elem, @NonNull String fullName) {
        id = counter.getAndIncrement();
        element = elem;
        elementName = element.getName();
        name = fullName;
    }

    public DAGNode setGrouping(Grouping g) {
        grouping = g;
        return this;
    }

    public DAGNode setTopicName(String s) {
        topicName = s;
        return this;
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object o) {
        return (o instanceof DAGNode) && (toString().equals(o.toString()));
    }

    public String toString() {
        return "NODE "+ Objects.requireNonNullElse(name, "<ROOT>");
    }
}
