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
import gr.iccs.imu.ems.baguette.server.ServerCoordinator;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Vector;

@Slf4j
public class ServerCoordinatorWaitAll implements ServerCoordinator {
    private BaguetteServer server;
    private Runnable callback;
    private int expectedClients;
    private int numClients;
    private int phase;
    private Vector<ClientShellCommand> clients;
    private ClientShellCommand broker;
    private int readyClients;

    public void initialize(final TranslationContext TC, String upperwareGrouping, BaguetteServer server, Runnable callback) {
        this.server = server;
        this.expectedClients = server.getConfiguration().getNumberOfInstances();
        this.callback = callback;
        this.clients = new Vector<>();
        log.info("initialize: Done");
    }

    public BaguetteServer getServer() {
        return server;
    }

    public void start() {
    }

    public void stop() {
    }

    public int getPhase() {
        return phase;
    }

    public synchronized void register(ClientShellCommand c) {
        if (phase != 0) return;
        clients.add(c);
        numClients++;
        log.info("ServerCoordinatorWaitAll: {} of {} clients registered", numClients, expectedClients);
        if (numClients == expectedClients) {
            startPhase1();
        }
    }

    public synchronized void unregister(ClientShellCommand c) {
        if (phase != 0) return;
        clients.remove(c);
        numClients--;
    }

    protected synchronized void startPhase1() {
        if (phase != 0) return;
        log.info("ServerCoordinatorWaitAll: Phase #1");
        phase = 1;
        Thread runner = new Thread(new Runnable() {
            public void run() {
                // Pick a random client for Broker
                int sel = (int) Math.round((numClients - 1) * Math.random());
                if (sel >= numClients) sel = numClients - 1;
                broker = clients.get(sel);
                log.info("ServerCoordinatorWaitAll: Client #{} will become BROKER", broker.getId());

                // Signal BROKER to prepare
                phase = 2;
                broker.sendToClient("ROLE BROKER");
            }
        });
        runner.setDaemon(true);
        runner.start();
    }

    public synchronized void clientReady(ClientShellCommand c) {
        if (getPhase()==2) _brokerReady(c);
        else _clientReady(c);
    }

    private void _brokerReady(ClientShellCommand c) {
        if (phase != 2) return;
        log.info("ServerCoordinatorWaitAll: Broker is ready");
        phase = 3;
        readyClients = 1;
        if (readyClients == expectedClients) {
            phase = 4;
            signalTopologyReady();
        } else {
            Thread runner = new Thread(new Runnable() {
                public void run() {
                    // Signal all clients except broker to prepare
                    for (ClientShellCommand c : clients) {
                        if (c != broker) {
                            c.sendToClient("ROLE CLIENT");
                        }
                    }
                }
            });
            runner.setDaemon(true);
            runner.start();
        }
    }

    private void _clientReady(ClientShellCommand c) {
        if (phase != 3) return;
        readyClients++;
        log.info("ServerCoordinatorWaitAll: {} of {} clients are ready", readyClients, expectedClients);
        if (readyClients == expectedClients) {
            phase = 4;
            signalTopologyReady();
        }
    }

    protected void signalTopologyReady() {
        if (phase != 4) return;
        log.info("ServerCoordinatorWaitAll: Invoking callback");
        phase = 5;
        Thread runner = new Thread(new Runnable() {
            public void run() {
                // Invoke callback
                callback.run();
                log.info("ServerCoordinatorWaitAll: FINISHED");
            }
        });
        runner.setDaemon(true);
        runner.start();
    }
}
