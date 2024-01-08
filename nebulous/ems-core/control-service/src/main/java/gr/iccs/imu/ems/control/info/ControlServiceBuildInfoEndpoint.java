/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@Endpoint(id = "emsBuildInfo")
public class ControlServiceBuildInfoEndpoint {
    @Autowired
    private BuildInfoProvider buildInfoProvider;

    @ReadOperation
    public Map<String,Object> infoMap() { return buildInfoProvider.getMetricValues(); }

    @ReadOperation
    public Map<String,Object> info(@Selector String s) { return buildInfoProvider.getMetricValuesFor(s); }
}
