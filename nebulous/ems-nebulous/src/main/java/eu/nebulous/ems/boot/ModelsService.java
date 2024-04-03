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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelsService implements InitializingBean {
	final static String MODEL_FILE_KEY = "model-file";
	final static String BINDINGS_FILE_KEY = "bindings-file";

	private final EmsBootProperties properties;
	private final ObjectMapper objectMapper;
	private final IndexService indexService;

	@Override
	public void afterPropertiesSet() throws Exception {
		if (! properties.isEnabled()) {
			log.info("ModelsService is disabled because EMS Boot is disabled");
			return;
		}
		initModelsStore();
	}

	private void initModelsStore() throws IOException {
		// Create and check models directory
		File f = Paths.get(properties.getModelsDir()).toFile();
		if (! f.exists()) f.mkdirs();
		if (! f.isDirectory())
			throw new IllegalArgumentException("Configured 'modelsDir' is not a directory: "+properties.getModelsDir());
		if (! f.canRead() || ! f.canWrite())
			throw new IllegalArgumentException("Cannot read or write to configured 'modelsDir': "+properties.getModelsDir());

		// Initialize models index file
		indexService.initIndexFile();
	}

	String extractBindings(Command command, String appId) throws IOException {
		// Process DEL Generic message
		log.debug("Received a new DSL Generic message from external broker: {}", command.body());

		// Extract EMS constants-to-Optimizer variables bindings
		Map<String, String> bindingsMap = null;
		try {
			List<Map<String,Object>> list = (List) command.body().get("utilityFunctions");
			if (list==null || list.isEmpty()) {
				log.warn("No utilityFunctions found in DSL generic message: {}", command.body());
			} else {
				bindingsMap = list.stream()
						.filter(uf -> "constant".equalsIgnoreCase( uf.getOrDefault("type", "").toString() ))
						.collect(Collectors.toMap(
								uf -> ((Map) ((List) ((Map) uf.get("expression")).get("variables")).get(0)).getOrDefault("value", "").toString(),
								uf -> uf.getOrDefault("name", "").toString()
						));
				if (bindingsMap.isEmpty())
					log.warn("No bindings found in DSL generic message: {}", command.body());
			}
		} catch (Exception e) {
			log.warn("Error while extracting constants-optimiser variables bindings from DSL generic message: ", e);
			return "ERROR: Error while extracting constants-optimiser variables bindings from DSL generic message: "+e.getMessage();
		}

		// Store bindings in models store
		String bindingsFile = getFileName("bindings", appId, "json");
		if (bindingsMap==null) bindingsMap = Map.of();
		storeToFile(bindingsFile, objectMapper.writeValueAsString(bindingsMap));
		log.info("Stored bindings in file: app-id={}, file={}", appId, bindingsFile);

		// Add appId-modelFile entry in the stored Index
		indexService.storeToIndex(appId, Map.of(BINDINGS_FILE_KEY, bindingsFile));

		return "OK";
	}

	String processMetricModelMessage(Command command, String appId) throws IOException {
		// Process metric model message
		log.debug("Received a new Metric Model message from external broker: {}", command.body());

		if (StringUtils.isBlank(appId)) {
			log.warn("AppId is blank");
			return "ERROR: AppId is blank";
		}

		// Get model string and/or model file
		Object modelObj = command.body().getOrDefault("model", null);
		if (modelObj==null) {
			modelObj = command.body().getOrDefault("yaml", null);
		}
		if (modelObj==null) {
			modelObj = command.body().getOrDefault("body", null);
		}
		String modelFile = command.body().getOrDefault("model-path", "").toString();

		// Check if 'model' or 'model-path' is provided
		if (modelObj==null && StringUtils.isBlank(modelFile)) {
			log.warn("No model found in Metric Model message: {}", command.body());
			return "ERROR: No model found in Metric Model message: "+command.body();
		}

        log.debug("modelObj: class={}, object={}", modelObj.getClass(), modelObj);
		String modelStr = null;
        if (modelObj instanceof String) {
            modelStr = (String) modelObj;
        } else
        if (modelObj instanceof Map) {
            modelStr = objectMapper.writeValueAsString(modelObj);
        } else {
			log.warn("Body of Metric Model message is of unsupported type: class={}, body={}", command.body().getClass(), command.body());
			return "ERROR: Unsupported body type in Metric Model message: "+command.body().getClass().getName();
		}

        // If 'model' string is provided, store it in a file
		if (StringUtils.isNotBlank(modelStr)) {
			// Store metric model in a new file
			modelFile = StringUtils.isBlank(modelFile) ? getFileName("model", appId, "yml") : modelFile;
			storeToFile(modelFile, modelStr);
			log.info("Stored metric model in file: app-id={}, file={}", appId, modelFile);

			// Add appId-modelFile entry in the stored Index
			indexService.storeToIndex(appId, Map.of(MODEL_FILE_KEY,  modelFile));

			return "OK";
		} else {
			log.warn("Metric Model message body is blank");
			return "ERROR: Metric Model message body is blank";
		}
	}

	private String getFileName(String type, String appId, String suffix) {
		if (StringUtils.isNotBlank(appId))
			appId = appId.replaceAll("[^a-zA-Z0-9\\-.]+", "_");
		if (StringUtils.isBlank(suffix))
			suffix = "txt";
		return StringUtils.isNotBlank(appId)
				? String.format("%s--%s--%d.%s", appId, type, System.currentTimeMillis(), suffix)
				: String.format("%s--%d.%s", type, System.currentTimeMillis(), suffix);
	}

	String readFromFile(String fileName) throws IOException {
		Path path = Paths.get(properties.getModelsDir(), fileName);
		String modelStr = Files.readString(path);
		log.debug("Read from file: {}", path);
		return modelStr;
	}

	private void storeToFile(String fileName, String modelStr) throws IOException {
		Path path = Paths.get(properties.getModelsDir(), fileName);
		Files.writeString(path, modelStr);
		log.debug("Wrote to file: {}", path);
	}
}