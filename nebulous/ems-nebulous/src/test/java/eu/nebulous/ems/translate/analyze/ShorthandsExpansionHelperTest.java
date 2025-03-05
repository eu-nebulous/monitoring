/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
class ShorthandsExpansionHelperTest {
    @Test
    void testExpandConstraint() throws IOException {
        Object spec = new HashMap<>(Map.of(
//                "constraint", "ZZ < 7 AND ( IF NOT XX <= 5 THEN 4 > YY Else XX <> 7 ) AND BBBB <> 8 OR CCC == 1"
                "constraint", "(mean_job < 7878 AND NOT (mean_job_process_time < 12) AND\n" +
                        "            job_process_time_instance < 1 AND time > 10 AND NOT (tstee <\n" +
                        "            312432))"
        ));
        ShorthandsExpansionHelper helper = new ShorthandsExpansionHelper(null);
        helper.expandConstraintExpression(spec);
        log.info("ShorthandsExpansionHelperTest:\n{}", spec);
    }
}