/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client;

import gr.iccs.imu.ems.baguette.client.cluster.ClusterManagerProperties;
import gr.iccs.imu.ems.baguette.client.collector.netdata.NetdataCollector;
//import prometheus.collector.gr.iccs.imu.ems.baguette.client.PrometheusCollector;
import gr.iccs.imu.ems.baguette.client.plugin.recovery.SelfHealingPlugin;
import gr.iccs.imu.ems.util.EventBus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Baguette client
 */
@Slf4j
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "gr.iccs.imu.ems.baguette.client", "gr.iccs.imu.ems.brokercep", "gr.iccs.imu.ems.common",
        "gr.iccs.imu.ems.brokerclient", "gr.iccs.imu.ems.util"})
@RequiredArgsConstructor
public class BaguetteClient implements ApplicationRunner {
    @Getter
    private final BaguetteClientProperties baguetteClientProperties;
    private final ClusterManagerProperties clusterManagerProperties;
    private final ConfigurableApplicationContext applicationContext;

    private final List<Class<? extends Collector>> DEFAULT_COLLECTORS_LIST = List.of(
        NetdataCollector.class//, PrometheusCollector.class
    );

    @Getter
    private final List<Collector> collectorsList = new ArrayList<>();

    private static int killDelay;

    @Getter
    private Sshc client;

    public static void main(String[] args) {
        SpringApplication.run(BaguetteClient.class, args);

        forceExit();
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public EventBus<String,Object,Object> eventBus() {
        return EventBus.<String,Object,Object>builder().build();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        log.debug("BaguetteClient: Starting");

        // Process command line arguments
        processCommandLineArgs(args);
        killDelay = baguetteClientProperties.getKillDelay();
        log.debug("BaguetteClient: configuration: {}", baguetteClientProperties);
        log.debug("Cluster: configuration: {}", clusterManagerProperties);

        boolean interactiveMode = args.containsOption("i");

        // Start measurement collectors (but not in interactive mode)
        if (!interactiveMode) {
            startCollectors();
            applicationContext.getBean(SelfHealingPlugin.class).start();
        }

        if (interactiveMode) {
            // Run CLI
            log.debug("BaguetteClient: Enters interactive mode");
            runCli();
        } else {
            // Run SSH client
            log.debug("BaguetteClient: Enters SSH mode");
            runSshClient();
        }
        log.debug("BaguetteClient: Exiting");

        // Stop measurement collectors
        if (!interactiveMode) {
            applicationContext.getBean(SelfHealingPlugin.class).stop();
            stopCollectors();
        }

        // Stop Baguette Client services
        applicationContext.close();

        log.info("BaguetteClient: Bye");
    }

    private void processCommandLineArgs(ApplicationArguments args) {
        // Get cluster node addresses and properties
        List<String> addresses = args.getNonOptionArgs();
        if (addresses!=null && addresses.size()>0) {
            clusterManagerProperties.getLocalNode().setAddress(addresses.get(0));
            if (addresses.size()>1) {
                clusterManagerProperties.setMemberAddresses(addresses.subList(1, addresses.size()));
            }
        }

        // Enable/Disable TLS
        if (args.containsOption("tls"))
            clusterManagerProperties.getTls().setEnabled(true);
        if (args.containsOption("notls"))
            clusterManagerProperties.getTls().setEnabled(false);
    }

    protected void startCollectors() {
        if (!collectorsList.isEmpty())
            throw new IllegalArgumentException("Collectors have already been started");

        log.debug("BaguetteClient: Starting collectors...");
        if (baguetteClientProperties.getCollectorClasses()==null)
            baguetteClientProperties.setCollectorClasses(DEFAULT_COLLECTORS_LIST);
        for (Class<? extends Collector> collectorClass : baguetteClientProperties.getCollectorClasses()) {
            try {
                log.debug("BaguetteClient: Starting collector: {}...", collectorClass.getName());
                Collector collector = applicationContext.getBean(collectorClass);
                log.debug("BaguetteClient: Starting collector: {}: instance={}", collectorClass.getName(), collector);
                if (baguetteClientProperties.getCollectorConfigurations()!=null) {
                    Object config = baguetteClientProperties.getCollectorConfigurations().get(collector.getName());
                    log.debug("BaguetteClient: Starting collector: {}: collector-config={}", collectorClass.getName(), collector);
                    if (config!=null)
                        collector.setConfiguration(config);
                }
                collector.start();
                collectorsList.add(collector);
                log.debug("BaguetteClient: Starting collector: {}...ok", collectorClass.getName());
            } catch (NoSuchBeanDefinitionException e) {
                log.error("BaguetteClient: Exception while starting collector: {}: ", collectorClass.getName(), e);
            }
        }
        log.debug("BaguetteClient: Starting collectors...ok");
    }

    protected void stopCollectors() {
        log.debug("BaguetteClient: Stopping collectors...");
        for (Collector collector : collectorsList) {
            try {
                log.debug("BaguetteClient: Stopping collector: {}...", collector.getClass().getName());
                collector.stop();
                log.debug("BaguetteClient: Stopping collector: {}...ok", collector.getClass().getName());
            } catch (NoSuchBeanDefinitionException e) {
                log.error("BaguetteClient: Exception while stopping collector: {}: ", collector.getClass().getName(), e);
            }
        }
        collectorsList.clear();
    }

    protected void runSshClient() {
        long retryDelay = baguetteClientProperties.getConnectionRetryDelay();
        boolean retry = baguetteClientProperties.isConnectionRetryEnabled() && retryDelay>=0;
        int retryLimit = baguetteClientProperties.getConnectionRetryLimit();
        int retryCount = 0;
        while (true) {
            try {
                // Connect to baguette server
                startSshClient(retry);

                // Exchange messages with Baguette server
                log.trace("BaguetteClient: Calling SSHC run()");
                client.run();
                retryCount = 0;

                // Disconnect from baguette server
                stopSshClient();
            } catch (Exception ex) {
                log.error("BaguetteClient: EXCEPTION: ", ex);
            }

            // Check if retry is enabled
            if (!retry) break;

            // Check if retry limit has been reached
            retryCount++;
            if (retryLimit>=0 && retryCount > retryLimit) {
                log.error("BaguetteClient: Giving up connection retries after {} failed attempts", retryCount-1);
                break;
            }

            // Wait for a while before retrying to reconnect
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                log.warn("BaguetteClient: Cancelling connection retry");
                break;
            }
            log.info("BaguetteClient: Retrying to connect (attempt #{})...", retryCount);
        }
    }

