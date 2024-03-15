/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

/**
 * EMS Release info
 */
public class EmsRelease {
    public final static String EMS_ID = "ems";
    public final static String EMS_NAME = "Event Management System";
    public final static String EMS_VERSION = EmsRelease.class.getPackage().getImplementationVersion();
    public final static String EMS_COPYRIGHT =
            "Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)";
    public final static String EMS_LICENSE = "Mozilla Public License, v2.0";
    public final static String EMS_DESCRIPTION = String.format("\n%s (%s), v.%s, %s\n%s\n",
            EMS_NAME, EMS_ID, EMS_VERSION, EMS_LICENSE, EMS_COPYRIGHT);
}