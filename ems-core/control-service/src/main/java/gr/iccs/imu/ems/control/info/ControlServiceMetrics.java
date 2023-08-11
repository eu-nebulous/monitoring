/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ControlServiceMetrics implements ApplicationContextAware {

    private final MeterRegistry meterRegistry;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Counter howmany = Counter
                .builder("ems-howmany")
                .description("EMS test counter metric")
                .tags("ems", "test")
                .register(meterRegistry);
        howmany.increment(10);
        Gauge freemem = Gauge
                .builder("ems-freemem", () -> Runtime.getRuntime().freeMemory())
                .description("EMS test gauge metric")
                .tags("ems", "test")
                .register(meterRegistry);
    }
}
