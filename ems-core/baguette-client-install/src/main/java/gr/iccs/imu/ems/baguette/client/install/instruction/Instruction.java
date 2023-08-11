/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.instruction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.regex.Pattern;

@Data
@Accessors(chain = true, fluent = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Getter(onMethod = @__(@JsonProperty))
public class Instruction extends AbstractInstructionsBase {
    private INSTRUCTION_TYPE taskType;
    private String description;
    private String message;
    private String command;
    private String fileName;
    private String localFileName;
    private String contents;
    private boolean executable;
    private int exitCode;
    private boolean match;
    private long executionTimeout;
    private int retries;

    private Map<String, Pattern> patterns;
    private Map<String, String> variables;

    // Fluent API addition
    public Instruction pattern(String varName, Pattern pattern) {
        this.patterns.put(varName, pattern);
        return this;
    }

    // Creators API
    public static Instruction createLog(@NotNull String message) {
        return Instruction.builder()
                .taskType(INSTRUCTION_TYPE.LOG)
                .command(message)
                .build();
    }

    public static Instruction createShellCommand(@NotNull String command) {
        return Instruction.builder()
                .taskType(INSTRUCTION_TYPE.CMD)
                .command(command)
                .build();
    }

    public static Instruction createWriteFile(@NotNull String file, String contents, boolean executable) {
        return Instruction.builder()
                .taskType(INSTRUCTION_TYPE.FILE)
                .fileName(file)
                .contents(contents==null ? "" : contents)
                .executable(executable)
                .build();
    }

    public static Instruction createUploadFile(@NotNull String localFile, @NotNull String remoteFile) {
        return Instruction.builder()
                .taskType(INSTRUCTION_TYPE.COPY)
                .fileName(remoteFile)
                .localFileName(localFile)
                .build();
   }

    public static Instruction createDownloadFile(@NotNull String remoteFile, @NotNull String localFile) {
        return Instruction.builder()
                .taskType(INSTRUCTION_TYPE.DOWNLOAD)
                .fileName(remoteFile)
                .localFileName(localFile)
                .build();
   }

    public static Instruction createCheck(@NotNull String command, @NotNull int exitCode, boolean match, String message) {
        return Instruction.builder()
                .taskType(INSTRUCTION_TYPE.CHECK)
                .command(command)
                .exitCode(exitCode)
                .match(match)
                .contents(message)
                .build();
    }
}