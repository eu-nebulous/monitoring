/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.recovery;

import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Service
@ToString
public class RecoveryContext {
    private final static List<String> variablesToRetrieve = List.of(
            "BAGUETTE_CLIENT_BASE_DIR:baseDir", "BAGUETTE_CLIENT_BASE_DIR:getBaseDir()");

    private final Map<String,String> variablesMap = new HashMap<>();

    public void initialize(@NonNull Object... sources) {
        for (Object source : sources) {
            log.trace("RecoveryContext.initialize: Processing source: {}", source);
            initialize(source);
        }
    }

    public void initialize(@NonNull Object source) {
        log.debug("RecoveryContext.initialize: BEGIN: source: {}", source);
        try {
            log.trace("RecoveryContext.initialize: variablesToRetrieve: {}", variablesToRetrieve);
            Map<String,String> vars = new HashMap<>();
            for (String varSpec : variablesToRetrieve) {
                log.trace("RecoveryContext.initialize: var-spec={}", varSpec);
                boolean isMethod = varSpec.endsWith("()");
                varSpec = isMethod ? varSpec.substring(0, varSpec.length()-2) : varSpec;

                String[] s = varSpec.split(":", 2);
                String entryName = s[0];
                String varName = s.length==2 ? s[1] : s[0];
                log.trace("RecoveryContext.initialize: is-method={}, var-name={}, entry-name={}", isMethod, varName, entryName);

                try {
                    Object varValue;
                    if (isMethod) {
                        log.trace("RecoveryContext.initialize: Retrieving Method: {}", varName);
                        Method method = source.getClass().getMethod(varName);
                        log.trace("RecoveryContext.initialize: Method: {}", method);
                        varValue = method.invoke(source);
                    } else {
                        log.trace("RecoveryContext.initialize: Retrieving Field: {}", varName);
                        Field field = source.getClass().getField(varName);
                        log.trace("RecoveryContext.initialize: Field: {}", field);
                        varValue = field.get(source);
                    }
                    log.trace("RecoveryContext.initialize: Var-value: {} = {}", varName, varValue);
                    if (varValue != null)
                        vars.put(entryName, varValue.toString());
                } catch (NoSuchFieldException | NoSuchMethodException e) {
                    log.trace("RecoveryContext.initialize: Method or Field not found or not accessible: {} -- Exception: ", varName, e);
                }
            }
            log.debug("RecoveryContext.initialize: Variables collected: {}", vars);

            log.trace("RecoveryContext.initialize: Variables map BEFORE update: {}", variablesMap);
            variablesMap.putAll(vars);
            log.trace("RecoveryContext.initialize: Variables map AFTER update: {}", variablesMap);

            log.debug("RecoveryContext.initialize: END");
        } catch (Exception e) {
            log.error("RecoveryContext.initialize: EXCEPTION: Source={}\n", source, e);
        }
    }
}