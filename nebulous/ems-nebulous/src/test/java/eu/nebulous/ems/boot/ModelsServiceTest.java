package eu.nebulous.ems.boot;

import eu.nebulous.ems.test.TestUtils;
import eu.nebulous.ems.translate.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static eu.nebulous.ems.test.TestUtils.toMap;

@Slf4j
@DisplayNameGeneration(DisplayNameGenerator.IndicativeSentences.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelsServiceTest {

    public static final String TESTS_YAML_FILE = "src/test/resources/ModelsServiceTest.yaml";

    private ModelsService modelsService;

    @BeforeAll
    public void setUp() throws IOException {
        log.info("ModelsServiceTest: Setting up");
        TranslationService translationService = new TranslationService(null, null);
        EmsBootProperties properties = TestUtils.initializeEmsBootProperties(this);
        IndexService indexService = new IndexService(null, null, properties, TestUtils.objectMapper);
        modelsService = new ModelsService(translationService, properties, TestUtils.objectMapper, indexService);
        log.debug("ModelsServiceTest: modelsService: {}", modelsService);
        indexService.initIndexFile();
        log.debug("ModelsServiceTest: indexService initialized!!");
    }

    public List<Arguments> testsForExtractBindings() throws IOException {
        return TestUtils.getTestsFromYamlFile(ModelsServiceTest.class, TESTS_YAML_FILE, "extractBindings");
    }

    @ParameterizedTest(name = "#{index}: {0}")
    @MethodSource("testsForExtractBindings")
    void extractBindings(String testDescription, Object jsonObj, String expectedOutcome) throws IOException {
        Map body = toMap(jsonObj);
        log.debug("ModelsServiceTest: {}: body: {}", testDescription, body);

        Command command = new Command("key", "topic", body, null, null);
        String appId = body.getOrDefault("uuid", "").toString();
        String result = modelsService.extractBindings(command, appId);

        if (StringUtils.isNotBlank(expectedOutcome))
            Assertions.assertEquals(expectedOutcome, TestUtils.getFirstTerm(result));
    }

    public List<Arguments> testsForExtractSolution() throws IOException {
        return TestUtils.getTestsFromYamlFile(ModelsServiceTest.class, TESTS_YAML_FILE, "extractSolution");
    }

    @ParameterizedTest(name = "#{index}: {0}")
    @MethodSource("testsForExtractSolution")
    void extractSolution(String testDescription, Object jsonObj, String expectedOutcome) throws IOException {
        Map body = toMap(jsonObj);
        log.debug("ModelsServiceTest: {}: body: {}", testDescription, body);

        Command command = new Command("key", "topic", body, null, null);
        String appId = body.getOrDefault("uuid", "").toString();
        String result = modelsService.extractSolution(command, appId);

        if (StringUtils.isNotBlank(expectedOutcome))
            Assertions.assertEquals(expectedOutcome, TestUtils.getFirstTerm(result));
    }
}