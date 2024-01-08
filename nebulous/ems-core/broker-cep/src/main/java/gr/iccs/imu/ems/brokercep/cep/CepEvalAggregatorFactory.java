/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.cep;

import com.espertech.esper.client.hook.AggregationFunctionFactory;
import com.espertech.esper.collection.Pair;
import com.espertech.esper.epl.agg.aggregator.AggregationMethod;
import com.espertech.esper.epl.agg.service.common.AggregationValidationContext;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Map;

@Slf4j
public class CepEvalAggregatorFactory implements AggregationFunctionFactory {

    private String aggregatorFunctionName;

    public Class getValueType() {
        return Double.class;
    }

    public AggregationMethod newAggregator() {
        return new CepEvalAggregator();
    }

    public void setFunctionName(String functionName) {
        aggregatorFunctionName = functionName;
    }

    public void validate(AggregationValidationContext validationContext) {
		log.debug("CepEvalAggregatorFactory.validate(): BEGIN: validationContext: {}", validationContext);
		Class[] paramType = validationContext.getParameterTypes();
        log.debug("CepEvalAggregatorFactory.validate(): param-types: {}", Arrays.asList(paramType));
		if (!paramType[0].equals(String.class)) {
            log.error("CepEvalAggregatorFactory.validate(): Invalid argument #0 type in aggregator '" + aggregatorFunctionName + "'. Expected 'String' but found: " + paramType[0].getName());
            throw new IllegalArgumentException("CepEvalAggregatorFactory.validate(): Invalid argument #0 type in aggregator '"+aggregatorFunctionName+"'. Expected 'String' but found: "+paramType[0].getName());
        }
		if (!paramType[1].equals(String.class)) {
            log.error("CepEvalAggregatorFactory.validate(): Invalid argument #1 type in aggregator '" + aggregatorFunctionName + "'. Expected 'String' but found: " + paramType[1].getName());
            throw new IllegalArgumentException("CepEvalAggregatorFactory.validate(): Invalid argument #1 type in aggregator '"+aggregatorFunctionName+"'. Expected 'String' but found: "+paramType[1].getName());
        }
		for (int i=2; i<paramType.length; i++) {
			if (!paramType[i].equals(EventMap.class) && !paramType[i].equals(Map.class) && !paramType[i].equals(Pair.class)) {
                log.error("CepEvalAggregatorFactory.validate(): Invalid argument #" + i + " type in aggregator '" + aggregatorFunctionName + "'. Expected 'EventMap' or 'Map' but found: " + paramType[i].getName());
                throw new IllegalArgumentException("CepEvalAggregatorFactory.validate(): Invalid argument #"+i+" type in aggregator '"+aggregatorFunctionName+"'. Expected 'EventMap' or 'Map' but found: "+paramType[i].getName());
            }
		}
        log.debug("CepEvalAggregatorFactory.validate(): END: OK");
    }
}
