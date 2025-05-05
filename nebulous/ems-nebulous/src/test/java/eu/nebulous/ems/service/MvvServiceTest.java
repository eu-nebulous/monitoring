package eu.nebulous.ems.service;

import eu.nebulous.ems.test.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static eu.nebulous.ems.test.TestUtils.toMap;

@Slf4j
@DisplayName("MvvService Tests")
@DisplayNameGeneration(DisplayNameGenerator.IndicativeSentences.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MvvServiceTest {

    public static final String TESTS_YAML_FILE = "src/test/resources/MvvServiceTest.yaml";

    private MvvService mvvService;

    @BeforeAll
    public void setUp() throws IOException {
        log.info("MvvServiceTest: Setting up");
        mvvService = new MvvService(null);
    }

    static List<Arguments> testsForTranslateAndSetControlServiceConstants() throws IOException {
        return TestUtils.getTestsFromYamlFile(MvvServiceTest.class, TESTS_YAML_FILE, "translateAndSetControlServiceConstants");
    }

//    @DisplayName("Translate and Set Constants")
    @ParameterizedTest(name = "#{index}: {0}")
    @MethodSource("testsForTranslateAndSetControlServiceConstants")
    void translateAndSetControlServiceConstants(String testDescription, Object yamlObj, String expectedOutcome) throws IOException {
        log.info("translateAndSetControlServiceConstants: === Test: {} ===", testDescription);
        Map testData = toMap(yamlObj);
        log.debug("translateAndSetControlServiceConstants: testData: {}", testData);

        Map bindings = toMap(Objects.requireNonNullElse(testData.get("bindings"), Map.of()));
        Map newValues = toMap(Objects.requireNonNullElse(testData.get("solution"), Map.of()));

        MvvService mvvService = new MvvService(null);
        mvvService.setBindings(bindings);
        log.info("translateAndSetControlServiceConstants: values BEFORE: {}", mvvService.getValues());
        try {
            mvvService.translateAndSetValues(newValues);
        } catch (NullPointerException ignored) {
            // Suppress NPE exception thrown because ControlServiceCoordinator is not available
        }
        log.info("translateAndSetControlServiceConstants: values AFTER: {}", mvvService.getValues());

        if (StringUtils.isNotBlank(expectedOutcome))
            Assertions.assertEquals(expectedOutcome, mvvService.getValues().toString());
    }
}