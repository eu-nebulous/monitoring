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
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Baguette Client Grouping Configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"brokerPassword"})
public class GroupingConfiguration implements Serializable {
    @NonNull private String name;
    private Properties properties;
    @NonNull private Map<String, BrokerConnectionConfig> brokerConnections;
    @NonNull private Set<String> eventTypeNames;
    @NonNull private Map<String, Set<String>> rules;
    @NonNull private Map<String, Set<String>> connections;
    @NonNull private Set<FunctionDefinition> functionDefinitions;
    @NonNull private Map<String, Double> constants;
    private String brokerUsername;
    private String brokerPassword;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString(exclude = {"certificate", "password"})
    public static class BrokerConnectionConfig implements Serializable {
        private String grouping;
        private String url;
        private String certificate;
        private String username;
        private String password;
    }
}