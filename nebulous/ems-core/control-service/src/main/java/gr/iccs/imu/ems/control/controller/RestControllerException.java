/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import lombok.Getter;
import lombok.Setter;

public class RestControllerException extends RuntimeException {
    @Getter @Setter
    private int statusCode;

    public RestControllerException() { super(); }
    public RestControllerException(String message) { super(message); }
    public RestControllerException(Throwable cause) { super(cause); }
    public RestControllerException(String message, Throwable cause) { super(message, cause); }

    public RestControllerException(int code) { super(); statusCode = code; }
    public RestControllerException(int code, String message) { super(message); statusCode = code; }
    public RestControllerException(int code, Throwable cause) { super(cause); statusCode = code; }
    public RestControllerException(int code, String message, Throwable cause) { super(message, cause); statusCode = code; }
}
