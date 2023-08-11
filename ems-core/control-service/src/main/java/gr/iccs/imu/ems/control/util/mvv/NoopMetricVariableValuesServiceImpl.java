/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util.mvv;

import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.mvv.MetricVariableValuesService;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Service
public class NoopMetricVariableValuesServiceImpl implements MetricVariableValuesService {
    public void init() { }

    @SneakyThrows
    public Map<String, Double> getMatchingMetricVariableValues(String cpModelPath, TranslationContext _TC) {
        return Collections.emptyMap();
    }

    @SneakyThrows
    public Map<String, Double> getMetricVariableValues(String cpModelPath, Set<String> variableNames) {
        return Collections.emptyMap();
    }
}
