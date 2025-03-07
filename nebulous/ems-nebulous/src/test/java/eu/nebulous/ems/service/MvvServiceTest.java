package eu.nebulous.ems.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class MvvServiceTest {

    @Test
    void translateAndSetControlServiceConstants() {
        @NonNull Map<String, Map<String, String>> bindings = Map.of(
                "simple-bindings", Map.of(
                        "spec_components_1_traits_0_properties_replicas", "dosage_analysis_replica_count_const",
                        "spec_components_0_traits_0_properties_replicas", "data_collection_replica_count_const"
                ),
                "composite-bindings", Map.of(
                        "spec_components_0_traits_0_properties_replicas+spec_components_1_traits_0_properties_replicas+1", "total_instances_const"
                )
        );
        Map<String, Object> newValues = Map.of(
                "spec_components_1_traits_0_properties_replicas", 2.0,
                "spec_components_0_traits_0_properties_replicas", 3.0
        );
        log.info("MvvServiceTest:  bindings: {}", bindings);
        log.info("MvvServiceTest: newValues: {}", newValues);

        MvvService mvvService = new MvvService(null);
        mvvService.setBindings(bindings);
        log.info("MvvServiceTest: values BEFORE: {}", mvvService.getValues());
        try {
            mvvService.translateAndSetValues(newValues);
        } catch (Exception ignored) {}
        log.info("MvvServiceTest: values AFTER: {}", mvvService.getValues());
    }
}