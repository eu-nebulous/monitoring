/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.instruction;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InstructionsSet extends AbstractInstructionsBase {
    private String os;
    private String description;
    private String fileName;
    private List<Instruction> instructions = new ArrayList<>();

    public List<Instruction> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }

    public void setInstructions(List<Instruction> ni) {
        instructions = new ArrayList<>(ni);
    }

    public InstructionsSet appendInstruction(Instruction i) {
        instructions.add(i);
        return this;
    }

    public InstructionsSet appendLog(String message) {
        instructions.add(Instruction.createLog(message));
        return this;
    }

    public InstructionsSet appendExec(String command) {
        instructions.add(Instruction.createShellCommand(command));
        return this;
    }

    public InstructionsSet appendWriteFile(String file, String contents, boolean executable) {
        instructions.add(Instruction.createWriteFile(file, contents, executable));
        return this;
    }

    public InstructionsSet appendUploadFile(String localFile, String remoteFile) {
        instructions.add(Instruction.createUploadFile(localFile, remoteFile));
        return this;
    }

    public InstructionsSet appendDownloadFile(String remoteFile, String localFile) {
        instructions.add(Instruction.createDownloadFile(remoteFile, localFile));
        return this;
    }

    public InstructionsSet appendCheck(String command, int exitCode, boolean match, String message) {
        instructions.add(Instruction.createCheck(command, exitCode, match, message));
        return this;
    }
}
