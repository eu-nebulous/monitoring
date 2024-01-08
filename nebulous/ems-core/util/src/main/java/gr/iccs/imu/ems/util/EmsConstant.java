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
 * EMS constant
 */
public class EmsConstant {
    public final static String EMS_PROPERTIES_PREFIX = ""; //""ems.";
    public final static String EVENT_PROPERTY_SOURCE_ADDRESS = "producer-host";
    public final static String EVENT_PROPERTY_ORIGINAL_DESTINATION = "original-destination";
    public final static String EVENT_PROPERTY_EFFECTIVE_DESTINATION = "effective-destination";

    public final static String COLLECTOR_DESTINATION_ALIASES = "destination-aliases";
    public final static String COLLECTOR_DESTINATION_ALIASES_DELIMITERS = "[,;: \t\r\n]+";
    public final static String COLLECTOR_ALLOWED_TOPICS_VAR = "COLLECTOR_ALLOWED_TOPICS";
}