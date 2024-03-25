/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.instruction;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstructionsService implements EnvironmentAware {
    private Environment environment;
    private final ResourceLoader resourceLoader;
    private static InstructionsService INSTANCE;

    public static InstructionsService getInstance() {
        if (INSTANCE==null) throw new IllegalStateException("InstructionsService singleton instance has not yet been initialized");
        return INSTANCE;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        INSTANCE = this;
    }

    public boolean checkCondition(@NonNull AbstractInstructionsBase i, Map<String,String> valueMap) {
        log.trace("InstructionsService: checkCondition: condition={}, value-map={}", i.getCondition(), valueMap);
        String condition = i.getCondition();
        if (StringUtils.isBlank(condition)) return true;
        String conditionResolved = processPlaceholders(condition, valueMap);
        log.trace("InstructionsService: checkCondition: Expression after placeholder resolution: {}", conditionResolved);
        final ExpressionParser parser = new SpelExpressionParser();
        Object result = parser.parseExpression(conditionResolved).getValue();
        log.trace("InstructionsService: checkCondition: Expression result: {}", result);
        if (result==null)
            throw new IllegalArgumentException("Condition evaluation returned null: " + condition);
        if (result instanceof Boolean booleanValue)
            return booleanValue;
        throw new IllegalArgumentException("Condition evaluation returned a non-boolean value: " + result + ", condition:  " + condition+", resolved condition: "+ conditionResolved);
    }

    public Instruction resolvePlaceholders(Instruction instruction, Map<String,String> valueMap) {
        return instruction.toBuilder()
                .description(processPlaceholders(instruction.description(), valueMap))
                .message(processPlaceholders(instruction.message(), valueMap))
                .command(processPlaceholders(instruction.command(), valueMap))
                .fileName(processPlaceholders(instruction.fileName(), valueMap))
                .localFileName(processPlaceholders(instruction.localFileName(), valueMap))
                .contents(processPlaceholders(instruction.contents(), valueMap))
                .build();
    }

    public String processPlaceholders(String s, Map<String,String> valueMap) {
        if (StringUtils.isBlank(s)) return s;
        s = StringSubstitutor.replace(s, valueMap);
        s = environment.resolvePlaceholders(s);
        //s = environment.resolveRequiredPlaceholders(s);
        //s = s.replace('\\', '/');
        return s;
    }

    public InstructionsSet loadInstructionsFile(@NonNull String fileName) throws IOException {
        if (StringUtils.isBlank(fileName))
            throw new IllegalArgumentException("File name is blank");
        fileName = fileName.trim();

        // Get file type from file extension
        String ext = null;
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            ext = fileName.substring(i+1);
            if (ext.contains("/") || ext.contains("\\")) ext = null;
        }
        if (ext==null)
            throw new IllegalArgumentException("Unknown file type: "+fileName);

        // Process instructions file based on its type
        try {
            if ("json".equalsIgnoreCase(ext)) {
                // Load instructions set from JSON file
                return _loadFromJsonFile(fileName);
            } else if ("yml".equalsIgnoreCase(ext) || "yaml".equalsIgnoreCase(ext)) {
                // Load instructions set from YAML file
                return _loadFromYamlFile(fileName);
            } else if ("js".equalsIgnoreCase(ext)) {
                // Just return an instruction set with the file name set
                InstructionsSet is = new InstructionsSet();
                is.setFileName(fileName);
                return is;
            }
        } catch (IOException e) {
            log.error("Exception thrown while processing instructions set file: {}", fileName);
            throw new IOException(fileName+": "+e.getMessage(), e);
        }
        throw new IllegalArgumentException("Unsupported file type: "+fileName);
    }

    private InstructionsSet _loadFromJsonFile(String jsonFile) throws IOException {
        log.debug("InstructionsService: Loading instructions from JSON file: {}", jsonFile);
        byte[] bdata = FileCopyUtils.copyToByteArray(resourceLoader.getResource(jsonFile).getInputStream());
        String jsonStr = new String(bdata, StandardCharsets.UTF_8);
        log.trace("InstructionsService: JSON instructions file contents: \n{}", jsonStr);

        // Create InstructionsSet object from JSON
        ObjectMapper mapper = new ObjectMapper();
        InstructionsSet instructionsSet = mapper.readerFor(InstructionsSet.class)
                .with(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .readValue(jsonStr);
        instructionsSet.setFileName(jsonFile);
        log.trace("InstructionsService: Installation instructions loaded from JSON file: {}\n{}", jsonFile, instructionsSet);

        return instructionsSet;
    }

    private InstructionsSet _loadFromYamlFile(String yamlFile) throws IOException {
        log.debug("InstructionsService: Loading instructions from YAML file: {}", yamlFile);
        byte[] bdata = FileCopyUtils.copyToByteArray(resourceLoader.getResource(yamlFile).getInputStream());
        String yamlStr = new String(bdata, StandardCharsets.UTF_8);
        log.trace("InstructionsService: YAML instructions file contents: \n{}", yamlStr);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        InstructionsSet instructionsSet =
                mapper.readValue(yamlStr, InstructionsSet.class);
        instructionsSet.setFileName(yamlFile);
        log.trace("InstructionsService: Installation instructions loaded from YAML file: {}\n{}", yamlFile, instructionsSet);

        return instructionsSet;
    }
}