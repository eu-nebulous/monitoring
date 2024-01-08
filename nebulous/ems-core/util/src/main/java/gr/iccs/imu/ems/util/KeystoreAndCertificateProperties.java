/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@ToString(exclude = {"truststorePassword", "keystorePassword"})
public class KeystoreAndCertificateProperties implements IKeystoreAndCertificateProperties {

    private String keystoreFile;
    private String keystoreType;
    private String keystorePassword;

    private String truststoreFile;
    private String truststoreType;
    private String truststorePassword;

    private String certificateFile;

    private KEY_ENTRY_GENERATE keyEntryGenerate;
    private String keyEntryName;
    private String keyEntryDName;
    private String keyEntryExtSAN;
}
