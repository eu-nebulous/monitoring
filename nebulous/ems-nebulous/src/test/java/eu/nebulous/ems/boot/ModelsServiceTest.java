package eu.nebulous.ems.boot;

import eu.nebulous.ems.AbstractBaseTest;
import eu.nebulous.ems.translate.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelsServiceTest extends AbstractBaseTest {

    public static final String TESTS_YAML_FILE = "src/test/resources/ModelsServiceTest.yaml";

    private ModelsService modelsService;

    @BeforeAll
    public void setUp() throws IOException {
        log.info("ModelsServiceTest: Setting up");
        TranslationService translationService = new TranslationService(null, null);
        EmsBootProperties properties = initializeEmsBootProperties();
        IndexService indexService = new IndexService(null, null, properties, objectMapper);
        modelsService = new ModelsService(translationService, properties, objectMapper, indexService);
        log.debug("ModelsServiceTest: modelsService: {}", modelsService);
        indexService.initIndexFile();
        log.debug("ModelsServiceTest: indexService initialized!!");
    }

    @Test
    void extractBindings() throws IOException {
        loadAndRunTests("extractBindings", TESTS_YAML_FILE, (testDescription, json) -> {
            Map body = toMap(json);
            log.info("ModelsServiceTest: {}: body: {}", testDescription, body);

            Command command = new Command("key", "topic", body, null, null);
            String appId = body.getOrDefault("uuid", "").toString();
            return modelsService.extractBindings(command, appId);
        });
    }

    @Test
    void extractSolution() throws IOException {
        loadAndRunTests("extractSolution", TESTS_YAML_FILE, (testDescription, json) -> {
            Map body = toMap(json);
            log.info("ModelsServiceTest: {}: body: {}", testDescription, body);

            Command command = new Command("key", "topic", body, null, null);
            String appId = body.getOrDefault("uuid", "").toString();
            return modelsService.extractSolution(command, appId);
        });
    }
}