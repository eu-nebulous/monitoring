/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * Logs and formats In/Out/Err streams
 */
@Slf4j
public class StreamLogger {
    private final FileOutputStream fos;
    private final PipedOutputStream pos;
    private final PipedInputStream pis;
    private final MonitorOutputStream mos;

    private final OutputStream ncInvertedIn;
    private final InputStream ncIn;
    private final OutputStream ncOut;
    private final OutputStream ncErr;

    private String lastLine;
    private long lastLineTime;

    public StreamLogger(String logFile) throws IOException {
        this(logFile, "");
    }

    public StreamLogger(String logFile, String prefix) throws IOException {
        this.fos = StringUtils.isNotBlank(logFile) ? new FileOutputStream(logFile) : null;
        this.pos = new PipedOutputStream();
        this.pis = new PipedInputStream(pos);
        this.mos = new MonitorOutputStream(this);

        this.ncIn = new LoggerInputStream(pis, prefix+"  IN", toArray(System.out, fos));
        this.ncInvertedIn = pos;
        this.ncOut = new LoggerOutputStream(prefix+" OUT", toArray(System.out, mos, fos));
        this.ncErr = new LoggerOutputStream(prefix+" ERR", toArray(System.err, fos));
    }

    private OutputStream[] toArray(OutputStream...streams) {
        return Arrays.stream(streams)
                .filter(Objects::nonNull)
                .toArray(OutputStream[]::new);
    }

    public InputStream getIn() { return ncIn; }

    public OutputStream getInvertedIn() {
        return ncInvertedIn;
    }

    public OutputStream getOut() {
        return ncOut;
    }

    public OutputStream getErr() {
        return ncErr;
    }

    public void close() throws IOException {
        if (fos!=null) fos.close();
        pos.close();
    }

    public void logMessage(String message) throws IOException {
        if (fos!=null) fos.write(message.getBytes());
    }

    private void newLine(String line, long timestamp) {
        lastLine = line;
        lastLineTime = timestamp;
    }

    static class LoggerInputStream extends InputStream {
        private final InputStream in;
        private final OutputStream[] streams;
        private final byte[] prefix;

        public LoggerInputStream(InputStream in, String prefix, OutputStream...streams) {
            this.in = in;
            this.prefix = (prefix+"< ").getBytes();
            this.streams = streams;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            writeToStreams(b);
            return b;
        }

        private void writeToStreams(int b) throws IOException {
            for (int i=0; i<streams.length; i++)
                streams[i].write(b);
        }
    }

    static class LoggerOutputStream extends OutputStream {
        private final OutputStream[] streams;
        private final byte[] prefix;
        private boolean newline = true;

        public LoggerOutputStream(String prefix, OutputStream...streams) {
            this.prefix = (prefix+"> ").getBytes();
            this.streams = streams;
        }

        @Override
        public void write(int b) throws IOException {
            if (newline) {
                writeToStreams(prefix);
                newline = false;
            }
            writeToStreams(b);
            if (b=='\n') newline = true;
        }

        private void writeToStreams(int b) throws IOException {
            for (int i=0; i<streams.length; i++)
                streams[i].write(b);
        }

        private void writeToStreams(byte[] buff) throws IOException {
            for (int i=0; i<streams.length; i++)
                streams[i].write(buff);
        }
    }

    @RequiredArgsConstructor
    static class MonitorOutputStream extends OutputStream {
        private final StreamLogger streamLogger;
        private boolean newline = true;
        private StringBuilder lastLine;

        @Override
        public void write(int b) throws IOException {
            if (newline) {
                this.lastLine = new StringBuilder();
                newline = false;
            }
            lastLine.append(b);
            if (b=='\n') {
                newline = true;
                signalLine();
            }
        }

        private void signalLine() {
            streamLogger.newLine(lastLine.toString(), System.currentTimeMillis());
        }
    }
}
