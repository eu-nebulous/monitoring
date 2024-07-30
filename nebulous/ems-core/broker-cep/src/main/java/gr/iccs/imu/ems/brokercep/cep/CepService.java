/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.cep;

import com.espertech.esper.client.*;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.util.FunctionDefinition;
import gr.iccs.imu.ems.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CepService implements InitializingBean {
    private final static AtomicLong eventCounter = new AtomicLong(0);

    /**
     * Esper service
     */
    private EPServiceProvider epService;

    @Override
    public void afterPropertiesSet() {
        log.debug("CepService: Configuring CEP Service...");
        initService();
    }

    /**
     * Start Esper Service
     */
    public void initService() {
        log.debug("CepService: Initializing CEP Service...");
        Configuration config = new Configuration();
        epService = EPServiceProviderManager.getDefaultProvider(config);
    }

    /**
     * Dynamic registration of new Event Type using property name and property type arrays
     */
    public synchronized void addEventType(String eventTypeName, String[] properties, Class[] propertyTypes) {
        log.debug("CepService: Register new Event Type: name={}, properties={}, property-types={}", eventTypeName, properties, propertyTypes);
        Map<String, Object> eventTypeDef = new HashMap<String, Object>();
        for (int i = 0; i < properties.length; i++) {
            eventTypeDef.put(properties[i], propertyTypes[i]);
        }
        epService.getEPAdministrator().getConfiguration().addEventType(eventTypeName, eventTypeDef);
    }

    /**
     * Dynamic registration of new Event Type using event type class
     */
    public synchronized void addEventType(String eventTypeName, Class eventType) {
        log.debug("CepService: Register new Event Type: name={}, event-type={}", eventTypeName, eventType);
        epService.getEPAdministrator().getConfiguration().addEventType(eventTypeName, eventType);
    }

    /**
     * Clear all registered Event Types
     */
    public synchronized void clearEventTypes() {
        log.info("CepService: Clear registered Event Types");
        ConfigurationOperations co = epService.getEPAdministrator().getConfiguration();
        EventType[] types = co.getEventTypes();
        for (EventType t : types) {
            boolean removed = co.removeEventType(t.getName(), true);
            log.info("CepService: Event Type: {} --> removed={}", t.getName(), removed);
        }
    }

    /**
     * Dynamic registration of new EPL statements and corresponding subscribers
     */
    public synchronized void addStatementSubscriber(StatementSubscriber subscriber) {
        log.debug("CepService: Register EPL statement and subscriber: {}", subscriber.getName());
        String statementStr = subscriber.getStatement();
        log.debug("CepService: EPL statement: {}", statementStr);
        EPStatement eventStatement = epService.getEPAdministrator().createEPL(statementStr, subscriber.getName());
        eventStatement.setSubscriber(subscriber);
    }

    /**
     * Dynamic de-registration of existing EPL statements and corresponding subscribers
     */
    public synchronized void removeStatementSubscriber(StatementSubscriber subscriber) {
        EPStatement stmt = epService.getEPAdministrator().getStatement(subscriber.getName());
        stmt.stop();
        stmt.destroy();
    }

    /**
     * Clear all registered Statements
     */
    public synchronized void clearStatements() {
        log.info("CepService: Clear registered Statements");
        epService.getEPAdministrator().destroyAllStatements();
    }

    /**
     * Get statement by name
     */
    public EPStatement getStatementByName(String stmtName) {
        log.debug("CepService.getStatementByName(): statement-name={}", stmtName);
        return epService.getEPAdministrator().getStatement(stmtName);
    }

    /**
     * Handle the incoming event as Map
     */
    public void handleEvent(Map<String, Object> event, String eventType) {
        log.debug("CepService.handleEvent(): type={}, event={}", eventType, event.toString());
        EventMap.checkEvent(event);
        epService.getEPRuntime().sendEvent(event, eventType);
        eventCounter.incrementAndGet();
    }

    /**
     * Handle the incoming event as String
     */
    public void handleEvent(String event, String eventType) {
        log.debug("CepService.handleEvent(): type={}, event={}", eventType, event);
        EventMap eventMap = EventMap.parseEventMap(event);
        log.trace("CepService.handleEvent(): event-map={}", eventMap);
        epService.getEPRuntime().sendEvent(eventMap, eventType);
        eventCounter.incrementAndGet();
    }

    /**
     * Handle the incoming event as Object
     */
    public void handleEvent(Object event) {
        log.debug("CepService.handleEvent(): event={}", event);
        EventMap.checkEvent(StrUtil.castToMapStringObject(event));
        epService.getEPRuntime().sendEvent(event);
        eventCounter.incrementAndGet();
    }

    /**
     * Add a user-defined aggregator function in Esper
     */
    public void addAggregatorFunction(String functionName, String aggregationFactoryClassName) {
        log.debug("CepService.addAggregatorFunction(): function={}, aggregator-factory-class={}", functionName, aggregationFactoryClassName);
        epService.getEPAdministrator().getConfiguration().addPlugInAggregationFunctionFactory(functionName, aggregationFactoryClassName);
    }

    /**
     * Add a user-defined single-row function in Esper
     */
    public void addSingleRowFunction(String functionName, String className, String methodName) {
        log.debug("CepService.addSingleRowFunction(): function={}, class={}, method={}", functionName, className, methodName);
		/*epService.getEPAdministrator().getConfiguration().addPlugInSingleRowFunction(functionName, className, methodName,
			com.espertech.esper.client.ConfigurationPlugInSingleRowFunction.ValueCache.CONFIGURED,		//enum: ENABLED, DISABLED, CONFIGURED
			com.espertech.esper.client.ConfigurationPlugInSingleRowFunction.FilterOptimizable.ENABLED,	//enum: ENABLED, DISABLED
			true		// re-throw exceptions
		);*/
        com.espertech.esper.client.ConfigurationPlugInSingleRowFunction entry = new com.espertech.esper.client.ConfigurationPlugInSingleRowFunction();
        entry.setName(functionName);
        entry.setFunctionClassName(className);
        entry.setFunctionMethodName(methodName);
        entry.setRethrowExceptions(true);
        epService.getEPAdministrator().getConfiguration().addPlugInSingleRowFunction(entry);
    }

    /**
     * Get statement streams (i.e. FROM clause stream names. Non-named streams (those without AS) return 'null')
     */
    public List<String> getStatementStreams(String statementText) {
        log.debug("CepService.getStatementStreams(): statement={}", statementText);
        return epService.getEPAdministrator().compileEPL(statementText).getFromClause().getStreams().stream().map(stream -> stream.getStreamName()).collect(Collectors.toList());
    }

    /**
     * Add a function definition in MathParser
     */
    public void addFunctionDefinition(FunctionDefinition functionDef) {
        log.debug("CepService.addFunctionDefinition(): Add new function definition: {}", functionDef);
        MathUtil.addFunctionDefinition(functionDef);
    }

    /**
     * Clear function definitions in MathParser
     */
    public void clearFunctionDefinitions() {
        log.debug("CepService.clearFunctionDefinitions(): Clear function definitions");
        MathUtil.clearFunctionDefinitions();
    }

    /**
     * Add/Set a constant in MathParser
     */
    public void setConstant(String constName, double constValue) {
        log.debug("CepService.setConstant(): Add/Set constant: name={}, value={}", constName, constValue);
        MathUtil.setConstant(constName, constValue);
    }

    /**
     * Add/Set constants in a map, in MathParser
     */
    public void setConstants(Map<String, Double> constants) {
        log.debug("CepService.setConstants(): Add/Set constants in map: {}", constants);
        MathUtil.setConstants(constants);
    }

    /**
     * Clear constants in MathParser
     */
    public void clearConstants() {
        log.debug("CepService.clearConstants(): Clear constants");
        MathUtil.clearConstants();
    }

    public static long getEventCounter() {
        return eventCounter.get();
    }

    public static synchronized void clearCounters() {
        eventCounter.set(0L);
    }
}
