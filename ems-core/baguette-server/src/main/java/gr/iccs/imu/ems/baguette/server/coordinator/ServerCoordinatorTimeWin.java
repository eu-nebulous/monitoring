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

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ServerCoordinatorTimeWin implements ServerCoordinator {
    private final ServerCoordinatorTimeWin LOCK = this;
    private BaguetteServer server;
    private Runnable callback;
    private boolean started;
    private long registrationWindow;
    private boolean registrationWindowEnded;
    private Thread timeout;
    private int numClients;
    private int phase;
    private List<ClientShellCommand> clients;
    private ClientShellCommand broker;
    private int readyClients;
    private String brokerCfgIpAddressCmd;
    private String brokerCfgPortCmd;

    public void initialize(final TranslationContext TC, String upperwareGrouping, BaguetteServer server, Runnable callback) {
        this.server = server;
        this.registrationWindow = server.getConfiguration().getRegistrationWindow();
        this.callback = callback;
        this.clients = new ArrayList<>();
    }

    public BaguetteServer getServer() {
        return server;
    }

    public void start() {
        timeout = new Thread(
                new Runnable() {
                    private long delay;

                    public void run() {
                        log.info("ServerCoordinatorTimeWin: REGISTRATION PERIOD STARTS");
                        started = true;
                        registrationWindowEnded = false;
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ex) {
                            log.info("ServerCoordinatorTimeWin: INTERRUPTED: Registration stopped");
                            return;
                        }
                        log.info("ServerCoordinatorTimeWin: REGISTRATION PERIOD ENDS");

                        List<ClientShellCommand> registeredIntime;
                        synchronized (LOCK) {
                            registeredIntime = new ArrayList<>(clients);
                        }
                        if (registeredIntime.size() > 0) {
                            startPhase1(registeredIntime);
                        } else {
                            registrationWindowEnded = true;
                            log.warn("ServerCoordinatorTimeWin: No clients have been registered");
                            log.warn("ServerCoordinatorTimeWin: The first client to register will become BROKER");
                        }
                    }

                    public Runnable setDelay(long delay) {
                        this.delay = delay;
                        return this;
                    }
                }
                        .setDelay(registrationWindow)
        );
        timeout.setDaemon(true);
        timeout.start();
        log.info("ServerCoordinatorTimeWin: START");
    }

    public void stop() {
        started = false;
        if (timeout.isAlive()) timeout.interrupt();
    }

    public boolean isStarted() {
        return started;
    }

    public int getPhase() {
        return phase;
    }

    public synchronized void register(ClientShellCommand c) {
        if (!started) return;
        //if (phase!=0) return;
        clients.add(c);
        numClients++;
        if (phase == 0 && numClients == 1 && registrationWindowEnded) startPhase1(clients);
        else if (phase != 0) {
            c.sendToClient(brokerCfgIpAddressCmd);
            c.sendToClient(brokerCfgPortCmd);
            c.sendToClient("ROLE CLIENT");
        }
        log.info("ServerCoordinatorTimeWin: register: {} clients registered", numClients);
    }

    public synchronized void unregister(ClientShellCommand c) {
        if (!started) return;
        //if (phase!=0) return;
        clients.remove(c);
        numClients--;
        log.info("ServerCoordinatorTimeWin: unregister: {} clients registered", numClients);
    }

    protected synchronized void startPhase1(List<ClientShellCommand> registeredIntime) {
        if (phase != 0) return;
        log.info("ServerCoordinatorTimeWin: Phase #1");
        phase = 1;

        // Pick a random client for Broker
        int howmany = registeredIntime.size();
        int sel = (int) Math.round((howmany - 1) * Math.random());
        if (sel >= howmany) sel = howmany - 1;
        broker = registeredIntime.get(sel);
        log.info("ServerCoordinatorTimeWin: Client {} will become BROKER", broker.getId());

        // Push broker IP address to all clients
        try {
            //java.net.InetSocketAddress brokerSocketAddress = (java.net.InetSocketAddress) broker.getSession().getIoSession().getRemoteAddress();
            //String brokerIpAddress = brokerSocketAddress.getAddress().getHostAddress();
            //int brokerPort = brokerSocketAddress.getPort();
            String brokerIpAddress = broker.getClientIpAddress();
            int brokerPort = broker.getClientPort();
            if (brokerIpAddress == null || brokerIpAddress.trim().isEmpty() || brokerPort <= 0)
                throw new Exception("ServerCoordinatorTimeWin: startPhase1(): Unable to get broker IP address or Port: " + broker);
            this.brokerCfgIpAddressCmd = String.format("SET-PARAM bin/broker.cfg-template BROKER_IP_ADDR %s bin/broker.cfg", brokerIpAddress);
            this.brokerCfgPortCmd = String.format("SET-PARAM bin/broker.cfg-template BROKER_PORT %d bin/broker.cfg", brokerPort);
        } catch (Exception ex) {
            this.brokerCfgIpAddressCmd = null;
            this.brokerCfgPortCmd = null;
            log.error("ServerCoordinatorTimeWin: startPhase1(): Error while getting broker IP address and port: {}", broker);
        }

        // Signal BROKER to prepare
        phase = 2;
        broker.sendToClient("ROLE BROKER");
    }

    public synchronized void clientReady(ClientShellCommand c) {
        if (getPhase()==2) _brokerReady(c);
        else _clientReady(c);
    }

    private void _brokerReady(ClientShellCommand c) {
        if (!started) return;
        if (phase != 2) return;
        log.info("ServerCoordinatorTimeWin: Broker is ready");
        phase = 3;
        readyClients = 1;
        if (readyClients == numClients) {
            phase = 4;
            signalTopologyReady();
        } else {
            Thread runner = new Thread(new Runnable() {
                public void run() {
                    // Signal all clients except broker to prepare
                    for (ClientShellCommand c : clients) {
                        if (c != broker) {
                            c.sendToClient(brokerCfgIpAddressCmd);
                            c.sendToClient(brokerCfgPortCmd);
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
        if (!started) return;
        if (phase != 3) return;
        readyClients++;
        log.info("ServerCoordinatorTimeWin: {} of {} clients are ready", readyClients, numClients);
        if (readyClients == numClients) {
            phase = 4;
            signalTopologyReady();
        }
    }

    protected void signalTopologyReady() {
        if (phase != 4) return;
        log.info("ServerCoordinatorTimeWin: Invoking callback");
        phase = 5;
        Thread runner = new Thread(new Runnable() {
            public void run() {
                // Invoke callback
                callback.run();
                log.info("ServerCoordinatorTimeWin: FINISHED");
            }
        });
        runner.setDaemon(true);
        runner.start();
    }
}
