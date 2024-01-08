/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

public interface IKeystoreAndCertificateProperties {

    enum KEY_ENTRY_GENERATE { YES, ALWAYS, NO, NEVER, IF_MISSING, IF_IP_CHANGED };

    String getKeystoreFile();
    String getKeystoreType();
    String getKeystorePassword();
    String getTruststoreFile();
    String getTruststoreType();
    String getTruststorePassword();
    String getCertificateFile();

    KEY_ENTRY_GENERATE getKeyEntryGenerate();
    String getKeyEntryName();
    String getKeyEntryDName();
    String getKeyEntryExtSAN();
}
