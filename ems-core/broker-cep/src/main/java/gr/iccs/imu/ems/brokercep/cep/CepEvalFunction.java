/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.cep;

import com.espertech.esper.collection.Pair;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class CepEvalFunction {
    public static double eval(String formula, String streamNames, Map e) {
        return _eval(formula, streamNames, e);
    }

    public static double eval(String formula, String streamNames, Map e1, Map e2) {
        return _eval(formula, streamNames, e1, e2);
    }

    public static double eval(String formula, String streamNames, Map e1, Map e2, Map e3) {
        return _eval(formula, streamNames, e1, e2, e3);
    }

    public static double eval(String formula, String streamNames, Map e1, Map e2, Map e3, Map e4) {
        return _eval(formula, streamNames, e1, e2, e3, e4);
    }

    protected static double _eval(String formula, String streamNames, Map... maps) {
        log.debug(">> ---------------------------------------------------------------------------");
        log.debug(">> eval(map):   formula: {}", formula);
        log.debug(">> eval(map):   streams: {}", streamNames);
        log.debug(">> eval(map):   entries: {}", maps.length);
        log.debug(">> eval(map):   maps:    {}", java.util.Arrays.asList(maps));

        String[] names = streamNames.split(",");
        if (names.length != maps.length)
            throw new IllegalArgumentException("The num. of stream names provided is not equal to the num. of values provided");
        Map<String, Double> args = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            String entryName = names[i].trim();
            Object entryValue = maps[i].get("metricValue");
            log.debug(">> eval(map):   maps-entry: {} = {} / {}", entryName, entryValue, entryValue.getClass().getName());
            if (entryValue instanceof String) entryValue = Double.parseDouble((String)entryValue);
            args.put(entryName, (Double) entryValue);
        }
        log.debug(">> eval(map):   map-args: {}", args);

        double result = MathUtil.eval(formula, args);
        log.debug(">> eval(map):   result:  {}", result);

        return result;
    }

    public static double eval(String formula, String streamNames, Pair pair1) {
        return _eval(formula, streamNames, pair1);
    }

    public static double eval(String formula, String streamNames, Pair pair1, Pair pair2) {
        return _eval(formula, streamNames, pair1, pair2);
    }

    public static double eval(String formula, String streamNames, Pair pair1, Pair pair2, Pair pair3) {
        return _eval(formula, streamNames, pair1, pair2, pair3);
    }

    public static double eval(String formula, String streamNames, Pair pair1, Pair pair2, Pair pair3, Pair pair4) {
        return _eval(formula, streamNames, pair1, pair2, pair3, pair4);
    }

    public static double _eval(String formula, String streamNames, Pair... pairs) {
        log.debug(">> ---------------------------------------------------------------------------");
        log.debug(">> eval(Pair):   formula: {}", formula);
        log.debug(">> eval(Pair):   streams: {}", streamNames);
        log.debug(">> eval(Pair):   entries: {}", pairs.length);
        log.debug(">> eval(Pair):   values:  {}", Arrays.asList(pairs));

        String[] names = streamNames.split(",");
        if (names.length != pairs.length)
            throw new IllegalArgumentException("The num. of stream names provided is not equal to the num. of value pairs provided");
        Map<String, Double> args = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (log.isTraceEnabled())
                log.trace(">> eval(Pair):  LOOP:  i={}, name={}, pair-1={}/{}, pair-2={}/{}",
                        i, names[i],
                        pairs[i].getFirst(), pairs[i].getFirst()==null ? null : pairs[i].getFirst().getClass().getName(),
                        pairs[i].getSecond(), pairs[i].getSecond()==null ? null : pairs[i].getSecond().getClass().getName());
            Object eventObj = pairs[i].getFirst();
            double value;
            if (eventObj instanceof EventMap)
                value = ((EventMap)eventObj).getMetricValue();
            else if (eventObj instanceof Map)
                value = (double) (StrUtil.castToMapStringObject(eventObj)).get("metricValue");
            else if (eventObj instanceof Double)
                value = (double) eventObj;
            else
                throw new IllegalArgumentException("Encountered unsupported Event type in Pair: "+eventObj.getClass().getName()+", event: "+eventObj);
            args.put(names[i].trim(), value);
        }
        log.debug(">> eval(Pair):   map-args: {}", args);

        double result = MathUtil.eval(formula, args);
        log.debug(">> eval(Pair):   result:  {}", result);

        return result;
    }

    public static double eval(String formula, String streamNames, double... v) {
        log.debug(">> ---------------------------------------------------------------------------");
        log.debug(">> eval(double):   formula: {}", formula);
        log.debug(">> eval(double):   streams: {}", streamNames);
        log.debug(">> eval(double):   entries: {}", v.length);
        log.debug(">> eval(double):   values:  {}", v);

        String[] names = streamNames.split(",");
        if (names.length != v.length)
            throw new IllegalArgumentException("The num. of stream names provided is not equal to the num. of values provided");
        Map<String, Double> args = new HashMap<>();
        for (int i = 0; i < names.length; i++) args.put(names[i].trim(), v[i]);
        log.debug(">> eval(double):   map-args: {}", args);

        double result = MathUtil.eval(formula, args);
        log.debug(">> eval(double):   result:  {}", result);

        return result;
    }

    public static double evalMath(String formula, double...values) {
        log.debug(">> ---------------------------------------------------------------------------");
        log.debug(">> evalMath:   formula: {}", formula);
        log.debug(">> evalMath:    values: {}", values);

        // Get formula arguments
        Set<String> argNames = MathUtil.getFormulaArguments(formula);
        log.debug(">> evalMath: arg-names: {}", argNames);

        // Check the number of arguments and the number of provided values match
        if (argNames.size() != values.length)
            throw new IllegalArgumentException(String.format(
                    "evalMath: The number of provided values do not match the number of formula arguments: #args=%d != #values=%d",
                    argNames.size(), values.length));

        // Map values onto arguments, using the order of appearance (i.e. 1st value->1st arg, 2nd value->2nd arg...)
        final AtomicInteger i = new AtomicInteger(0);
        Map<String, Double> map = argNames.stream().collect(Collectors.toMap(
                arg -> arg, arg -> values[ i.getAndIncrement() ]
        ));
        log.debug(">> evalMath:  args-map: {}", map);

        double result = evalMath(formula, map);
        log.debug(">> evalMath:   result:  {}", result);
        return result;
    }

    public static double evalMath(String formula, Map<String,Double> args) {
        log.debug(">> ---------------------------------------------------------------------------");
        log.debug(">> evalMath:   formula: {}", formula);
        log.debug(">> evalMath:  args-map: {}", args);

        double result = MathUtil.eval(formula, args);
        log.debug(">> evalMath:   result:  {}", result);
        return result;
    }

    public static EventMap newEvent(double metricValue, String... params) {
        return newEvent(metricValue, 1, params);
    }

    public static EventMap newEvent(double metricValue, int level, String... params) {
        log.debug(">> ---------------------------------------------------------------------------");
        log.debug(">> newEvent:   metric-value:  {}", metricValue);
        log.debug(">> newEvent:   params-length: {}", params.length);

        // Add metric value
        EventMap event = new EventMap(metricValue, level, System.currentTimeMillis());

        // Add extra parameters
        for (int i = 0; i < params.length; i += 2) {
            String paramName = params[i];
            String paramValue = params[i + 1];
            event.put(paramName, paramValue);
        }
        log.debug(">> newEvent:   new-event: {}", event);

        return event;
    }
	
