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
public enum VariableType {
    CPU("cpu"),
    CORES("cores"),
    RAM("ram"),
    STORAGE("storage"),
    PROVIDER("provider"),
    CARDINALITY("cardinality"),
    OS("os"),
    LOCATION("location"),
    LATITUDE("latitude"),
    LONGITUDE("longitude");

    private final String name;
}
