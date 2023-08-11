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
import com.espertech.esper.epl.agg.aggregator.AggregationMethod;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class CepEvalAggregator implements AggregationMethod {
    private final LinkedHashMap<Integer,Object[]> entries = new LinkedHashMap<>();

    public void clear() {
        log.debug("CepEvalAggregator.clear(): aggregator-hash={}", hashCode());
        entries.clear();
    }

    public void enter(Object value) {
        log.debug("CepEvalAggregator.enter(): aggregator-hash={}, input={}, hash={}", hashCode(), value, value.hashCode());
        traceValue("ENTER-BEFORE", value, null, true);
        if (value instanceof Object[])
            entries.put(Arrays.hashCode((Object[]) value), (Object[]) value);      // 0:formula, 1:stream-names, 2+:EventMap
        else
            log.error("CepEvalAggregator.enter(): ERROR: WRONG ARG TYPE: Expected Object[]: aggregator-hash={}, input={}, input-type={}", hashCode(), value, value.getClass().getName());
        traceValue("ENTER-AFTER", value, null, true);
    }

    public void leave(Object value) {
        log.debug("CepEvalAggregator.leave(): aggregator-hash={}, input={}, hash={}", hashCode(), value, value.hashCode());
        traceValue("LEAVE-BEFORE", value, null, true);

//        int p = findEntry(value);
//        Object[] removedObject = p!=-1 ? entries.remove(p) : null;
        int valueHash = Arrays.hashCode((Object[]) value);
        Object[] removedObject = entries.remove(valueHash);
        log.debug("CepEvalAggregator.leave(): aggregator-hash={}, input={}, hash={}, p={}, removed={}", hashCode(), value, value.hashCode(),
                /*p*/null, removedObject==null ? null : Arrays.asList(removedObject));

        traceValue("LEAVE-AFTER", removedObject, value, true);
    }

    /*private int findEntry(Object value) {
        log.trace("CepEvalAggregator.findEntry: BEGIN:  to-remove={}", value);
        if (value==null) {
            log.trace("CepEvalAggregator.findEntry: END: ILLEGAL ARG: NULL ARG: to-remove={}", value);
            return -1;
        }
        if (log.isTraceEnabled())
            log.trace("CepEvalAggregator.findEntry: to-remove-class: {}", value.getClass().getName());
        if (! (value instanceof Object[])) {
            log.trace("CepEvalAggregator.findEntry: END: ILLEGAL ARG: Not an Object[]: class={}, to-remove={}", value.getClass().getName(), value);
            return -2;
        }
        Object[] valArr = (Object[]) value;
        int valArrLen = valArr.length;
        if (log.isTraceEnabled())
            log.trace("CepEvalAggregator.findEntry: to-remove: size={}, str={}", Arrays.toString(valArr), valArrLen);

        log.trace("CepEvalAggregator.findEntry: num-of-entries: {}", entries.size());
        int pos = -1;
        for (Object[] oArr : entries.values()) {
            pos++;
            log.trace("CepEvalAggregator.findEntry: entry-item: pos={}, item={}, to-remove={}", pos, oArr, value);
            if (oArr==value) {
                log.trace("CepEvalAggregator.findEntry: END: FOUND-SAME-OBJECT: pos={}, to-remove={}", pos, value);
                return pos;
            }
            log.trace("CepEvalAggregator.findEntry: entry-item: pos={}, item-arr-len={}, to-remove-arr-len={}", pos, oArr.length, valArrLen);
            if (oArr.length!=valArrLen)
                continue;
//            int x = _findEntry_extraChecks(valArr, oArr);
//            log.trace("CepEvalAggregator.findEntry: entry-item: pos={}, extra-checks-result={}", pos, x);
//            if (x != 0)
//                continue;
            if (log.isTraceEnabled())
                log.trace("CepEvalAggregator.findEntry: entry-item: pos={}, item-arr-hash={}, to-remove-arr-hash={}", pos, Arrays.hashCode(oArr), Arrays.hashCode(valArr));
            if (Arrays.hashCode(oArr) != Arrays.hashCode(valArr))
                continue;
            log.trace("CepEvalAggregator.findEntry: END: FOUND-SAME-ARRAY-HASH: pos={}, to-remove={}", pos, value);
            return pos;
        }

        log.trace("CepEvalAggregator.findEntry: END: NOT FOUND: {}", value);
        return -10;
    }

    private static Integer _findEntry_extraChecks(Object[] valArr, Object[] oArr) {
        for (int i = 0; i< oArr.length; i++) {
            if (i>=2) {
                if (oArr[i] instanceof EventMap && valArr[i] instanceof EventMap) {
                    if (((EventMap) oArr[i]).getEventId() != ((EventMap) valArr[i]).getEventId())
                        return -4;
                } else
                if (oArr[i] instanceof Pair && valArr[i] instanceof Pair) {
                    log.trace("CepEvalAggregator._findEntry_extraChecks:    oArr[{}]: {} / {}", i, oArr[i].getClass().getName(), oArr[i]);
                    log.trace("CepEvalAggregator._findEntry_extraChecks:  valArr[{}]: {} / {}", i, valArr[i].getClass().getName(), valArr[i]);
                    Object e1 = ((Pair<?, ?>) oArr[i]).getFirst();
                    Object e2 = ((Pair<?, ?>) valArr[i]).getFirst();
                    log.trace("CepEvalAggregator._findEntry_extraChecks:    e1: {} / {}", e1.getClass().getName(), e1);
                    log.trace("CepEvalAggregator._findEntry_extraChecks:    e2: {} / {}", e2.getClass().getName(), e2);
                    if (e1 instanceof EventMap && e2 instanceof EventMap) {
                        log.trace("CepEvalAggregator._findEntry_extraChecks:    e1 + e2  ARE EventMaps");
                        log.trace("CepEvalAggregator._findEntry_extraChecks:    e1-id: {}", ((EventMap) e1).getEventId());
                        log.trace("CepEvalAggregator._findEntry_extraChecks:    e2-id: {}", ((EventMap) e2).getEventId());
                        if (((EventMap) e1).getEventId() != ((EventMap) e2).getEventId())
                            return -5;
                        log.trace("CepEvalAggregator._findEntry_extraChecks:    e1-id AND e2-id  MATCH!!!");
                    } else {
                        log.trace("CepEvalAggregator._findEntry_extraChecks:    e1 + e2  ARE *NOT* EventMaps");
                        return -6;
                    }
                } else
                {
                    return -7;
                }
            }
            if (oArr[i].hashCode()!= valArr[i].hashCode())
                return -8;
        }
        return 0;
    }*/

    public Object getValue() {
        log.debug("CepEvalAggregator.getValue(): BEGIN");

        // Get an unmodifiable local copy of entries
        Map<Integer, Object[]> _entries;
        synchronized (entries) {
//            _entries = Collections.unmodifiableList(entries);
            _entries = Collections.unmodifiableMap(entries);
        }

        if (_entries.size() == 0) {
            log.debug("CepEvalAggregator.getValue(): END_0: aggregator-hash={}, result=0", hashCode());
            return 0;
        }

        // get formula and stream names (they must be identical for all entries)
//        Object[] first = _entries.get(0);
        Object[] first = _entries.values().iterator().next();
        String formula = (String) first[0];
        String[] streamNames = ((String) first[1]).split(",");

        // initialize event lists for each stream
        List<List<EventMap>> lists = new ArrayList<>();
        for (int i = 0; i < streamNames.length; i++) {
            lists.add(new ArrayList<>());
        }

        // append events from entries into stream event lists
        for (Object[] entry : _entries.values()) {
            if (!entry[0].equals(formula) && !entry[1].equals(streamNames))
                throw new IllegalArgumentException("Aggregator entries do not contain the same formula or stream names in arguments #0 or #1");
            for (int i = 0; i < streamNames.length; i++) {
                Object currentEntry = entry[i + 2];

                // If entry is a Pair then extract first value (must be an EventMap or Map)
                if (currentEntry instanceof Pair) {
                    Pair pair = (Pair)currentEntry;
                    Object firstInPair = ((Pair)currentEntry).getFirst();
                    log.trace("CepEvalAggregator.getValue():  First: {} -- {}", pair.getFirst().getClass().getName(), pair.getFirst());
                    log.trace("CepEvalAggregator.getValue(): Second: {} -- {}", pair.getSecond().getClass().getName(), pair.getSecond());
                    if (firstInPair instanceof HashMap)
                        currentEntry = firstInPair;
                }

                // Process entry
                if (EventMap.class.isAssignableFrom(currentEntry.getClass())) {
                    lists.get(i).add((EventMap) currentEntry);
                } else if (HashMap.class.isAssignableFrom(currentEntry.getClass())) {
                    EventMap eventMap = new EventMap(StrUtil.castToMapStringObject(currentEntry));
                    lists.get(i).add(eventMap);
                } else {
                    log.error("CepEvalAggregator.getValue(): ERROR: Event type is not supported: {}, Event:\n{}",
                            currentEntry.getClass().getName(), currentEntry);
                    throw new RuntimeException("Event type is not supported: " + currentEntry.getClass().getName());
                }
            }
        }

        // extract values from events
        log.debug("CepEvalAggregator.getValue(): formula: {}", formula);
        log.debug("CepEvalAggregator.getValue(): streams: {}", java.util.Arrays.asList(streamNames));
        log.debug("CepEvalAggregator.getValue(): stream-event-lists: {}", lists.size());
        List<List<Double>> dataLists = new ArrayList<>();
        for (int i = 0, n = lists.size(); i < n; i++) {
            log.trace("CepEvalAggregator.getValue(): event-list-{}: {}", i, lists.get(i));
            //List<Double> data = lists.get(i).stream().map(event -> (Double) event.get("metricValue")).collect(Collectors.toList());
            List<Double> data = lists.get(i).stream().map(EventMap::getMetricValue).collect(Collectors.toList());
            log.trace("CepEvalAggregator.getValue(): data-list-{}: {}", i, data);
            dataLists.add(data);
        }

        // prepare arguments of MathParser
        Map<String, List<Double>> args = new HashMap<>();
        for (int i = 0; i < streamNames.length; i++) {
            args.put(streamNames[i].trim(), dataLists.get(i));
        }
        log.debug("CepEvalAggregator.getValue(): stream-data-lists: {}", args);

        // use MathParser to evaluate formula using stream data lists
        double result = MathUtil.evalAgg(formula, args);
        log.debug("CepEvalAggregator.getValue(): END: aggregator-hash={}, result={}", hashCode(), result);
        return result;
    }


    private void traceValue(String logPrefix, Object value, Object match, boolean listEntries) {
        log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:  BEGIN: {}", logPrefix, value);
        if (value==null) return;
        log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:  CLASS: {}", logPrefix, value.getClass().getName());
        log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:   HASH: {}", logPrefix, value.hashCode());
        if (! (value instanceof Object[])) return;
        Object[] oArr = (Object[])value;
        log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:   SIZE: {}", logPrefix, oArr.length);
        log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}: A-HASH: {}", logPrefix, Arrays.hashCode(oArr));
        int i=0;
        for (Object oVal : oArr) {
            log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:   ,,,,,,,,,,,,,,,,,,,,,,,,,,,", logPrefix);
            log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:   ITEM: {}", logPrefix, i++);
            log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:         VAL: {}", logPrefix, oVal);
            if (oVal==null) continue;
            log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:       CLASS: {}", logPrefix, oVal.getClass().getName());
            log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:        HASH: {}", logPrefix, oVal.hashCode());
            EventMap event = null;
            if (oVal instanceof Pair) {
                Pair p = (Pair)oVal;
                log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:         1-ST: {}", logPrefix, p.getFirst());
                log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:         2-ND: {}", logPrefix, p.getSecond());
                traceValue(logPrefix+"-PAIR-1ST", p.getFirst(), null, false);
                traceValue(logPrefix+"-PAIR-2ND", p.getSecond(), null, false);
                if (p.getFirst() instanceof EventMap) event = (EventMap) p.getFirst();
            }
            else if (oVal instanceof EventMap) event = (EventMap) oVal;
            if (event!=null)
                log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:        E-ID: {}", logPrefix, event.getEventId());
            else
                log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:        NO EVENT: {}", logPrefix, oVal.getClass().getName());

            if (match==null) continue;
            log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:     MATCH-1: {}", logPrefix, oVal==match);
            log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:     MATCH-2: {} =? {}", logPrefix, oVal.hashCode(), match.hashCode());
        }
        log.trace("CepEvalAggregator.logValue: LOG-VALUE: {}:   ,,,,,,,,,,,,,,,,,,,,,,,,,,,", logPrefix);

        if (listEntries) {
            log.trace("CepEvalAggregator.logValue: LIST-ENTRIES:  ----> ENTRIES: {}", entries.size());
            int j = 0;
            for (Object arr : entries.values()) {
                log.trace("CepEvalAggregator.logValue: LIST-ENTRIES:  ----> ENTRY-{}: ------------------------------", j);
                traceValue("LOG-VALUE-"+j, arr, null, false);
            }
            log.trace("CepEvalAggregator.logValue: LIST-ENTRIES:  ----> ENTRY-END: ------------------------------");
        }
    }
}
