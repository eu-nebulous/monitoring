/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.nebulouscloud.exn.core.Publisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BootService implements InitializingBean {
	private final EmsBootProperties properties;
	private final IndexService indexService;
	private final ObjectMapper objectMapper;

	@Override
	public void afterPropertiesSet() throws Exception {
		log.info("EMS Boot Service: {}", properties.isEnabled() ? "enabled" : "disabled");
	}

	void processEmsBootMessage(Command command, String appId, Publisher emsBootResponsePublisher) throws IOException {
		// Process EMS Boot message
		log.info("Received a new EMS Boot message from external broker: {}", command.body());

		// Load info from models store
		Map<String, String> entry = indexService.getFromIndex(appId);
		log.debug("Index entry for app-id: {},  entry: {}", appId, entry);
		if (entry==null) {
			log.warn("No EMS Boot entry found for app-id: {}", appId);
			return;
		}
		String modelFile = entry.get(ModelsService.MODEL_FILE_KEY);
		String bindingsFile = entry.get(ModelsService.BINDINGS_FILE_KEY);
		String solutionFile = entry.get(ModelsService.SOLUTIONS_FILE_KEY);
		String optimiserMetricsFile = entry.get(ModelsService.OPTIMISER_METRICS_FILE_KEY);
		log.info("""
                Received EMS Boot request:
                           App-Id: {}
                       Model File: {}
                    Bindings File: {}
                    Solution File: {}
                Opt. Metrics File: {}
                """, appId, modelFile, bindingsFile, solutionFile, optimiserMetricsFile);

		if (StringUtils.isAnyBlank(appId, modelFile, bindingsFile, optimiserMetricsFile)) {
			log.warn("Missing info in EMS Boot entry for app-id: {}", appId);
			return;
		}

		String modelStr = Files.readString(Paths.get(properties.getModelsDir(), modelFile));
		log.debug("Model file contents:\n{}", modelStr);
		String bindingsStr = Files.readString(Paths.get(properties.getModelsDir(), bindingsFile));
		Map bindingsMap = objectMapper.readValue(bindingsStr, Map.class);
		log.debug("Bindings file contents:\n{}", bindingsMap);
		String solutionStr = Files.readString(Paths.get(properties.getModelsDir(), solutionFile));
		Map solutionMap = objectMapper.readValue(solutionStr, Map.class);
		log.debug("Solution file contents:\n{}", solutionMap);
		String metricsStr = Files.readString(Paths.get(properties.getModelsDir(), optimiserMetricsFile));
		List metricsList = objectMapper.readValue(metricsStr, List.class);
		log.debug("Optimiser Metrics file contents:\n{}", metricsList);

		// Send EMS Boot response message
		Map<Object, Object> message = Map.of(
				"application", appId,
				"metric-model", modelStr,
				"bindings", bindingsMap,
				"solution", solutionMap,
				"optimiser-metrics", metricsList
		);
		emsBootResponsePublisher.send(message, appId);
		log.info("EMS Boot response sent");
	}
}