package eu.nebulous.ems.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import eu.nebulous.ems.boot.EmsBootProperties;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.platform.engine.TestExecutionResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public abstract class TestUtils {

    public static final String COLOR_WHITE = "\u001b[1;37m";
    public static final String COLOR_GREEN = "\u001b[32m";
    public static final String COLOR_YELLOW = "\u001b[33m";
    public static final String COLOR_RED = "\u001b[31m";
    public static final String COLOR_BLUE = "\u001b[34m";
    public static final String COLOR_MAGENTA = "\u001b[35m";

    public static final String COLOR_BOLD = "\u001b[1m";
    public static final String COLOR_ITALIC = "\u001b[3m";
    public static final String COLOR_UNDERLINE = "\u001b[4m";

    public static final String COLOR_WHITE_INTENSE = "\u001b[1;97m";
    public static final String COLOR_GREEN_INTENSE = COLOR_BOLD+COLOR_UNDERLINE+"\u001b[92m";
    public static final String COLOR_RED_INTENSE = COLOR_BOLD+COLOR_UNDERLINE+"\u001b[91m";
    public static final String COLOR_YELLOW_INTENSE = "\u001b[1;93m";
    public static final String COLOR_RESET = "\u001b[0m";

    public final static ObjectMapper objectMapper = new ObjectMapper();
    public final static ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public static EmsBootProperties initializeEmsBootProperties(@NonNull Object o) {
        return initializeEmsBootProperties(
                (o instanceof Class<?> c) ? c : o.getClass());
    }

    public static EmsBootProperties initializeEmsBootProperties(@NonNull Class<?> c) {
        EmsBootProperties properties = new EmsBootProperties();
        properties.setModelsDir("target/nebulous-tests/"+c.getSimpleName());
        Paths.get(properties.getModelsDir()).toFile().mkdirs();
        properties.setModelsIndexFile(properties.getModelsDir()+"/index.json");
        return properties;
    }

    protected static Map<String, Object> parserYaml(@NonNull String testsFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(testsFile)) {
            return yamlMapper.readValue(inputStream, Map.class);
        }
    }

    public static List<Arguments> getTestsFromYamlFile(@NonNull Object caller, @NonNull String testsFile, String key) throws IOException {
        Map<String, Object> data = parserYaml(testsFile);
        Object testData = data.get(key);
        if (testData==null) {
            log.warn(color("EXCEPTION", "Key '{}' not found or empty, in file: {}"), key, testsFile);
            return List.of();
        }
        if (testData instanceof List<?> list) {
            AtomicInteger testNum = new AtomicInteger(1);
            return list.stream()
                    .map(e -> {
                        String testDescription = color("INFO", String.format("%s #%d", key, testNum.getAndIncrement()));
                        if (e instanceof String testStr)
                            return Arguments.arguments(Named.of(testDescription, testDescription), testStr, null);
                        if (e instanceof Map testMap) {
                            String title = Objects.requireNonNullElse(testMap.getOrDefault("title", "").toString(), "");
                            String expected = Objects.requireNonNullElse(testMap.getOrDefault("expected_outcome", "").toString(), "");
                            if (StringUtils.isNotBlank(title))
                                testDescription = title;
                            return Arguments.arguments(Named.of(testDescription, testDescription), testMap, expected);
                        }
                        throw new IllegalArgumentException("Test item is neither string nor map, in key '"+key+"' at file "+testsFile);
                    })
                    .toList();
        }
        log.warn(color("EXCEPTION", "Key '{}' returned a non-map, in file: {} -- data: {}"), key, testsFile, testData);
        return List.of();
    }

    public static String getPassOrFail(String result, String expectedOutcome) {
        if (StringUtils.isBlank(expectedOutcome)) return "";
        if (result==null) result = "";
        String resultFirst = result.split("[^\\p{Alnum}]", 2)[0].toUpperCase();
        return StringUtils.equalsIgnoreCase(resultFirst, expectedOutcome) || expectedOutcome.equals(result)
                ? color("PASS", "PASS") : color("FAIL", "FAIL");
    }

    public static String getFirstTerm(String s) {
        if (StringUtils.isBlank(s)) return "";
        return s.split("[^\\p{Alnum}]", 2)[0].toUpperCase();
    }

    public static String getResultColor(TestExecutionResult result) {
        return switch (result.getStatus()) {
            case SUCCESSFUL -> "PASS";
            case FAILED -> "FAIL";
            case ABORTED -> "WARN";
        };
    }

    public static String color(String colorName, String message) {
        if (colorName==null) colorName = "";
        colorName = colorName.split("[^\\p{Alnum}]", 2)[0].toUpperCase();
        String color = null;
        switch (colorName) {
            case "OK" -> color = COLOR_GREEN;
            case "ERROR" -> color = COLOR_YELLOW;
            case "EXCEPTION" -> color = COLOR_RED;
            case "INFO" -> color = COLOR_BLUE;
            case "WARN" -> color = COLOR_MAGENTA;
            case "WHITE" -> color = COLOR_WHITE_INTENSE;
            case "YELLOW" -> color = COLOR_YELLOW_INTENSE;
            case "GREY" -> color = COLOR_WHITE;
            case "PASS", "SUCCESS" -> color = COLOR_GREEN_INTENSE;
            case "FAIL" -> color = COLOR_RED_INTENSE;
            case "SKIP" -> color = COLOR_ITALIC + COLOR_WHITE;
        }
        return color==null
                ? message
                : color + message + COLOR_RESET;
    }

    public static Map toMap(Object obj) throws IOException {
        if (obj instanceof Map map) return map;
        if (obj instanceof String s) return yamlMapper.readValue(s, Map.class);
        throw new ClassCastException("toMap: Cannot cast "+obj+" to Map");
    }
}