/*	public static double eval(String formula, EPLMethodInvocationContext context) {
		log.debug(">>>>>>>>>>>>>>>>>>   formula: {}", formula);
		log.debug(">>>>>>>>>>>>>>>>>>   statement-name: {}", context.getStatementName());
		String stmtName = context.getStatementName();
		EPStatement stmt = CepExtensions.cepService.getStatementByName(stmtName);
		String stmtText = stmt.getText();
		log.debug(">>>>>>>>>>>>>>>>>>   statement-text: {}", stmtText);
		
		log.debug(">>>>>>>>>>>>>>>>>>   statement-streams: {}", CepExtensions.cepService.getStatementStreams(stmtText) );
		
		double value = -100*Math.random();
		log.debug(">>>>>>>>>>>>>>>>>>   EVAL RESULT: {}", value);
		return value;
	}*/

    public static Object prop(Object eventObj, String propertyName) {
        return prop(eventObj, propertyName, null);
    }

    public static Object prop(Object eventObj, String propertyName, Object defaultValue) {
        EventMap event = eventObj instanceof EventMap ? ((EventMap) eventObj) : null;
        log.debug(">> ---------------------------------------------------------------------------");
        log.debug(">> prop:   event-object:  {}", eventObj);
        log.debug(">> prop:    event-class:  {}", eventObj!=null ? eventObj.getClass() : null);
        log.debug(">> prop:      event-map:  {}", event);
        log.debug(">> prop:       property:  {}", propertyName);

        // Retrieve event property
        Object ret = null;
        if (event!=null) {
            Map<String, Object> props = event.getEventProperties();
            if (props != null) {
                log.debug(">> prop:     properties: {}", props);
                ret = props.getOrDefault(propertyName, defaultValue);
                defaultValue = null;
            }
        }
        if (ret==null) ret = defaultValue;
        log.debug(">> prop:          value: {}", ret);
        return ret;
    }
}
