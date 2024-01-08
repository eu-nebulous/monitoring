/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import gr.iccs.imu.ems.brokercep.cep.MathUtil;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.util.FunctionDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static eu.nebulous.ems.translate.analyze.AnalysisUtils.*;

// ------------------------------------------------------------------------
//  Custom Function processing methods
// ------------------------------------------------------------------------

@Slf4j
@Service
@RequiredArgsConstructor
class FunctionsHelper extends AbstractHelper {
    void processFunction(TranslationContext _TC, Object s) {
        // Get function definition elements
        String name = getSpecName(s);
        String expression = getMandatorySpecField(s, "expression", "Custom Function without expression: ");
        List<Object> args = asList(((Map<?, ?>) s).get("arguments"));

        // Check function definition elements
        if (StringUtils.isBlank(name)) throw createException("Custom Function with blank name: "+ s);
        if (StringUtils.isBlank(expression)) throw createException("Custom Function with blank expression: "+ s);
        if (args==null || args.isEmpty())
            throw createException("Custom Function without arguments: "+ s);
        if (args.stream().anyMatch(a -> !(a instanceof String)))
            throw createException("Custom Function spec contains non-string arguments: "+ s);

        // Check if function name is unique
        if (_TC.containsFunction(name) || $$(_TC).functionNames.contains(name))
            throw createException("Custom Function with 'name' already exists: "+ s);

        // Check if function definition is correct/valid
        List<String> argsList = args.stream().map(Object::toString).toList();
        FunctionDefinition fd = new FunctionDefinition()
                .setName(name).setExpression(expression).setArguments(argsList);
        MathUtil.addFunctionDefinition(fd);
        MathUtil.clearFunctionDefinitions();

        // Update TC
        _TC.addFunction(gr.iccs.imu.ems.translate.model.Function.builder()
                .name(name)
                .expression(expression)
                .arguments(argsList)
                .build());
        log.debug("Added custom function: {}", name);

        $$(_TC).functionNames.add(name);
    }
}