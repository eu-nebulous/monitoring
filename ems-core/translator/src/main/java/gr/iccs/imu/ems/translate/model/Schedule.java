/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.model;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@lombok.Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Schedule extends Feature {
    private Date start;
    private Date end;
    private String timeUnit;
    private long interval;
    private int repetitions;

    public long getIntervalInMillis() {
        if (timeUnit == null) return interval;
        return TimeUnit.MILLISECONDS.convert(interval, TimeUnit.valueOf(timeUnit.toUpperCase()));
    }
}
