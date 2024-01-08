/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@lombok.Data
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractRootObject implements Serializable {
    @JsonProperty("@objectClass")
    private final String _objectClass = getClass().getName();

    @JsonIgnore
    protected transient Object object;
    protected NamedElement container;

    public <T> T getObject(Class<T> c) {
        return c.cast(object);
    }

    public <T extends NamedElement> T getContainer(Class<T> c) {
        return c.cast(container);
    }
}
