/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@lombok.Data
@SuperBuilder
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MetricTemplate extends Feature {
    public final static short FORWARD_DIRECTION = 1;
    public final static short BACKWARD_DIRECTION = -1;

    private ValueType valueType;
    @Builder.Default
    private short valueDirection = FORWARD_DIRECTION;
    private String unit;
    private MeasurableAttribute attribute;

    @Builder.Default
    private double lowerBound = Double.NEGATIVE_INFINITY;
    @Builder.Default
    private double upperBound = Double.POSITIVE_INFINITY;
    private List<Object> allowedValues;
}
