package eu.nebulous.ems.service;

import eu.nebulous.ems.AbstractBaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Slf4j
class MvvServiceTest extends AbstractBaseTest {

    public static final String TESTS_YAML_FILE = "src/test/resources/MvvServiceTest.yaml";

    private MvvService mvvService;

    @BeforeAll
    public void setUp() throws IOException {
        log.info("MvvServiceTest: Setting up");
        mvvService = new MvvService(null);
    }

    @Test
    void translateAndSetControlServiceConstants() throws IOException {
        loadAndRunTests("translateAndSetControlServiceConstants", TESTS_YAML_FILE, (testDescription, yaml) -> {
            Map testData = toMap(yaml);
            log.debug("translateAndSetControlServiceConstants: {}: testData: {}", testDescription, testData);
            String title = testData.getOrDefault("title", "").toString();
            String expected = testData.getOrDefault("expected_outcome", "").toString();

            Map bindings = toMap(Objects.requireNonNullElse(testData.get("bindings"), Map.of()));
            Map newValues = toMap(Objects.requireNonNullElse(testData.get("solution"), Map.of()));

            MvvService mvvService = new MvvService(null);
            mvvService.setBindings(bindings);
            log.info("translateAndSetControlServiceConstants: values BEFORE: {}", mvvService.getValues());
            try {
                mvvService.translateAndSetValues(newValues);
            } catch (Exception ignored) {}
            log.info("translateAndSetControlServiceConstants: values AFTER: {}", mvvService.getValues());

            return Map.of(
                    "result", mvvService.getValues().toString(),
                    "title", title,
                    "expected_outcome", expected
            );
        });
    }
}