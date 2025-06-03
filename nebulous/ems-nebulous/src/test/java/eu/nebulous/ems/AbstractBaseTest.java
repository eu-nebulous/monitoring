package eu.nebulous.ems;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import eu.nebulous.ems.boot.EmsBootProperties;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractBaseTest {

    public static final String COLOR_WHITE = "\u001b[1;97m";
    public static final String COLOR_GREEN = "\u001b[32m";
    public static final String COLOR_YELLOW = "\u001b[33m";
    public static final String COLOR_RED = "\u001b[31m";
    public static final String COLOR_BLUE = "\u001b[34m";
    public static final String COLOR_MAGENTA = "\u001b[35m";
    public static final String COLOR_GREEN_INTENSE = "\u001b[1m\u001b[4m\u001b[92m";
    public static final String COLOR_RED_INTENSE = "\u001b[1m\u001b[4m\u001b[91m";
    public static final String COLOR_RESET = "\u001b[0m";

    public static final String NO_RESULTS_COLORED = color("(no result returned)", "WARN");

    protected String callerClass = getClass().getSimpleName();
    protected ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    protected ObjectMapper objectMapper = new ObjectMapper();

    protected static LinkedHashMap<String,Map<String,String>> globalResults = new LinkedHashMap<>();
    protected static boolean printGlobalResultsSummary = true;
    protected LinkedHashMap<String,String> results = new LinkedHashMap<>();
    protected boolean printResultsSummary = false;

    @FunctionalInterface
    public interface CheckedBiFunction<T, U, R> {
        R apply(T t, U u) throws Exception;
    }

    public AbstractBaseTest() {
        globalResults.put(getClass().getSimpleName(), results);
    }

    protected EmsBootProperties initializeEmsBootProperties() {
        EmsBootProperties properties = new EmsBootProperties();
        properties.setModelsDir("target/nebulous-tests/"+getClass().getSimpleName());
        Paths.get(properties.getModelsDir()).toFile().mkdirs();
        properties.setModelsIndexFile(properties.getModelsDir()+"/index.json");
        return properties;
    }

    protected Map<String, Object> parserYaml(@NonNull String testsFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(testsFile)) {
            return yamlMapper.readValue(inputStream, Map.class);
        }
    }

    protected void loadAndRunTests(@NonNull String key, @NonNull String testsFile,
                                   @NonNull CheckedBiFunction<String, Object, Object> testRunner) throws IOException
    {
        Map<String, Object> data = parserYaml(testsFile);
        Object testData = data.get(key);
        if (testData==null) {
            log.warn(color("{}: Key not found or empty: {}", "EXCEPTION"), callerClass, key);
            return;
        }

        int testNum = 1;
        if (testData instanceof Collection<?> testsList) {
            // If test data object is a Collection iterate through it and run each test item
            for (Object jsonObj : testsList) {
                runTest(key, testNum, testRunner, jsonObj);
                testNum++;
            }
        } else {
            // If test data object is NOT a Collection then run ONE test passing it the test data object
            runTest(key, testNum, testRunner, testData);
        }
    }

    private void runTest(String key, int testNum, CheckedBiFunction<String, Object, Object> testRunner, Object dataObj) {
        String testDescription = String.format("Test %s #%d", key, testNum);
        try {
            log.info(color("{}:", "INFO"), callerClass);
            log.info(color("{}: ---------------------------------------------------------------", "INFO"), callerClass);
            log.info(color("{}: {}: json:\n{}", "INFO"), callerClass, testDescription, dataObj);
            Object resultObj = testRunner.apply(testDescription, dataObj);
            String result;
            String title = "";
            String expected_outcome = null;
            if (resultObj instanceof String s) result = s;
            else if (resultObj instanceof Map m) {
                result = m.getOrDefault("result", "").toString();
                title = m.getOrDefault("title", "").toString();
                if (! title.trim().isEmpty()) title = ": "+title+":\n          ";
                expected_outcome = m.getOrDefault("expected_outcome", "").toString();
            } else result = resultObj.toString();
            results.put(testDescription + color(title,"WHITE"),
                    getPassOrFail(result, expected_outcome) + " " + color(result,result));
            log.info(color("{}: {}: Result: {}", result), callerClass, testDescription,
                    Objects.requireNonNullElse(result, color("(no result returned)", "WARN")));
        } catch (Exception e) {
            log.warn(color("{}: {}: EXCEPTION: ", "EXCEPTION"), callerClass, testDescription, e);
            results.put(testDescription, "EXCEPTION: "+e.getMessage());
        }
    }

    private String getPassOrFail(String result, String expectedOutcome) {
        if (StringUtils.isBlank(expectedOutcome)) return "";
        if (result==null) result = "";
        String resultFirst = result.split("[^\\p{Alnum}]", 2)[0].toUpperCase();
        return StringUtils.equalsIgnoreCase(resultFirst, expectedOutcome) || expectedOutcome.equals(result)
                ? color("PASS","PASS") : color("FAIL", "FAIL");
    }

    private static String color(String message, String result) {
        if (result==null) result = "";
        result = result.split("[^\\p{Alnum}]", 2)[0].toUpperCase();
        String color = null;
        switch (result) {
            case "OK" -> color = COLOR_GREEN;
            case "ERROR" -> color = COLOR_YELLOW;
            case "EXCEPTION" -> color = COLOR_RED;
            case "INFO" -> color = COLOR_BLUE;
            case "WARN" -> color = COLOR_MAGENTA;
            case "WHITE" -> color = COLOR_WHITE;
            case "PASS" -> color = COLOR_GREEN_INTENSE;
            case "FAIL" -> color = COLOR_RED_INTENSE;
        }
        return color==null
                ? message
                : color + message + COLOR_RESET;
    }

    public Map toMap(Object obj) throws IOException {
        if (obj instanceof Map map) return map;
        if (obj instanceof String s) return yamlMapper.readValue(s, Map.class);
        throw new ClassCastException("toMap: Cannot cast "+obj+" to Map");
    }

    @AfterAll
    public void printResultsSummary() {
        if (! printResultsSummary) return;
        StringBuilder sb = new StringBuilder();
        doPrintResults(callerClass, results, sb);
        log.info(sb.toString());
    }

    @AfterAll
    public static void printGlobalResultsSummary() {
        if (! printGlobalResultsSummary) return;
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(color("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "WHITE")).append("\n");
        sb.append(color("━━━━━      Global Test Results      ━━━━━", "WHITE")).append("\n");
        sb.append(color("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "WHITE")).append("\n");
        globalResults.forEach((callerClass, results) -> {
            doPrintResults(callerClass, results, sb);
        });
        log.info(sb.toString());
    }

    private static void doPrintResults(String callerClass, Map<String,String> results, StringBuilder sb) {
        sb.append("\n");
        sb.append(String.format(color("  Test Results for: %s", "INFO"), callerClass)).append("\n");
        final AtomicInteger c = new AtomicInteger(1);
        results.forEach((testDescription, result) -> {
            sb.append(String.format("     %s. %s: %s", c.getAndIncrement(), testDescription, StringUtils.firstNonBlank(color(result, result), NO_RESULTS_COLORED))).append("\n");
        });
    }
}