package eu.nebulous.ems.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.nebulous.ems.translate.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

@Slf4j
class ModelsServiceTest {

    private ObjectMapper objectMapper;
    private ModelsService modelsService;

    @BeforeEach
    public void setUp() throws IOException {
        log.info("ModelsServiceTest: Setting up");
        objectMapper = new ObjectMapper();
        TranslationService translationService = new TranslationService(null, null);
        EmsBootProperties properties = new EmsBootProperties();
        properties.setModelsDir("target");
        properties.setModelsIndexFile("target/index.json");
        IndexService indexService = new IndexService(null, null, properties, objectMapper);
        modelsService = new ModelsService(translationService, properties, objectMapper, indexService);
        log.debug("ModelsServiceTest: modelsService: {}", modelsService);
        indexService.initIndexFile();
        log.debug("ModelsServiceTest: indexService initialized!!");
    }

    @Test
    void extractBindings() throws IOException {
        if (objectMapper==null) {
            log.info("ModelsServiceTest: calling setUp!!");
            setUp();
        }

        String json = """
                {
                	"utilityFunctions": [
                		{
                			"name": "test_utility",
                			"type": "maximize",
                			"expression": {
                				"formula": "0.5*exp((log(0.001) * (mean_cpu_consumption_all - 50)^2) /1600) + 0.5*exp((log(0.001) * (mean_requests_per_second - 7)^2) /25)",
                				"variables": [
                					{
                						"name": "mean_cpu_consumption_all",
                						"value": "mean_cpu_consumption_all"
                					},
                					{
                						"name": "mean_requests_per_second",
                						"value": "mean_requests_per_second"
                					}
                				]
                			}
                		},
                		{
                			"name": "dosage_analysis_replica_count_const",
                			"type": "constant",
                			"expression": {
                				"formula": "dosage_analysis_replica_count_const",
                				"variables": [
                					{
                						"name": "dosage_analysis_replica_count_const",
                						"value": "spec_components_1_traits_0_properties_replicas"
                					}
                				]
                			}
                		},
                		{
                			"name": "data_collection_replica_count_const",
                			"type": "constant",
                			"expression": {
                				"formula": "data_collection_replica_count",
                				"variables": [
                					{
                						"name": "data_collection_replica_count",
                						"value": "spec_components_0_traits_0_properties_replicas"
                					}
                				]
                			}
                		},
                		{
                			"name": "total_instances_const",
                			"type": "constant",
                			"expression": {
                				"formula": "data_collection_replica_count+dosage_analysis_replica_count_const+1",
                				"variables": [
                					{
                						"name": "data_collection_replica_count",
                						"value": "spec_components_0_traits_0_properties_replicas"
                					},
                					{
                						"name": "dosage_analysis_replica_count_const",
                						"value": "spec_components_1_traits_0_properties_replicas"
                					}
                				]
                			}
                		}
                	],
                	"uuid": "1487b024-dcbb-4edc-b21b-998c71757566",
                	"_create": true,
                	"_delete": true
                }
                """;
        log.info("ModelsServiceTest: json:\n{}", json);

        Map body = objectMapper.readValue(json, Map.class);
        log.info("ModelsServiceTest: body: {}", body);

        Command command = new Command("key", "topic", body, null, null);
        String appId = body.getOrDefault("uuid", "").toString();
        String result = modelsService.extractBindings(command, appId);

        log.info("ModelsServiceTest: Result: {}", result);
    }
}