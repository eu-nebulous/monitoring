/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Builder
public class ControlServiceRequestInfo {
    public final static  ControlServiceRequestInfo EMPTY = create(null, null, null);

    private final String notificationUri;
    private final String requestUuid;
    @ToString.Exclude
    private final String jwtToken;

    public static ControlServiceRequestInfo create(String notificationUri, String requestUuid, String jwtToken) {
        return ControlServiceRequestInfo.builder()
                .notificationUri(notificationUri)
                .requestUuid(requestUuid)
                .jwtToken(jwtToken)
                .build();
    }
}