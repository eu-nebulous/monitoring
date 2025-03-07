/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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

	String processEmsBootMessage(Command command, String appId, BiConsumer<Map,String> emsBootResponsePublisher) throws IOException {
		// Process EMS Boot message
		log.info("Received a new EMS Boot message from external broker: {}", command.body());

		// Load info from models store
		Map<String, String> entry = indexService.getFromIndex(appId);
		log.debug("Index entry for app-id: {},  entry: {}", appId, entry);
		if (entry==null) {
			String msg = "No EMS Boot entry found for app-id: " + appId;
			log.warn(msg);
			return "ERROR "+msg;
		}
		String modelFile = entry.get(ModelsService.MODEL_FILE_KEY);
		String bindingsFile = entry.get(ModelsService.BINDINGS_FILE_KEY);
		String solutionFile = entry.get(ModelsService.SOLUTION_FILE_KEY);
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
			String msg = "Missing info in EMS Boot entry for app-id: " + appId;
			log.warn(msg);
			return "ERROR "+msg;
		}

		String modelStr = readFileContentsSafe(modelFile);
		log.debug("Model file contents:\n{}", modelStr);

		String bindingsStr = readFileContentsSafe(bindingsFile);
		Map bindingsMap = bindingsStr!=null ? objectMapper.readValue(bindingsStr, Map.class) : Map.of();
		log.debug("Bindings file contents:\n{}", bindingsMap);

		String solutionStr = readFileContentsSafe(solutionFile);
		Map solutionMap = solutionStr!=null ? objectMapper.readValue(solutionStr, Map.class) : Map.of();
		log.debug("Solution file contents:\n{}", solutionMap);

		String metricsStr = readFileContentsSafe(optimiserMetricsFile);
		List metricsList = metricsStr!=null ? objectMapper.readValue(metricsStr, List.class) : List.of();
		log.debug("Optimiser Metrics file contents:\n{}", metricsList);

		// Send EMS Boot response message
		Map<Object, Object> message = Map.of(
				"application", appId,
				"metric-model", modelStr,
				"bindings", bindingsMap,
				"solution", solutionMap,
				"optimiser-metrics", metricsList
		);
		log.debug("EMS Boot response message: {}", message);
		emsBootResponsePublisher.accept(message, appId);
		log.info("EMS Boot response sent");
		return "OK";
	}

	protected String readFileContentsSafe(String file) {
		if (StringUtils.isBlank(file)) return null;
		Path path = Paths.get(properties.getModelsDir(), file);
		if (! path.toFile().exists()) {
			log.warn("File not found in models dir.: {}", file);
			return null;
		}

		try {
			String contents = Files.readString(path);
			return contents;
		} catch (Exception e) {
			log.warn("Error while reading file: {}\n", file, e);
			return null;
		}
	}
}