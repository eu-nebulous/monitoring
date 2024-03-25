/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server.coordinator;

import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.baguette.server.ServerCoordinator;
import gr.iccs.imu.ems.baguette.server.properties.BaguetteServerProperties;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoopCoordinator implements ServerCoordinator {
    protected BaguetteServer server;
    protected BaguetteServerProperties config;
    protected Runnable callback;
    protected boolean started;

    @Override
    public void initialize(final TranslationContext TC, String upperwareGrouping, BaguetteServer server, Runnable callback) {
        if (_logInvocation("initialize", null, false)) return;
        this.server = server;
        this.config = server.getConfiguration();
        this.callback = callback;
    }

    @Override
    public BaguetteServer getServer() {
        return server;
    }

    @Override
    public void start() {
        if (_logInvocation("start", null, false)) return;
        started = true;

        if (callback != null) {
            log.info("{}: start(): Invoking callback", getClass().getSimpleName());
            callback.run();
        }
    }

    @Override
    public void stop() {
        if (!_logInvocation("stop", null, true)) return;
        started = false;
    }

    public boolean isStarted() {
        return started;
    }

    @Override
    public int getPhase() {
        return -1;
    }

    @Override
    public synchronized void preregister(NodeRegistryEntry entry) {
        _logInvocation("preregister", entry, true);
    }

    @Override
    public synchronized void register(ClientShellCommand c) {
        _logInvocation("register", c, true);
    }

    @Override
    public synchronized void unregister(ClientShellCommand c) {
        _logInvocation("unregister", c, true);
    }

    @Override
    public synchronized void clientReady(ClientShellCommand c) {
        _logInvocation("clientReady", c, true);
    }

    protected boolean _logInvocation(String methodName, Object o, boolean checkStarted) {
        String className = getClass().getSimpleName();
        String str =  (o==null) ? "" : (
                o instanceof ClientShellCommand
                        ? ". CSC: %s".formatted(o)
                        : (o instanceof NodeRegistryEntry
                                ? ". NRE: %s".formatted(o)
                                : ". Object: %s".formatted(o)) );
        if (checkStarted && !started) {
            log.warn("{}: {}(): Coordinator has not been started{}\n", className, methodName, str, new RuntimeException("DEBUG EXCEPTION"));
        } else
        if (!checkStarted && started) {
            log.warn("{}: {}(): Coordinator is already running{}\n", className, methodName, str, new RuntimeException("DEBUG EXCEPTION"));
        } else {
            log.debug("{}: {}(): Method invoked{}", className, methodName, str);
        }
        return started;
    }

    public void sleep(long millis) {
        try { Thread.sleep(millis); } catch (Exception ignored) { }
    }
}
