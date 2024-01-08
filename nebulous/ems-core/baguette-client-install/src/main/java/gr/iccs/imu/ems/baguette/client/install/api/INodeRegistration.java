/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.api;

import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.translate.TranslationContext;

import java.util.Map;

public interface INodeRegistration {
    String registerNode(String baseUrl, Map<String,Object> nodeInfo, TranslationContext translationContext) throws Exception;
    String unregisterNode(String nodeAddress, TranslationContext translationContext) throws Exception;
    String reinstallNode(String ipAddress, TranslationContext translationContext) throws Exception;
    NodeRegistryEntry requestNodeDetails(String nodeAddress) throws Exception;
    void requestInfo() throws Exception;
}
