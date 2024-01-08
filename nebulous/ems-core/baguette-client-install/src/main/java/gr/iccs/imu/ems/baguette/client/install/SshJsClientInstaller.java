/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install;

import gr.iccs.imu.ems.baguette.client.install.instruction.INSTRUCTION_RESULT;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsSet;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ResourceUtils;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * SSH-Javascript client installer
 */
@Slf4j
public class SshJsClientInstaller extends SshClientInstaller {

    @Builder(builderMethodName = "jsBuilder")
    public SshJsClientInstaller(ClientInstallationTask task, long taskCounter, ClientInstallationProperties properties) {
        super(task, taskCounter, properties);
    }

    @Override
    public boolean executeTask() {
        log.info("SshJsClientInstaller: Task #{}: Opening SSH connection...", getTaskCounter());
        if (!openSshConnection()) {
            return false;
        }

        boolean success;
        try {
            log.info("SshJsClientInstaller: Task #{}: Executing JS installation scripts...", getTaskCounter());
            INSTRUCTION_RESULT exitResult = executeJsScripts();
            success = exitResult != INSTRUCTION_RESULT.FAIL;
        } catch (Exception ex) {
            log.error("SshJsClientInstaller: Task #{}: Exception while executing JS installation scripts: ", getTaskCounter(), ex);
            success = false;
        }

        log.info("SshJsClientInstaller: Task #{}: Closing SSH connection...", getTaskCounter());
        return closeSshConnection(success);
    }

    private INSTRUCTION_RESULT executeJsScripts() throws IOException {
        List<String> jsScriptList = Optional.ofNullable(getTask().getInstructionSets())
                .orElseThrow(() -> new IllegalArgumentException("No SSH-Javascript installer scripts configured"))
                .stream()
                .map(InstructionsSet::getFileName)
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());
        log.debug("SshJsClientInstaller: Task #{}: Configured installation scripts: {}", getTaskCounter(), jsScriptList);
        if (jsScriptList.isEmpty())
            throw new IllegalArgumentException("SSH-Javascript installation scripts are blank");

        INSTRUCTION_RESULT exitResult = null;
        int cntSuccess = 0;
        int cntFail = 0;
        for (String jsScript : jsScriptList) {
            log.info("\n  ----------------------------------------------------------------------\n  Task #{} :  JS installation script: {}", getTaskCounter(), jsScript);

            // Execute JS installation script
            getStreamLogger().logMessage(
                    String.format("\n  ----------------------------------------------------------------------\n  Task #%d :  JS installation script: %s\n",
                    getTaskCounter(), jsScript));

            INSTRUCTION_RESULT result = executeJsScript(jsScript);

            if (result==INSTRUCTION_RESULT.FAIL) {
                log.error("SshJsClientInstaller: Task #{}: JS installation script failed: {}", getTaskCounter(), jsScript);
                getStreamLogger().logMessage(
                        String.format("\n  Task #%d :  JS installation script failed: %s\n", getTaskCounter(), jsScript));
                cntFail++;
                exitResult = INSTRUCTION_RESULT.FAIL;
                if (!isContinueOnFail()) {
                    break;
                }
            } else
            if (result==INSTRUCTION_RESULT.EXIT) {
                log.info("SshJsClientInstaller: Task #{}: JS installation script processing exits", getTaskCounter());
                getStreamLogger().logMessage(
                        String.format("\n  Task #%d :  JS installation script processing exits\n", getTaskCounter()));
                cntSuccess++;
                exitResult = INSTRUCTION_RESULT.EXIT;
                break;
            } else {
                log.info("SshJsClientInstaller: Task #{}: JS installation script succeeded: {}", getTaskCounter(), jsScript);
                getStreamLogger().logMessage(
                        String.format("\n  Task #%d :  JS installation script succeeded: %s\n", getTaskCounter(), jsScript));
                cntSuccess++;
                exitResult = INSTRUCTION_RESULT.SUCCESS;
            }
        }
        log.info("\n  -------------------------------------------------------------------------\n  Task #{} :  JS installation scripts processed: successful={}, failed={}, exit-result={}", getTaskCounter(), cntSuccess, cntFail, exitResult);
        getStreamLogger().logMessage(
                String.format("\n  ----------------------------------------------------------------------\n  Task #%d :  JS installation scripts processed: successful=%d, failed=%d, exit-result=%s\n", getTaskCounter(), cntSuccess, cntFail, exitResult));
        return exitResult;
    }

    public void printAndLog(Object args) {
        try {
            String message;
            if (args==null) {
                message = "null";
            } else
            if (args.getClass().isArray()) {
                message = Arrays.stream((Object[]) args)
                        .map(o -> o == null ? "null" : o.toString())
                        .collect(Collectors.joining(" "));
            } else {
                message = args.toString();
            }
            if (!message.endsWith("\n")) message += "\n";
//            getStreamLogger().getOut().write(String.format(message).getBytes());
            getStreamLogger().logMessage(message);
        } catch (IOException e) {
            log.error("SshJsClientInstaller: printAndLog: ", e);
        }
    }

    private INSTRUCTION_RESULT executeJsScript(String jsScript) {
        try {
            // Initializing JS engine
            log.debug("SshJsClientInstaller: Task #{}: Initializing JS engine", getTaskCounter());
            ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
            ScriptEngine engine = scriptEngineManager.getEngineByName("nashorn");
            engine.getContext().getBindings(ScriptContext.GLOBAL_SCOPE).put("installer", this);
            engine.getContext().getBindings(ScriptContext.GLOBAL_SCOPE).put("log", (Consumer<?>)this::printAndLog);

            log.debug("SshJsClientInstaller: Task #{}: Executing JS script: {}", getTaskCounter(), jsScript);
            File jsFile = ResourceUtils.getFile(jsScript);
            log.trace("SshJsClientInstaller: Task #{}: JS script file: {}", getTaskCounter(), jsFile);
            Object result = engine.eval(new FileReader(jsFile));

            if (result==null) {
                log.error("SshJsClientInstaller: Task #{}: JS installation script returned NULL: {}", getTaskCounter(), jsScript);
                return INSTRUCTION_RESULT.FAIL;
            }
            if (result instanceof Integer code) {
                log.info("SshJsClientInstaller: Task #{}: JS installation script returned: code={}, script: {}", getTaskCounter(), code, jsScript);
                return code==0 ? INSTRUCTION_RESULT.SUCCESS : INSTRUCTION_RESULT.FAIL;
            } else {
                log.error("SshJsClientInstaller: Task #{}: JS installation script returned NON-INTEGER value: {}, script: {}", getTaskCounter(), result, jsScript);
                return INSTRUCTION_RESULT.FAIL;
            }
        } catch (ScriptException | IOException e) {
            log.error("SshJsClientInstaller: Task #{}: Exception while executing script: {}, Exception: ", getTaskCounter(), jsScript, e);
            return INSTRUCTION_RESULT.FAIL;
        }
    }

    public String getInstallationResult() {
        return getTask().getNodeRegistryEntry().getPreregistration().get(getProperties().getClientInstallVarName());
    }

    public void setInstallationResult(boolean success) {
        getTask().getNodeRegistryEntry().getPreregistration().put(
                getProperties().getClientInstallVarName(),
                success ? "INSTALLED" : "ERROR");
    }

    public void clearInstallationResult() {
        getTask().getNodeRegistryEntry().getPreregistration().remove(
                getProperties().getClientInstallVarName());
    }
}
