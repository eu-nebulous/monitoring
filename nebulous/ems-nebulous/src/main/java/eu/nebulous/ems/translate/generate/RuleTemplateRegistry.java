/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.generate;

import gr.iccs.imu.ems.util.EmsConstant;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Data
@Service
public class RuleTemplateRegistry implements InitializingBean {
    @ToString.Exclude
    private final ResourceLoader resourceLoader;

    private String language;
    private Map<String, Map<String, List<String>>> ruleTemplates;

    @Override
    public void afterPropertiesSet() throws IOException {
        loadRuleTemplates(resourceLoader);
        log.debug("RuleTemplateProperties: {}", this);
    }

    private void loadRuleTemplates(ResourceLoader resourceLoader) throws IOException {
        // Load templates from resource
        Resource resource = resourceLoader.getResource("classpath:rule-templates.yml");
        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

        // Parse resource contents (YAML) into a Map
        Yaml yaml = new Yaml();
        Map result = yaml.loadAs(content, Map.class);
        result = (Map) result.get(EmsConstant.EMS_PROPERTIES_PREFIX + "translator.generator");

        // Update variables
        language = result.get("language").toString();
        ruleTemplates = (Map<String,Map<String,List<String>>>)result.get("rule-templates");
    }

    public List<String> getTemplatesFor(String type, String grouping) {
        log.trace("RuleTemplateProperties.getTemplatesFor: type={}, grouping={}", type, grouping);
        List<String> list = Optional.ofNullable(ruleTemplates.get(type)).map(groupings -> groupings.get(grouping)).orElse(Collections.emptyList());
        log.trace("RuleTemplateProperties.getTemplatesFor: results={}", list);
        return list;
    }
}
