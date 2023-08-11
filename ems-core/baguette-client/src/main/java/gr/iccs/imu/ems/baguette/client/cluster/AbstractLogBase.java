/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.cluster;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;

import java.io.*;

@Data
@Slf4j
public abstract class AbstractLogBase {
    protected final static Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private BufferedReader rIn = new BufferedReader(new InputStreamReader(System.in));
    private InputStream in = System.in;
    private PrintStream out = System.out;
    private PrintStream err = System.err;
    private boolean logEnabled = true;
    private boolean outEnabled = true;

    public void setIn(InputStream in) { this.in = in; this.rIn = new BufferedReader(new InputStreamReader(in)); }

    protected String readLine(String prompt) throws IOException {
        out.print(prompt);
        out.flush();
        return rIn.readLine();
    }

    protected void log_trace(String formatter, Object...args) {
        if (log.isTraceEnabled()) {
            if (logEnabled) log.trace(formatter, args);
            if (outEnabled) out.println(MessageFormatter.arrayFormat(formatter, args).getMessage());
        }
    }

    protected void log_debug(String formatter, Object...args) {
        if (log.isDebugEnabled()) {
            if (logEnabled) log.debug(formatter, args);
            if (outEnabled) out.println(MessageFormatter.arrayFormat(formatter, args).getMessage());
        }
    }

    protected void log_info(String formatter, Object...args) {
        if (log.isInfoEnabled()) {
            if (logEnabled) log.info(formatter, args);
            if (outEnabled) out.println(MessageFormatter.arrayFormat(formatter, args).getMessage());
        }
    }

    protected void log_warn(String formatter, Object...args) {
        if (log.isWarnEnabled()) {
            if (logEnabled) log.warn(formatter, args);
            if (outEnabled) out.println(MessageFormatter.arrayFormat(formatter, args).getMessage());
        }
    }

    protected void log_error(String formatter) {
        if (log.isErrorEnabled()) {
            if (logEnabled) log.error(formatter);
            if (outEnabled) err.println(MessageFormatter.arrayFormat(
                    formatter, EMPTY_OBJECT_ARRAY, null).getMessage());
        }
    }

    protected void log_error(String formatter, Object...args) {
        if (log.isErrorEnabled()) {
            if (logEnabled) log.error(formatter, args);
            if (outEnabled) err.println(MessageFormatter.arrayFormat(formatter, args).getMessage());
        }
    }

    protected void log_error(String formatter, Exception ex) {
        if (log.isErrorEnabled()) {
            if (logEnabled) log.error(formatter, ex);
            if (outEnabled) {
                err.print(MessageFormatter.arrayFormat(
                        formatter, EMPTY_OBJECT_ARRAY, ex).getMessage());
                ex.printStackTrace(err);
            }
        }
    }

    protected void out_print(String formatter, Object...args) { stream_print(out, false, formatter, args); }
    protected void out_println(String formatter, Object...args) { stream_print(out, true, formatter, args); }
    protected void out_println() { stream_print(out, true, "", (Object)null); }
    protected void err_print(String formatter, Object...args) { stream_print(err, false, formatter, args); }
    protected void err_println(String formatter, Object...args) { stream_print(err, true, formatter, args); }
    protected void err_println() { stream_print(err, true, "", (Object)null); }

    protected void stream_print(PrintStream stream, boolean nl, String formatter, Object...args) {
        if (outEnabled) {
            String message = MessageFormatter.arrayFormat(formatter, args).getMessage();
            if (nl)
                stream.println(message);
            else
                stream.print(message);
            stream.flush();
        }
    }
}
