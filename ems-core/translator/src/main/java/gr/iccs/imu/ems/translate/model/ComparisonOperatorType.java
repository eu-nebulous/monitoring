/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ComparisonOperatorType {
    GREATER_THAN("GREATER_THAN", ">"),
    GREATER_EQUAL_THAN("GREATER_EQUAL_THAN", ">="),
    LESS_THAN("LESS_THAN", "<"),
    LESS_EQUAL_THAN("LESS_EQUAL_THAN", "<="),
    EQUAL("EQUAL", "="),
    NOT_EQUAL("NOT_EQUAL", "<>");

    private final String name;
    private final String operator;
}
