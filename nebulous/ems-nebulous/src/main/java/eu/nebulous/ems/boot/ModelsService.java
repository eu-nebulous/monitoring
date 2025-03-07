/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.nebulous.ems.translate.TranslationService;
import gr.iccs.imu.ems.brokercep.cep.MathUtil;
import lombok.NonNull;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelsService implements InitializingBean {
	final static String MODEL_FILE_KEY = "model-file";
	final static String BINDINGS_FILE_KEY = "bindings-file";
	final static String SOLUTION_FILE_KEY = "solution-file";
	final static String OPTIMISER_METRICS_FILE_KEY = "optimiser-metrics-file";

	public final static String SIMPLE_BINDING_KEY = "simple-bindings";
	public final static String COMPOSITE_BINDING_KEY = "composite-bindings";

	private final TranslationService translationService;
	private final EmsBootProperties properties;
	private final ObjectMapper objectMapper;
	private final IndexService indexService;

	private final Map<String,Map<String,Double>> allVariableValues = new HashMap<>();

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
		// Process DSL Generic message
		log.debug("Received a new DSL Generic message from external broker: {}", command.body());

		// Extract EMS constants-to-Optimizer variables bindings
		Map<String,Map<String, String>> bindingsMap = null;
		try {
			List<Map<String,Object>> list = (List) command.body().get("utilityFunctions");
			if (list==null || list.isEmpty()) {
				log.warn("No utilityFunctions found in DSL generic message: {}", command.body());
			} else {
				Map<String, String> simpleBindingsMap = list.stream()
						.filter(uf -> "constant".equalsIgnoreCase(uf.getOrDefault("type", "").toString()))
						.filter(uf -> ((List) ((Map) uf.get("expression")).get("variables")).size() == 1
								&& StringUtils.equals(
										((Map) uf.get("expression")).getOrDefault("formula", "").toString().trim(),
										((Map) ((List) ((Map) uf.get("expression")).get("variables")).get(0)).getOrDefault("name", "").toString().trim()
								)
						)
						.collect(Collectors.toMap(
								uf -> ((Map) ((List) ((Map) uf.get("expression")).get("variables")).get(0)).getOrDefault("value", "").toString().trim(),
								uf -> uf.getOrDefault("name", "").toString().trim()
						));
				Map<String, String> compositeBindingsMap = list.stream()
						.filter(uf -> "constant".equalsIgnoreCase(uf.getOrDefault("type", "").toString()))
						.filter(uf -> ((List) ((Map) uf.get("expression")).get("variables")).size() > 1
								|| ! StringUtils.equals(
										((Map) uf.get("expression")).getOrDefault("formula", "").toString().trim(),
										((Map) ((List) ((Map) uf.get("expression")).get("variables")).get(0)).getOrDefault("name", "").toString().trim()
								)
						)
						.collect(Collectors.toMap(
								uf -> {
									log.trace("Got composite constant entry: {}", uf);

									// Get UF variable names-to-vela variables mapping
									final Map<String,String> varBindings = new HashMap<>();
									((List) ((Map) uf.get("expression")).get("variables")).forEach(o -> {
										if (o instanceof Map map) {
											String varName = map.getOrDefault("name", "").toString();
											String varValue = map.getOrDefault("value", "").toString();
											varBindings.put(varName, varValue);
										}
									});
									log.trace("Composite constant bindings: {}", varBindings);

									// Rename formula variables
									String formula = ((Map) uf.get("expression")).getOrDefault("formula", "").toString().trim();
									log.trace("Composite constant original formula: {}", formula);
									@NonNull String newFormula = MathUtil.renameFormulaArguments(formula, varBindings);
									log.trace("Composite constant modified formula: {}", newFormula);

									return newFormula;
								},
								uf -> uf.getOrDefault("name", "").toString().trim()
						));
				bindingsMap = Map.of(
						SIMPLE_BINDING_KEY, simpleBindingsMap,
						COMPOSITE_BINDING_KEY, compositeBindingsMap
				);
				if (simpleBindingsMap.isEmpty())
					log.warn("No bindings found in DSL generic message: {}", command.body());
			}
		} catch (Exception e) {
			log.warn("Error while extracting constants-optimiser variables bindings from DSL generic message: ", e);
			return "ERROR: Error while extracting constants-optimiser variables bindings from DSL generic message: "+e.getMessage();
		}

		// Store bindings in models store
		String bindingsFile = getFileName("bindings", appId, "json");
		if (bindingsMap==null) {
			bindingsMap = Map.of(
					SIMPLE_BINDING_KEY, Map.of(),
					COMPOSITE_BINDING_KEY, Map.of()
			);
		}
		storeToFile(bindingsFile, objectMapper.writeValueAsString(bindingsMap));
		log.info("Stored bindings in file: app-id={}, file={}", appId, bindingsFile);

		// Add appId-bindingsMap entry in the stored Index
		indexService.storeToIndex(appId, Map.of(BINDINGS_FILE_KEY, bindingsFile));

		return "OK";
	}

	String extractOptimiserMetrics(Command command, String appId) throws IOException {
		// Process Optimiser Metrics message
		log.debug("Received a new Optimiser Metrics message from external broker: {}", command.body());

		// Extract Optimizer metrics
		List<String> metricsList = null;
		try {
			List<String> list = (List) command.body().get("metrics");
			if (list==null || list.isEmpty()) {
				log.warn("No metrics found in Optimiser Metrics message: {}", command.body());
			} else {
				metricsList = list.stream()
						.filter(StringUtils::isNotBlank)
						.toList();
				if (metricsList.isEmpty())
					log.warn("No metrics found in Optimiser Metrics message: {}", command.body());
			}
		} catch (Exception e) {
			log.warn("Error while extracting metrics from Optimiser Metrics message: ", e);
			return "ERROR: Error while extracting metrics from Optimiser Metrics message: "+e.getMessage();
		}

		// Store metrics in models store
		String metricsFile = getFileName("metrics", appId, "json");
		if (metricsList==null) metricsList = List.of();
		storeToFile(metricsFile, objectMapper.writeValueAsString(metricsList));
		log.info("Stored metrics in file: app-id={}, file={}", appId, metricsFile);

		// Add appId-metricsList entry in the stored Index
		indexService.storeToIndex(appId, Map.of(OPTIMISER_METRICS_FILE_KEY, metricsFile));

		return "OK";
	}

	String extractSolution(Command command, String appId) throws IOException {
		// Process Optimiser Metrics message
		log.debug("Received a new Solution message from external broker: {}", command.body());

		// Extract Optimizer metrics
		boolean deployFlag;
		Map<String,Double> varValues;
		try {
			deployFlag = Boolean.parseBoolean(command.body().getOrDefault("DeploySolution", "false").toString());
			Map<String,Object> map = (Map) command.body().get("VariableValues");
			if (map==null || map.isEmpty()) {
				log.warn("No VariableValues found in Solution message: {}", command.body());
				return "ERROR: No VariableValues found in Solution message: "+command.body();
			} else {
				Map<String, Object> varValuesObj = map.entrySet().stream()
						.filter(e -> StringUtils.isNotBlank(e.getKey()))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
				if (varValuesObj.isEmpty()) {
					log.warn("Blank VariableValues found in Solution message: {}", command.body());
					return "ERROR: Blank VariableValues found in Solution message: "+command.body();
				}

				// Update variable values
				varValues = varValuesObj.entrySet().stream()
						.filter(e -> StringUtils.isNotBlank(e.getKey()))
						.filter(e -> e.getValue()!=null)
						.filter(e -> e.getValue() instanceof Number)
						.collect(Collectors.toMap(
								Map.Entry::getKey,
								e->((Number)e.getValue()).doubleValue()
						));
			}
		} catch (Exception e) {
			log.warn("Error while extracting VariableValues from Solution message: ", e);
			return "ERROR: Error while extracting VariableValues from Solution message: "+e.getMessage();
		}

		// Get previous application (UF) variable values (solution)
		Map<String, Double> appVariableValues = allVariableValues.computeIfAbsent(appId, app_id -> new HashMap<>());
		if (appVariableValues.isEmpty() || deployFlag)
			appVariableValues.putAll(varValues);

		// Store app solution in models store
		String solutionFile = getFileName("sol", appId, "json");
        storeToFile(solutionFile, objectMapper.writeValueAsString(appVariableValues));
		log.info("Stored solution in file: app-id={}, file={}", appId, solutionFile);

		// Add appId-solutionFile entry in the stored Index
		indexService.storeToIndex(appId, Map.of(SOLUTION_FILE_KEY, solutionFile));

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
		String modelStr;
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
			translationService.storeModel(appId, modelFile, modelStr);

			// Validate metric model
			String result = null;
			boolean valid = true;
			if (properties.isValidateModels()) {
				result = translationService.translateModel(appId, modelFile, modelStr);
				valid = "OK".equalsIgnoreCase(result);
			}

			// Add appId-modelFile entry in the stored Index
			if (valid || properties.isStoreInvalidModels())
				indexService.storeToIndex(appId, Map.of(MODEL_FILE_KEY,  modelFile));

			// Report invalid model
			if (!valid && properties.isReportInvalidModels())
				return "ERROR: Model invalid: " + result;

			return "OK";
		} else {
			log.warn("Metric Model message body is empty");
			return "ERROR: Metric Model message body is empty";
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