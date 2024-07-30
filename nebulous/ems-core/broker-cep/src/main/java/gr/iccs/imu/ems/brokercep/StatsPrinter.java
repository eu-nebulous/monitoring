/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gr.iccs.imu.ems.brokercep.cep.CepService;
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsPrinter implements InitializingBean, Runnable {
    private final BrokerCepProperties properties;
    private final TaskScheduler taskScheduler;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void afterPropertiesSet() throws Exception {
        if (properties.isStatsPrinterEnabled()) {
            taskScheduler.scheduleAtFixedRate(this,
                    Instant.now().plusSeconds(properties.getStatsPrinterInitDelay()),
                    Duration.ofSeconds(properties.getStatsPrinterRate()));
            log.info("BCEP Statistics Printer enabled");
        } else {
            log.info("BCEP Statistics Printer disabled");
        }
    }

    @Override
    public void run() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("timestamp", Instant.now().toEpochMilli());
        stats.put("OUT.publish.success", BrokerCepStatementSubscriber.getLocalPublishSuccessCounter());
        stats.put("OUT.publish.failure", BrokerCepStatementSubscriber.getLocalPublishFailureCounter());
        stats.put("OUT.forward.success", BrokerCepStatementSubscriber.getForwardSuccessCounter());
        stats.put("OUT.forward.failure", BrokerCepStatementSubscriber.getForwardFailureCounter());
        stats.put("IN.receive.success", BrokerCepConsumer.getEventCounter());
        stats.put("IN.receive.failure", BrokerCepConsumer.getEventFailuresCounter());
        stats.put("CEP.incoming.events", CepService.getEventCounter());

        if (properties.isStatsPrinterAsJson())
            log.info("BCEP statistics:\n{}", gson.toJson(stats));
        if (properties.isStatsPrinterAsCsv())
            log.info("BCEP statistics:\n{}\n{}",
                    String.join(",", stats.keySet()),
                    stats.values().stream().map(l->Long.toString(l)).collect(Collectors.joining(",")));
    }
}
