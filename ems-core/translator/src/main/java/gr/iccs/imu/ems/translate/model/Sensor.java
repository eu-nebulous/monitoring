/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.model;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@lombok.Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Sensor extends Component {
    private String configurationStr;
    private boolean isPush;

    public Map<String, String> additionalProperties;

    public boolean isPullSensor() {
        return !isPush;
    }

    public boolean isPushSensor() {
        return isPush;
    }

    public PullSensor pullSensor() {
        if (this instanceof PullSensor)
            return (PullSensor) this;
        throw new IllegalArgumentException("Not a Pull sensor: " + this.getName());
    }

    public PushSensor pushSensor() {
        if (this instanceof PushSensor)
            return (PushSensor) this;
        throw new IllegalArgumentException("Not a Push sensor: " + this.getName());
    }
}
