/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate;

public enum Grouping {
    UNSPECIFIED(-1, false, false),
    PER_INSTANCE(0, true, false),
    PER_HOST(1, true, false),
    PER_ZONE(2, false, true),
    PER_REGION(3, false, true),
    PER_CLOUD(4, false, true),
    GLOBAL(5, false, false);

    private int order;
    private boolean sameHost;
    private boolean sameCloud;

    Grouping(int n, boolean sh, boolean sc) {
        order = n;
        sameHost = sh;
        sameCloud = sc;
    }

    public boolean equals(Grouping g) {
        return this.order == g.order;
    }

    public boolean lowerThan(Grouping g) {
        return this.order < g.order;
    }

    public boolean greaterThan(Grouping g) {
        return this.order > g.order;
    }
}