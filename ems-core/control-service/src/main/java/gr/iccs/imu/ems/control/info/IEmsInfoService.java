/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import lombok.NonNull;

import java.util.Map;

public interface IEmsInfoService {
    String SYSTEM_INFO_PROVIDER = "system-info";
    String BUILD_INFO_PROVIDER = "build-info";
    String CONTROL_INFO_PROVIDER = "control";
    String BROKER_CEP_INFO_PROVIDER = "broker-cep";
    String BAGUETTE_SERVER_INFO_PROVIDER = "baguette-server";
    String CLIENT_INSTALLER_INFO_PROVIDER = "baguette-client-installer";
    String TRANSLATOR_INFO_PROVIDER = "translator";
    String MISC_INFO_PROVIDER = "misc-info";

    void clearServerMetricValues();
    Map<String,Object> getServerMetricValues();
    Map<String,Object> getServerMetricValuesFor(@NonNull String key);

    void clearClientMetricValues();
    Map<String,Object> getClientMetricValues();
    Map<String,Object> getClientMetricValues(@NonNull String clientId);
}
