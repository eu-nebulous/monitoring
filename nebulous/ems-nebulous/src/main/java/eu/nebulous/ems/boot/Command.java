/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.boot;

import eu.nebulouscloud.exn.core.Context;
import org.apache.qpid.protonj2.client.Message;

import java.util.Map;

record Command(String key, String address, Map body, Message message, Context context) {
}