    protected void runCli() throws IOException {
        BaguetteClientCLI cli = applicationContext.getBean(BaguetteClientCLI.class);
        cli.setConfiguration(baguetteClientProperties);
        cli.run();
    }

    public synchronized void startSshClient(boolean retry) throws IOException {
        log.trace("BaguetteClient: spring-boot application-context: {}", applicationContext);
        client = applicationContext.getBean(Sshc.class);
        client.setConfiguration(baguetteClientProperties);

        log.trace("BaguetteClient: Sshc instance from application-context: {}", client);
        log.trace("BaguetteClient: Calling SSHC start()");
        client.start(retry);
        client.greeting();
    }

    public synchronized void stopSshClient() throws IOException {
        log.trace("BaguetteClient: Calling SSHC stop()");
        Sshc tmp = client;
        client = null;
        tmp.stop();
    }

    /*protected static Properties loadConfig(String configFile) throws IOException {
        Properties config = new Properties();
        try {
            try (InputStream in = new FileInputStream(new File(configFile))) {
                config.load(in);
            }
        } catch (FileNotFoundException ex) {
            try (InputStream in = BaguetteClient.class.getResourceAsStream(configFile)) {
                if (in == null) throw ex;
                config.load(in);
            }
        }
        return config;
    }*/

    protected static void forceExit() {
        // Print remaining threads
        Thread.getAllStackTraces().keySet()
                .forEach(s -> log.debug("---> {}.{}: {} alive={}, daemon={}, interrupted={}",
                        s.getThreadGroup().getName(), s.getName(), s.getState(),
                        s.isAlive(), s.isDaemon(), s.isInterrupted()));

        // Start killer thread
        if (killDelay>0) {
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
                log.warn("Waiting JVM to exit for {} more seconds", killDelay);
                try { Thread.sleep(killDelay * 1000); } catch (InterruptedException ignored) { }
                log.warn("Forcing JVM to exit");
                System.exit(0);
            }) {{
                setDaemon(true);
                start();
            }};
        } else {
            log.debug("Killer thread disabled");
        }
    }
}
