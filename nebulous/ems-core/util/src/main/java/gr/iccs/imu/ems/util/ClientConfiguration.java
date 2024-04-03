/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Baguette Client Configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientConfiguration implements Serializable {
    @NonNull private Set<Serializable> nodesWithoutClient;
    private Map<String, List<Map<String,Serializable>>> collectorConfigurations;  // Collector Type - List of sensors' configuration Maps
}