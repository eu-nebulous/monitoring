/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@ToString
public class FunctionDefinition implements Serializable {
    private String name;
    private String expression;
    private List<String> arguments;

    public FunctionDefinition setName(String name) {
        this.name = name;
        return this;
    }

    public FunctionDefinition setExpression(String expression) {
        this.expression = expression;
        return this;
    }

    public FunctionDefinition setArguments(List<String> arguments) {
        this.arguments = new ArrayList<>(arguments);
        return this;
    }
}
