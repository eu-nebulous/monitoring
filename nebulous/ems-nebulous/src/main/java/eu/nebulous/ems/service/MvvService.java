/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.mvv.MetricVariableValuesService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MvvService implements MetricVariableValuesService {
	private Map<String,String> bindings = Map.of();
	private Map<String,Double> values = Map.of();

	public Map<String,String> getBindings() {
		return new LinkedHashMap<>(bindings);
	}

	public void setBindings(@NonNull Map<String,String> bindings) {
		this.bindings = bindings;
	}

	public void translateAndSetValues(Map<String,Double> varValues) {
		log.info("MvvService.translateAndSetValues: New Variable Values: {}", varValues);
		Map<String, Double> newValues = new HashMap<>();
		varValues.forEach((k,v) -> {
			String constName = bindings.get(k);
			if (StringUtils.isNotBlank(constName))
				newValues.put(constName, v);
		});
		log.info("MvvService.translateAndSetValues: New Constant values: {}", newValues);

		setValues(newValues);
	}

	private void setValues(@NonNull Map<String, Double> newValues) {
		this.values = newValues;
	}

	@Override
	public void init() {
		log.info("MvvService: initialized");
	}

	@Override
	public Map<String, Double> getMatchingMetricVariableValues(String cpModelPath, TranslationContext _TC) {
		return getMetricVariableValues(cpModelPath, _TC.getMvvCP().keySet());
	}

	@Override
	public Map<String, Double> getMetricVariableValues(String cpModelPath, Set<String> variableNames) {
		Map<String, Double> map = new HashMap<>(values);
		if (variableNames==null || variableNames.isEmpty()) return map;
		map.keySet().retainAll(variableNames);
		return map;
	}
}