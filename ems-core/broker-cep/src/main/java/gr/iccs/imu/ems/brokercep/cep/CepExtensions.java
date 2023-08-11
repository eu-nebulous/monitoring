/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.cep;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * This class registers a few Single-Row Functions as EPL extensions.
 * Registered function implementations reside in CepEvalFunction and CepEvalAggregator classes.
 * This class is instantiated automatically by Spring-Boot (no need for explicit instantiation)
 */
@Slf4j
@Service
public class CepExtensions {

    // Register Single-Row Functions methods

    @Autowired
    public CepExtensions(ApplicationContext appContext) {
        CepService cepService = appContext.getBean(CepService.class);
        cepService.addSingleRowFunction("EVAL", CepEvalFunction.class.getName(), "eval");
        cepService.addSingleRowFunction("MATH", CepEvalFunction.class.getName(), "evalMath");
        cepService.addSingleRowFunction("NEWEVENT", CepEvalFunction.class.getName(), "newEvent");
        cepService.addSingleRowFunction("PROP", CepEvalFunction.class.getName(), "prop");
        cepService.addAggregatorFunction("EVALAGG", CepEvalAggregatorFactory.class.getName());
    }
}
