/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.plugin.noop;

import gr.iccs.imu.ems.control.plugin.BeaconPlugin;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NoopBeaconPlugin implements BeaconPlugin {
    public void transmit(TopicBeacon.BeaconContext context) {
        log.trace("NoopBeaconPlugin.transmit(): Invoked");
    }
}
