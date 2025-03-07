package eu.nebulous.ems.boot;

import eu.nebulous.ems.AbstractBaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BootServiceTest extends AbstractBaseTest {

    public static final String TESTS_YAML_FILE = "src/test/resources/BootServiceTest.yaml";

    private EmsBootProperties properties;
    private BootService bootService;
    private IndexService indexService;

    @BeforeAll
    public void setUp() throws IOException {
        log.info("BootServiceTest: Setting up");
        properties = initializeEmsBootProperties();
        indexService = new IndexService(null, null, properties, objectMapper);
        bootService = new BootService(properties, indexService, objectMapper);
        log.debug("BootServiceTest: bootService: {}", bootService);
        indexService.initIndexFile();
        log.debug("BootServiceTest: indexService initialized!!");
    }

    private void initializeCache(Map data) throws IOException {
        String appId = Objects.requireNonNullElse(data.get("appId"), UUID.randomUUID()).toString();
        String timestamp = Objects.requireNonNullElse(data.get("timestamp"), Instant.now().toEpochMilli()).toString();
        Object model = data.get("model");
        Object bindings = data.get("bindings");
        Object solution = data.get("solution");
        Object metrics = data.get("metrics");

        String modelFile = writeToFile(appId, "model", timestamp, model);
        String bindingsFile = writeToFile(appId, "bindings", timestamp, bindings);
        String solutionFile = writeToFile(appId, "sol", timestamp, solution);
        String metricsFile = writeToFile(appId, "metrics", timestamp, metrics);

        indexService.deleteAll();
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put(ModelsService.MODEL_FILE_KEY, modelFile);
        entry.put(ModelsService.BINDINGS_FILE_KEY, bindingsFile);
        entry.put(ModelsService.SOLUTION_FILE_KEY, solutionFile);
        entry.put(ModelsService.OPTIMISER_METRICS_FILE_KEY, metricsFile);
        indexService.storeToIndex(appId, entry);
    }

    private String writeToFile(String appId, String fileType, String timestamp, Object contents) throws IOException {
        if (contents==null) return null;
        String fileName = String.format("%s--%s--%s.json", appId.trim(), fileType.trim(), timestamp.trim());
        String contentsStr = (contents instanceof String)
                ? contents.toString()
                : objectMapper.writeValueAsString(contents);
        Files.writeString(Paths.get(properties.getModelsDir(), fileName), contentsStr);
        return fileName;
    }

    @Test
    void processEmsBootMessage() throws IOException {
        loadAndRunTests("processEmsBootMessage", TESTS_YAML_FILE, (testDescription, yaml) -> {
            Map testData = toMap(yaml);
            log.debug("BootServiceTest: {}: testData: {}", testDescription, testData);
            String title = testData.getOrDefault("title", "").toString();
            String expected = testData.getOrDefault("expected_outcome", "").toString();

            initializeCache(testData);

            Command command = new Command("key", "topic", testData, null, null);
            String appId = testData.getOrDefault("appId", "").toString();
            String result = bootService.processEmsBootMessage(command, appId, (message, app_id) -> {});
            return Map.of(
                    "result", result,
                    "title", title,
                    "expected_outcome", expected
            );
        });
    }
}