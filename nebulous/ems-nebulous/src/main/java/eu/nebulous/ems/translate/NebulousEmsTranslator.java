/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate;

import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.Translator;
import eu.nebulous.ems.translate.analyze.MetricModelAnalyzer;
import eu.nebulous.ems.translate.analyze.MetricModelValidator;
import eu.nebulous.ems.translate.analyze.ShorthandsExpansionHelper;
import eu.nebulous.ems.translate.generate.RuleGenerator;
import eu.nebulous.ems.translate.transform.GraphTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NebulousEmsTranslator implements Translator, InitializingBean {

	private final NebulousEmsTranslatorProperties properties;
	private final ShorthandsExpansionHelper shorthandsExpansionHelper;
	private final MetricModelValidator validator;
	private final MetricModelAnalyzer analyzer;
	private final GraphTransformer transformer;
	private final RuleGenerator generator;

	@Override
	public void afterPropertiesSet() throws Exception {
		log.info("NebulousEmsTranslator: initialized");
	}

	// ================================================================================================================
	// Public API

	@Override
	public TranslationContext translate(String metricModelPath) {
		return translate(metricModelPath, null);
	}

	@Override
	public TranslationContext translate(String metricModelPath, String applicationId) {
		return translate(metricModelPath, null, null);
	}

	@Override
	public TranslationContext translate(String metricModelPath, String applicationId, Map<String,Object> additionalArguments) {
		if (StringUtils.isBlank(metricModelPath)) {
			log.error("NebulousEmsTranslator: No metric model specified");
			throw new NebulousEmsTranslationException("No metric model specified");
		}

		log.info("NebulousEmsTranslator: Parsing metric model file: {}", metricModelPath);
		Object modelObj;
		try {
			// -- Load model ------------------------------------------------------
			Path inputFile = Paths.get(properties.getModelsDir(), metricModelPath);
			String yamlStr = Files.readString(inputFile);

			// Parsing YAML file with SnakeYAML, since Jackson Parser does not support Anchors and references
			Yaml yaml = new Yaml();
			modelObj = yaml.loadAs(yamlStr, Object.class);
			log.trace("NebulousEmsTranslator: YAML model contents:\n{}", modelObj);

			// -- Translate model ---------------------------------------------
			log.info("NebulousEmsTranslator: Translating metric model: {}", metricModelPath);
			TranslationContext _TC = translate(modelObj, metricModelPath, applicationId, additionalArguments);
			log.info("NebulousEmsTranslator: Translating metric model completed: {}", metricModelPath);

			return _TC;
		} catch (Exception e) {
			log.error("NebulousEmsTranslator: EXCEPTION while translating metric model file: {}\nException: ", e.getMessage(), e);
			throw new NebulousEmsTranslationException("Error while translating metric model file: "+metricModelPath, e);
		}
	}

	@Override
	public String getModel(String metricModelPath) {
		log.info("NebulousEmsTranslator: Getting metric model from file: {}", metricModelPath);
		try {
			// -- Load model ------------------------------------------------------
			Path inputFile = Paths.get(properties.getModelsDir(), metricModelPath);
			return Files.readString(inputFile);
		} catch (Exception e) {
			log.error("NebulousEmsTranslator: EXCEPTION while getting metric model from file: {}\nException: ", metricModelPath, e);
			throw new NebulousEmsTranslationException("Error while getting metric model from file: "+metricModelPath, e);
		}
	}

	@Override
	public String addModel(String metricModelPath, String metricModelStr) {
		log.info("NebulousEmsTranslator: Adding metric model to file: {}", metricModelPath);
		log.debug("NebulousEmsTranslator: New metric model: {}", metricModelStr);
		try {
			// -- Load old model --------------------------------------------------
			Path inputFile = Paths.get(properties.getModelsDir(), metricModelPath);
			String oldModelStr = null;
			if (inputFile.toFile().exists()) {
				oldModelStr = Files.readString(inputFile);
			}

			// -- Store new model -------------------------------------------------
			Files.writeString(inputFile, metricModelStr);

			// Return model old contents (if any)
			return oldModelStr;
		} catch (Exception e) {
			log.error("NebulousEmsTranslator: EXCEPTION while adding metric model to file: {}\nException: ", metricModelPath, e);
			throw new NebulousEmsTranslationException("Error while adding metric model to file: "+metricModelPath, e);
		}
	}

	@Override
	public boolean removeModel(String metricModelPath) {
		log.info("NebulousEmsTranslator: Removing metric model file: {}", metricModelPath);
		try {
			// -- Delete model ----------------------------------------------------
			Path inputFile = Paths.get(properties.getModelsDir(), metricModelPath);
			return inputFile.toFile().delete();
		} catch (Exception e) {
			log.error("NebulousEmsTranslator: EXCEPTION while removing metric model file: {}\nException: ", metricModelPath, e);
			throw new NebulousEmsTranslationException("Error while removing metric model file: "+metricModelPath, e);
		}
	}

	// ================================================================================================================
	// Private methods

	private TranslationContext translate(Object modelObj, String modelFileName, String appId, Map<String,Object> additionalArguments) throws Exception {
		log.debug("NebulousEmsTranslator.translate():  BEGIN: metric-model={}", modelObj);

		// Get model name
		String modelName = getModelName(modelFileName);

		// Initialize data structures
		TranslationContext _TC = new TranslationContext(modelName, modelFileName);
		_TC.setAppId(appId);
		if (additionalArguments!=null && additionalArguments.get("required-metrics") instanceof List l) {
			List<String> list = l.stream().filter(o -> o instanceof String).map(o -> String.class.cast(o)).toList();
			_TC.getAdditionalArguments().put("required-metrics", list);
		} else {
			_TC.getAdditionalArguments().put("required-metrics", List.of());
		}

		// -- Expand shorthand expressions ------------------------------------
		log.debug("NebulousEmsTranslator.translate(): Expanding shorthand expressions: {}", modelName);
		shorthandsExpansionHelper.expandShorthandExpressions(modelObj, modelName);

		// -- Schematron Validation -------------------------------------------
		log.debug("NebulousEmsTranslator.translate(): Validating metric model: {}", modelName);
		if (!properties.isSkipModelValidation()) {
			validator.validateModel(modelObj, modelName);
			log.debug("MetricModelAnalyzer.analyzeModel(): Metric model is valid: {}", modelName);
		}

		// -- Analyze metric model --------------------------------------------
		log.debug("NebulousEmsTranslator.translate():  Analyzing model...");
		analyzer.analyzeModel(_TC, modelObj, modelName);
		log.debug("NebulousEmsTranslator.translate():  Analyzing model... done");

		// -- Transform graph -------------------------------------------------
		//XXX:TODO: Not sure if it is needed in Nebulous (removes MVVs and adds TL metrics above TL metric contexts,
		//XXX:TODO: ... but MVVs are not used, neither metrics (only metric contexts)).
		log.debug("NebulousEmsTranslator.translate():  Transforming DAG...");
		transformer.transformGraph(_TC.getDAG());
		log.debug("NebulousEmsTranslator.translate():  Transforming DAG... done");

		// -- Generate EPL rules ----------------------------------------------
		log.debug("NebulousEmsTranslator.translate():  Generating EPL rules...");
		generator.generateRules(_TC);
		log.debug("NebulousEmsTranslator.translate():  Generating EPL rules... done");

		log.debug("NebulousEmsTranslator.translate():  END: metric-model={}", modelObj);
		log.trace("NebulousEmsTranslator.translate():  END: result={}", _TC);
		return _TC;
	}

	private String getModelName(String modelFileName) {
		String modelName = Paths.get(modelFileName).toFile().getName();
		modelName = StringUtils.removeEndIgnoreCase(modelName, ".yaml");
		modelName = StringUtils.removeEndIgnoreCase(modelName, ".yml");
		return modelName;
	}

}