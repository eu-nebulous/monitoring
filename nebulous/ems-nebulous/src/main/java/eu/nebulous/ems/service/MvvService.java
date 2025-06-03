/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import eu.nebulous.ems.boot.ModelsService;
import gr.iccs.imu.ems.brokercep.cep.MathUtil;
import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.controller.ControlServiceRequestInfo;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.mvv.MetricVariableValuesService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MvvService implements MetricVariableValuesService {
	private final ApplicationContext applicationContext;

	private Map<String,Map<String,String>> bindings = Map.of();
	private Map<String,Double> values = Map.of();

	public Map<String,Map<String,String>> getBindings() {
		return new LinkedHashMap<>(bindings);
	}

	public void setBindings(@NonNull Map<String,Map<String,String>> bindings) {
		this.bindings = bindings;
	}

	public Map<String,Double> getValues() {
		return new LinkedHashMap<>(values);
	}

	public void setValues(@NonNull Map<String,Double> values) {
		this.values.clear();
		this.values.putAll(values);
	}

	@Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
	public void printValues() {
		Map<String, Double> vals = getValues();
		if (vals==null || vals.isEmpty())
			log.debug("MvvService: Curr. Values: ---");
		else
			log.debug("MvvService: Curr. Values: {}", vals);
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}

	public void translateAndSetValues(Map<String,Object> varValues) {
        // Check if bindings are available
        if (bindings == null || bindings.isEmpty()) {
            log.info("MvvService.translateAndSetValues: No bindings found: {}", bindings);
            return;
        }
		Map<String, String> simpleBindings = bindings.get(ModelsService.SIMPLE_BINDING_KEY);
        Map<String, String> compositeBindings = bindings.get(ModelsService.COMPOSITE_BINDING_KEY);
        if (simpleBindings==null || simpleBindings.isEmpty()) {
			log.error("MvvService.translateAndSetValues: No simple bindings found: {}", bindings);
			return;
		}

        // Log new values
        log.info("MvvService.translateAndSetValues: New Variable Values: {}", varValues);
        Map<String, Double> newValues = new HashMap<>();

        // Process simple constants
        varValues.forEach((k, v) -> {
            String constName = simpleBindings.get(k);
            if (StringUtils.isNotBlank(constName)) {
                if (v instanceof Number n)
                    newValues.put(constName, n.doubleValue());
                else
                    throw new IllegalArgumentException("Solution variable value is not a number: " + v);
                log.trace("MvvService.translateAndSetValues:   Added Simple Constant value: {} = {}", constName, n);
            } else
                log.warn("MvvService.translateAndSetValues:   No Constant found for Solution variable: {}", k);
        });
        log.debug("MvvService.translateAndSetValues: Simple Constant values: {}", newValues);

        // Process composite constants
		if (compositeBindings!=null && ! compositeBindings.isEmpty()) {
			Map<String, String> pending = new HashMap<>(bindings.get(ModelsService.COMPOSITE_BINDING_KEY));
			@NotNull final Map<String, Double> varValues1 = varValues.entrySet().stream().collect(Collectors.toMap(
					Map.Entry::getKey, e -> ((Number) e.getValue()).doubleValue()
			));
			while (!pending.isEmpty()) {
				Map<String, String> newPending = new HashMap<>();
				pending.forEach((formula, constName) -> {
					if (StringUtils.isNotBlank(formula) && StringUtils.isNotBlank(constName)) {
						try {
							log.trace("MvvService.translateAndSetValues:   Calculating Composite Constant value: {} ::= {} -- Values: {}", constName, formula, newValues);
							Double formulaValue = MathUtil.eval(formula, varValues1);
							newValues.put(constName, formulaValue);
							log.trace("MvvService.translateAndSetValues:   Added Composite Constant value: {} = {}", constName, formulaValue);
						} catch (Exception e) {
							log.trace("MvvService.translateAndSetValues:   Could not calculate Composite Constant value: {} ::= {} -- Values: {} -- Reason: {}", constName, formula, newValues, e.getMessage());
							newPending.put(formula, constName);
						}
					}
				});
				if (pending.size() == newPending.size())
					throw new IllegalArgumentException("Composite Constants cannot be calculated. Check for missing terms or circles in formulas: " + pending);
				pending = newPending;
			}
		}
        log.info("MvvService.translateAndSetValues: New Constant values: {}", newValues);

        setControlServiceConstants(newValues);
    }

	private void setControlServiceConstants(@NonNull Map<String, Double> newValues) {
		this.values = newValues;
		ControlServiceCoordinator controlServiceCoordinator =
				applicationContext.getBean(ControlServiceCoordinator.class);
		controlServiceCoordinator.setConstants(newValues, ControlServiceRequestInfo.EMPTY);
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