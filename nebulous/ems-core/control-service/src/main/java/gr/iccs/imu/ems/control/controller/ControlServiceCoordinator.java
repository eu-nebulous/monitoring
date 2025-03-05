/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.NodeRegistry;
import gr.iccs.imu.ems.baguette.server.ServerCoordinator;
import gr.iccs.imu.ems.baguette.server.coordinator.NoopCoordinator;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.BrokerCepStatementSubscriber;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.control.collector.netdata.ServerNetdataCollector;
import gr.iccs.imu.ems.control.plugin.*;
import gr.iccs.imu.ems.control.properties.ControlServiceProperties;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import gr.iccs.imu.ems.control.util.TranslationContextMonitorGsonDeserializer;
import gr.iccs.imu.ems.control.util.mvv.NoopMetricVariableValuesServiceImpl;
import gr.iccs.imu.ems.util.EventBus;
import gr.iccs.imu.ems.translate.NoopTranslator;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.TranslationContextPrinter;
import gr.iccs.imu.ems.translate.Translator;
import gr.iccs.imu.ems.translate.model.Monitor;
import gr.iccs.imu.ems.translate.model.Sink;
import gr.iccs.imu.ems.translate.mvv.MetricVariableValuesService;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ControlServiceCoordinator implements InitializingBean {

    public final static String COORDINATOR_STATUS_TOPIC = "COORDINATOR_STATUS_TOPIC";

    private final ApplicationContext applicationContext;
    private final ControlServiceProperties properties;
    @Getter private final BaguetteServer baguetteServer;
    private final NodeRegistry nodeRegistry;
    private final WebClient webClient;
    private final PasswordUtil passwordUtil;
    private final EventBus<String,Object,Object> eventBus;

    private final List<AppModelPlugin> appModelPluginList;

    private final List<Translator> translatorImplementations;
    private Translator translator;                      // Will be populated in 'afterPropertiesSet()'
    private final List<PreTranslationPlugin> preTranslationPlugins;
    private final List<PostTranslationPlugin> postTranslationPlugins;
    private final List<TranslationContextPlugin> translationContextPlugins;
    private final TranslationContextPrinter translationContextPrinter;

    private final List<MetasolverPlugin> metasolverPlugins;

    private final List<MetricVariableValuesService> mvvServiceImplementations;
    private MetricVariableValuesService mvvService;     // Will be populated in 'afterPropertiesSet()'

    @Getter private BrokerCepService brokerCep;

    private final AtomicBoolean inUse = new AtomicBoolean();
    private final Map<String, TranslationContext> appModelToTcCache = new HashMap<>();

    @Getter private String currentAppModelId;
    @Getter private String currentAppExecModelId;
    private TranslationContext currentTC;

    private ServerNetdataCollector netdataCollector;

    public enum EMS_STATE {
        IDLE, INITIALIZING, RECONFIGURING, READY, ERROR
    }

    @Getter private EMS_STATE currentEmsState = EMS_STATE.IDLE;
    @Getter private String currentEmsStateMessage;
    @Getter private long currentEmsStateChangeTimestamp;


    @Override
    public void afterPropertiesSet() throws Exception {
        setCurrentEmsState(EMS_STATE.INITIALIZING, "Starting ControlServiceCoordinator...");

        initTranslator();
        initMvvService();
        startBaguetteServer();

        // Run configuration checks and throw exceptions early (before actually using EMS)
        if (properties.isSkipTranslation()) {
            if (StringUtils.isBlank(properties.getTcLoadFile()))
                throw new IllegalArgumentException("Model translation will be skipped (see property control.skip-translation), but no Translation Context file or pattern has been set. Check property: control.tc-load-file");
            log.warn("Model translation will be skipped, and a Translation Context file will be used: tc-file-pattern={}", properties.getTcLoadFile());
        }

        log.debug("ControlServiceCoordinator.afterPropertiesSet():    Post-translation plugins: {}", postTranslationPlugins);
        log.debug("ControlServiceCoordinator.afterPropertiesSet():  TranslationContext plugins: {}", translationContextPlugins);
        log.debug("ControlServiceCoordinator.afterPropertiesSet():          MetaSolver plugins: {}", metasolverPlugins);

        setCurrentEmsState(EMS_STATE.IDLE, "ControlServiceCoordinator started");
    }

    private void initMvvService() {
        // Initialize MVV service
        log.debug("ControlServiceCoordinator.initMvvService():  MVV service implementations: {}", mvvServiceImplementations);
        if (mvvServiceImplementations.size() == 1) {
            mvvService = mvvServiceImplementations.get(0);
        } else if (mvvServiceImplementations.isEmpty()) {
            throw new IllegalArgumentException("No MVV service implementation found");
        } else {
            mvvService = mvvServiceImplementations.stream()
                    .filter(s -> s!=null && !(s instanceof NoopMetricVariableValuesServiceImpl))
                    .findAny()
                    .orElse(new NoopMetricVariableValuesServiceImpl());
        }
        log.debug("ControlServiceCoordinator.initMvvService():  MVV service implementation selected: {}", mvvService);
        mvvService.init();
        log.debug("ControlServiceCoordinator.initMvvService():  MVV service initialized");
    }

    private void initTranslator() {
        log.debug("ControlServiceCoordinator.initTranslator():  Translator implementations: {}", translatorImplementations);
        if (translatorImplementations.size() == 1) {
            translator = translatorImplementations.getFirst();
        } else if (translatorImplementations.isEmpty()) {
            throw new IllegalArgumentException("No Translator implementations found");
        } else {
            translator = translatorImplementations.stream()
                    .filter(s -> s!=null && !(s instanceof NoopTranslator))
                    .findAny()
                    .orElse(new NoopTranslator());
        }
        log.debug("ControlServiceCoordinator.initTranslator():  Translator implementation selected: {}", translator);

        log.info("ControlServiceCoordinator.initTranslator(): Effective translator: {}", translator.getClass().getName());
    }

    private void startBaguetteServer() {
        log.debug("ControlServiceCoordinator.startBaguetteServer(): Starting Baguette Server...");
        try {
            baguetteServer.startServer(new NoopCoordinator());
        } catch (Exception ex) {
            log.error("ControlServiceCoordinator.startBaguetteServer(): EXCEPTION while starting Baguette server: ", ex);
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    public String getAppModelId() {
        return currentAppModelId;
    }

    public String getAppExecModelId() {
        return currentAppExecModelId;
    }

    public TranslationContext getTranslationContextOfAppModel(String appModelId) {
        return appModelToTcCache.get(_normalizeModelId(appModelId));
    }

    public void setCurrentEmsState(@NonNull EMS_STATE newState, String message) {
        this.currentEmsState = newState;
        this.currentEmsStateMessage = message;
        this.currentEmsStateChangeTimestamp = System.currentTimeMillis();

        eventBus.send(COORDINATOR_STATUS_TOPIC, Map.of(
                "state", newState,
                "message", Objects.requireNonNullElse(message, ""),
                "timestamp", currentEmsStateChangeTimestamp
        ), this);
    }

    // ------------------------------------------------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        log.debug("ControlServiceCoordinator.applicationReady(): invoked");
        log.info("ControlServiceCoordinator.applicationReady(): IP setting: {}", properties.getIpSetting());
        preloadModels();
    }

    @Async
    public void preloadModels() {
        String preloadAppModel = properties.getPreload().getAppModel();
        String preloadAppExecModel = properties.getPreload().getCpModel();
        if (StringUtils.isNotBlank(preloadAppModel)) {
            log.info("===================================================================================================");
            log.info("ControlServiceCoordinator.preloadModels(): Preloading models: app-model={}, app-exec-model={}",
                    preloadAppModel, preloadAppExecModel);
            processAppModel(preloadAppModel, preloadAppExecModel, ControlServiceRequestInfo.EMPTY);
        } else {
            log.info("ControlServiceCoordinator.preloadModels(): No model to preload");
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    public TranslationContext translateAppModel(String appModelId, String appModel, ControlServiceRequestInfo requestInfo) {
        AtomicReference<TranslationContext> _TC = new AtomicReference<>();
        _lockAndProcessModel(appModelId, null, requestInfo, "translateAppModel()", false, () -> {
            // Call '_translateAppModel()' to do actual processing
            _TC.set(_translateAppModel(appModelId, appModel, requestInfo));
            EMS_STATE state = _TC.get() != null ? EMS_STATE.READY : EMS_STATE.ERROR;
            return List.of(state, "", System.currentTimeMillis());
        });
        return _TC.get();
    }

    @Async
    public void processAppModel(String appModelId, String appExecModelId, ControlServiceRequestInfo requestInfo) {
        _lockAndProcessModel(appModelId, appExecModelId, requestInfo, "processAppModel()", true, () -> {
            // Call '_processNewModels()' to do actual processing
            _processAppModels(appModelId, appExecModelId, requestInfo);
            this.currentAppModelId = _normalizeModelId(appModelId);
            this.currentAppExecModelId = _normalizeModelId(appExecModelId);
            return Arrays.asList(getCurrentEmsState(), getCurrentEmsStateMessage(), getCurrentEmsStateChangeTimestamp());
        });
    }

    @Async
    public void processAppExecModel(String appExecModelId, ControlServiceRequestInfo requestInfo) {
        _lockAndProcessModel(null, appExecModelId, requestInfo, "processAppExecModel()", true, () -> {
            // Call '_processAppExecModel()' to do actual processing
            _processAppExecModel(appExecModelId, requestInfo);
            this.currentAppExecModelId = _normalizeModelId(appExecModelId);
            return List.of(getCurrentEmsState(), getCurrentEmsStateMessage(), getCurrentEmsStateChangeTimestamp());
        });
    }

    public Map<String,Double> getConstants(ControlServiceRequestInfo requestInfo) {
        if (brokerCep == null) {
            log.debug("ControlServiceCoordinator.getConstants(): Broker-CEP: Initializing...");
            brokerCep = applicationContext.getBean(BrokerCepService.class);
            log.debug("ControlServiceCoordinator.getConstants(): Broker-CEP: Initializing...ok");
        }

        Map<String,Double> constants = brokerCep.getConstants();
        log.debug("ControlServiceCoordinator.getConstants(): Constants from Broker-CEP: {}", constants);
        return constants;
    }

    @Async
    public void setConstants(@NonNull Map<String,Double> constants, ControlServiceRequestInfo requestInfo) {
        _lockAndProcessModel(null, null, requestInfo, "setConstants()", true, () -> {
            // Call '_setConstants()' to do actual processing
            _setConstants(constants, requestInfo);
            //return List.of(getCurrentEmsState(), getCurrentEmsStateMessage(), getCurrentEmsStateChangeTimestamp());
            return null;
        });
    }

    protected void _lockAndProcessModel(String appModelId, String appExecModelId, ControlServiceRequestInfo requestInfo,
                                        String caller, boolean updateEmsState, Supplier<List<Object>> callback)
    {
        if (requestInfo==null)
            requestInfo = ControlServiceRequestInfo.EMPTY;

        // Acquire lock of this coordinator
        if (!inUse.compareAndSet(false, true)) {
            String mesg = "ControlServiceCoordinator."+caller+": ERROR: Coordinator is in use. Exits immediately";
            log.warn(mesg);
            if (!properties.isSkipNotification()) {
                sendErrorNotification(appModelId, requestInfo, mesg, mesg);
            } else {
                log.warn("ControlServiceCoordinator.{}: Skipping notification due to configuration", caller);
            }
            return;
        }

        // Execute callback after acquiring lock
        EMS_STATE state = null;
        String stateMessage = null;
        long stateTimestamp = -1L;
        try {
            List<Object> result = callback.get();
            if (result!=null && result.size()==3) {
                state = (EMS_STATE) result.get(0);
                stateMessage = (String) result.get(1);
                stateTimestamp = (long) result.get(2);
            }
        } catch (Exception ex) {
            StringBuilder sb = new StringBuilder(ex.getClass().getName()).append(": ").append(ex.getMessage());
            Throwable t = ex;
            while (t.getCause()!=null) {
                t = t.getCause();
                sb.append(", caused by: ").append(t.getClass().getName()).append(": ").append(t.getMessage());
            }
            state = EMS_STATE.ERROR;
            stateMessage = sb.toString();
            stateTimestamp = System.currentTimeMillis();
            if (updateEmsState)
                setCurrentEmsState(state, stateMessage);

            String mesg = "ControlServiceCoordinator."+caller+": EXCEPTION: " + ex;
            log.error(mesg, ex);
            if (!properties.isSkipNotification()) {
                sendErrorNotification(appModelId, requestInfo, mesg, mesg);
            } else {
                log.warn("ControlServiceCoordinator.{}: Skipping notification due to configuration", caller);
            }
        } finally {
            // Release lock of this coordinator
            inUse.compareAndSet(true, false);
        }

        // Invoke requestInfo callback if provided
        if (requestInfo.getCallback()!=null) {
            requestInfo.getCallback().accept(Map.of(
                    "ems-state", StringUtils.defaultIfBlank(state!=null ? state.name() : null, "UNKNOWN"),
                    "ems-state-message", StringUtils.defaultIfBlank(stateMessage, ""),
                    "ems-state-change-timestamp", stateTimestamp
            ));
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    protected TranslationContext _translateAppModel(String appModelId, String appModel, ControlServiceRequestInfo requestInfo) {
        // Store app model if provided
        if (StringUtils.isNotBlank(appModel)) {
            if (translator.addModel(appModelId, appModel)!=null) {
                log.warn("ControlServiceCoordinator._translateAppModel(): Discarded previous app model contents: {}", appModelId);
            }
        }

        // Translate model into Translation Context (with EPL rules etc.)
        log.info("ControlServiceCoordinator._translateAppModel(): Translating app model: {}", appModelId);
        TranslationContext _TC;
        _TC = translateAppModelAndStore(appModelId, requestInfo, false);

        // Run TranslationContext plugins
        if (translationContextPlugins!=null && !translationContextPlugins.isEmpty()) {
            log.info("ControlServiceCoordinator._translateAppModel(): Running {} TranslationContext plugins", translationContextPlugins.size());
            translationContextPlugins.stream().filter(Objects::nonNull).forEach(plugin -> {
                log.debug("ControlServiceCoordinator._translateAppModel(): Calling TranslationContext plugin: {}", plugin.getClass().getName());
                plugin.processTranslationContext(_TC);
                log.debug("ControlServiceCoordinator._translateAppModel(): RESULTS after running TranslationContext plugin: {}\n{}", plugin.getClass().getName(), _TC);
            });
        } else {
            log.info("ControlServiceCoordinator._translateAppModel(): No TranslationContext plugins found");
        }

        // Print resulting Translation Context
        try {
            translationContextPrinter.printResults(_TC, null);
        } catch (Exception e) {
            log.error("ControlServiceCoordinator._translateAppModel(): EXCEPTION while printing Translation results: ", e);
        }

        return _TC;
    }

    protected void _processAppModels(String appModelId, String appExecModelId, ControlServiceRequestInfo requestInfo) {
        log.info("ControlServiceCoordinator._processAppModel(): BEGIN: app-model-id={}, app-exec-model-id={}, request-info={}", appModelId, appExecModelId, requestInfo);

        // Run pre-processing plugins
        log.debug("ControlServiceCoordinator._processAppModel(): appModelPluginList: {}", appModelPluginList);
        if (appModelPluginList!=null) {
            for (AppModelPlugin plugin : appModelPluginList) {
                if (plugin!=null) {
                    log.debug("ControlServiceCoordinator._processAppModel():   Calling preProcessingNewAppModel on plugin: {}", plugin);
                    plugin.preProcessingNewAppModel(appModelId, requestInfo);
                }
            }
        }

        // Translate model into Translation Context (with EPL rules etc.)
        TranslationContext _TC;
        if (!properties.isSkipTranslation()) {
            _TC = translateAppModelAndStore(appModelId, requestInfo, true);
        } else {
            log.warn("ControlServiceCoordinator._processAppModel(): Skipping translation due to configuration");
            _TC = loadStoredTranslationContext(appModelId);
        }

        // Run TranslationContext plugins
        if (translationContextPlugins!=null && !translationContextPlugins.isEmpty()) {
            log.info("ControlServiceCoordinator._processAppModel(): Running {} TranslationContext plugins", translationContextPlugins.size());
            translationContextPlugins.stream().filter(Objects::nonNull).forEach(plugin -> {
                log.debug("ControlServiceCoordinator._processAppModel(): Calling TranslationContext plugin: {}", plugin.getClass().getName());
                plugin.processTranslationContext(_TC);
                log.debug("ControlServiceCoordinator._processAppModel(): RESULTS after running TranslationContext plugin: {}\n{}", plugin.getClass().getName(), _TC);
            });
        } else {
            log.info("ControlServiceCoordinator._processAppModel(): No TranslationContext plugins found");
        }

        // Print resulting Translation Context
        try {
            translationContextPrinter.printResults(_TC, null);
        } catch (Exception e) {
            log.error("ControlServiceCoordinator._processAppModel(): EXCEPTION while printing Translation results: ", e);
        }

        // Retrieve Metric Variable Values (MVV) from App Exec model - i.e. constants
        Map<String, Double> constants = new HashMap<>( _TC.getConstantDefaults() );
        if (!properties.isSkipMvvRetrieve()) {
            if (StringUtils.isNotBlank(appExecModelId)) {
                constants.putAll( retrieveConstantsFromAppExecModel(appExecModelId, _TC, EMS_STATE.INITIALIZING) );
            } else {
                log.warn("ControlServiceCoordinator._processAppModel(): No App Exec model has been provided");
            }
        } else {
            log.warn("ControlServiceCoordinator._processAppModel(): Skipping MVV retrieval due to configuration");
        }

        // (Re-)Configure Broker and CEP
        String upperwareGrouping = properties.getUpperwareGrouping();
        if (!properties.isSkipBrokerCep()) {
            configureBrokerCep(appModelId, _TC, constants, upperwareGrouping);
        } else {
            log.warn("ControlServiceCoordinator._processAppModel(): Skipping Broker-CEP setup due to configuration");
        }

        // Process placeholders in sink type configurations
        if (brokerCep!=null && brokerCep.getBrokerCepProperties()!=null) {
            String brokerUrlForClients = brokerCep.getBrokerCepProperties().getBrokerUrlForClients();
            processPlaceholdersInMonitors(_TC, brokerUrlForClients);
        }

        // (Re-)Configure Baguette server
        if (!properties.isSkipBaguette()) {
            configureBaguetteServer(appModelId, _TC, constants, upperwareGrouping);
        } else {
            log.warn("ControlServiceCoordinator._processAppModel(): Skipping Baguette Server setup due to configuration");
        }

        // Start/Stop Netdata collector
        if (!properties.isSkipCollectors() && !properties.isSkipBaguette() && nodeRegistry!=null) {
            startNetdataCollector(appModelId);
        } else {
            log.warn("ControlServiceCoordinator._processAppModel(): Skipping Collectors setup due to configuration");
        }

        // (Re-)Configure MetaSolver
        if (!properties.isSkipMetasolver()) {
            configureMetaSolver(_TC, requestInfo.getJwtToken());
        } else {
            log.warn("ControlServiceCoordinator._processAppModel(): Skipping MetaSolver setup due to configuration");
        }

        // Cache _TC in order to reply to Adapter queries about component-to-sensor mappings and sensor-configuration
        log.info("ControlServiceCoordinator._processAppModel(): Cache translation results: app-model-id={}", appModelId);
        appModelToTcCache.put(_normalizeModelId(appModelId), _TC);

        // Notify others, if 'notificationUri' is provided
        if (!properties.isSkipNotification()) {
            notifyOthers(appModelId, requestInfo, EMS_STATE.INITIALIZING);
        } else {
            log.warn("ControlServiceCoordinator._processAppModel(): Skipping notification due to configuration");
        }

        // Run post-processing plugins
        log.debug("ControlServiceCoordinator._processAppModel(): appModelPluginList: {}", appModelPluginList);
        if (appModelPluginList!=null) {
            for (AppModelPlugin plugin : appModelPluginList) {
                if (plugin!=null) {
                    log.debug("ControlServiceCoordinator._processAppModel():   Calling postProcessingNewAppModel on plugin: {}", plugin);
                    plugin.postProcessingNewAppModel(appModelId, requestInfo, _TC);
                }
            }
        }

        this.currentTC = _TC;
        log.info("ControlServiceCoordinator._processAppModel(): END: app-model-id={}", appModelId);

        setCurrentEmsState(EMS_STATE.READY, null);
    }

    protected void _processAppExecModel(String appExecModelId, ControlServiceRequestInfo requestInfo) {
        log.info("ControlServiceCoordinator._processAppExecModel(): BEGIN: app-exec-model-id={}, request-info={}", appExecModelId, requestInfo);
        log.info("ControlServiceCoordinator._processAppExecModel(): Current app-model-id={}", currentAppModelId);
        TranslationContext _TC = this.currentTC;

        // Retrieve Metric Variable Values (MVV) from App Exec model
        Map<String, Double> constants = new HashMap<>();
        if (!properties.isSkipMvvRetrieve()) {
            constants = retrieveConstantsFromAppExecModel(appExecModelId, _TC, EMS_STATE.RECONFIGURING);
        } else {
            log.warn("ControlServiceCoordinator._processAppExecModel(): Skipping MVV retrieval due to configuration");
        }

        // Set MVV constants in Broker-CEP and Baguette Server, and then notify others
        _setConstants(constants, requestInfo);

        log.info("ControlServiceCoordinator._processAppExecModel(): END: app-exec-model-id={}", appExecModelId);

        setCurrentEmsState(EMS_STATE.READY, null);
    }

    protected void _setConstants(@NonNull Map<String,Double> constants, ControlServiceRequestInfo requestInfo) {
        log.info("ControlServiceCoordinator.setConstants(): BEGIN: constants={}, request-info={}", constants, requestInfo);
        log.info("ControlServiceCoordinator.setConstants(): constants={}", constants);

        // Retrieve Metric Variable Values (MVV) from App Exec model
        if (properties.isSkipMvvRetrieve()) {
            log.warn("ControlServiceCoordinator.setConstants(): isSkipMvvRetrieve is true, but constants processing will continue");
        }

        // (Re-)Configure Broker and CEP
        if (!properties.isSkipBrokerCep()) {
            reconfigureBrokerCep(constants);
        } else {
            log.warn("ControlServiceCoordinator.setConstants(): Skipping Broker-CEP setup due to configuration");
        }

        // (Re-)Configure Baguette server
        if (!properties.isSkipBaguette()) {
            reconfigureBaguetteServer(constants);
        } else {
            log.warn("ControlServiceCoordinator.setConstants(): Skipping Baguette Server setup due to configuration");
        }

        // Notify others, if 'notificationUri' is provided
        if (!properties.isSkipNotification()) {
            notifyOthers(null, requestInfo, EMS_STATE.RECONFIGURING);
        } else {
            log.warn("ControlServiceCoordinator.setConstants(): Skipping notification due to configuration");
        }

        log.info("ControlServiceCoordinator.setConstants(): END: constants={}", constants);

        setCurrentEmsState(EMS_STATE.READY, null);
    }

    private TranslationContext translateAppModelAndStore(String appModelId, ControlServiceRequestInfo requestInfo, boolean updateEmsState) {
        final String applicationId = requestInfo.getApplicationId();
        final TranslationContext _TC;
        if (updateEmsState) setCurrentEmsState(EMS_STATE.INITIALIZING, "Retrieving and translating model");

        // Run pre-translation plugins
        if (preTranslationPlugins!=null && !preTranslationPlugins.isEmpty()) {
            log.info("ControlServiceCoordinator.translateAppModelAndStore(): Running {} pre-translation plugins", preTranslationPlugins.size());
            AtomicReference<String> appModelIdRef = new AtomicReference<>(appModelId);
            preTranslationPlugins.stream().filter(Objects::nonNull).forEach(plugin -> {
                log.debug("ControlServiceCoordinator.translateAppModelAndStore(): Calling pre-translation plugin: {}", plugin.getClass().getName());
                String newAppModelId = plugin.preprocessModel(appModelIdRef.get(), applicationId, requestInfo.getAdditionalArguments());
                if (StringUtils.isNotBlank(newAppModelId))
                    appModelIdRef.set(newAppModelId);
                log.debug("ControlServiceCoordinator.translateAppModelAndStore(): RESULTS after running pre-translation plugin: {} -- appModelId: {}", plugin.getClass().getName(), appModelIdRef.get());
            });
            if (!appModelIdRef.get().equals(appModelId))
                log.info("ControlServiceCoordinator.translateAppModelAndStore(): Original app-model-id has been replaced during pre-translation: {} --> {}", appModelId, appModelIdRef.get());
            appModelId = appModelIdRef.get();
        } else {
            log.info("ControlServiceCoordinator.translateAppModelAndStore(): No pre-translation plugins found");
        }

        // Translate application model into a TranslationContext object
        log.info("ControlServiceCoordinator.translateAppModelAndStore(): Model translation: model-id={}", appModelId);
        _TC = translator.translate(appModelId, applicationId, requestInfo.getAdditionalArguments());
        _TC.populateTopLevelMetricNames();
        log.debug("ControlServiceCoordinator.translateAppModelAndStore(): Model translation: RESULTS: {}", _TC);

        // Run post-translation plugins
        if (postTranslationPlugins!=null && !postTranslationPlugins.isEmpty()) {
            log.info("ControlServiceCoordinator.translateAppModelAndStore(): Running {} post-translation plugins", postTranslationPlugins.size());
            postTranslationPlugins.stream().filter(Objects::nonNull).forEach(plugin -> {
                log.debug("ControlServiceCoordinator.translateAppModelAndStore(): Calling post-translation plugin: {}", plugin.getClass().getName());
                plugin.processTranslationResults(_TC, applicationContext.getBean(TopicBeacon.class));
                log.debug("ControlServiceCoordinator.translateAppModelAndStore(): RESULTS after running post-translation plugin: {}\n{}", plugin.getClass().getName(), _TC);
            });
        } else {
            log.info("ControlServiceCoordinator.translateAppModelAndStore(): No post-translation plugins found");
        }

        // Serialize and store 'TranslationContext' in a file
        String fileName = properties.getTcSaveFile();
        if (StringUtils.isNotBlank(fileName)) {
            try {
                if (updateEmsState) setCurrentEmsState(EMS_STATE.INITIALIZING, "Storing translation context to file");

                // Get TC file name
                fileName = getTcFileName(appModelId, fileName);
                if (Paths.get(fileName).toFile().exists()) {
                    log.warn("ControlServiceCoordinator.translateAppModelAndStore(): The specified Translation Context file already exists. Its contents will be overwritten: tc-file-pattern={}, tc-file={}", properties.getTcLoadFile(), fileName);
                }

                // Store _TC in a file
                log.debug("ControlServiceCoordinator.translateAppModelAndStore(): Start serializing _TC data in file: {}", fileName);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Writer writer = new FileWriter(fileName);
                gson.toJson(_TC, writer);
                writer.close();

//                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
//                mapper.writeValue(Paths.get(fileName+".yml").toFile(), _TC);

                log.debug("ControlServiceCoordinator.translateAppModelAndStore(): Serialized _TC data in file: {}", fileName);
                log.info("ControlServiceCoordinator.translateAppModelAndStore(): Saved translation data in file: {}", fileName);

            } catch (IOException ex) {
                log.error("ControlServiceCoordinator.translateAppModelAndStore(): FAILED to serialize _TC to file: {} : Exception: ", fileName, ex);
            }
        }
        return _TC;
    }

    private TranslationContext loadStoredTranslationContext(String appModelId) {
        TranslationContext _TC;

        // deserialize 'TranslationContext' from file
        String fileName = properties.getTcLoadFile();
        if (StringUtils.isNotBlank(fileName)) {
            setCurrentEmsState(EMS_STATE.INITIALIZING, "Loading translation context from file");

            try {
                fileName = getTcFileName(appModelId, fileName);
                if (! Paths.get(fileName).toFile().exists()) {
                    log.error("ControlServiceCoordinator.loadStoredTranslationContext(): The specified Translation Context file does not exist: tc-file-pattern={}, tc-file={}", properties.getTcLoadFile(), fileName);
                    throw new IllegalArgumentException("The specified Translation Context file does not exist. Check property: control.tc-load-file=" + properties.getTcLoadFile() + ", file-name=" + fileName);
                }
                log.info("ControlServiceCoordinator.loadStoredTranslationContext(): Loading translator data from file: {}", fileName);
                log.debug("ControlServiceCoordinator.loadStoredTranslationContext(): Start deserializing _TC data from file: {}", fileName);
                Reader reader = new FileReader(fileName);
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(Monitor.class, new TranslationContextMonitorGsonDeserializer())
                        .create();
                _TC = gson.fromJson(reader, TranslationContext.class);
                reader.close();
                log.debug("ControlServiceCoordinator.loadStoredTranslationContext(): Deserialized _TC data from file: {}", fileName);
            } catch (IOException ex) {
                log.error("ControlServiceCoordinator.loadStoredTranslationContext(): FAILED to deserialize _TC from file: {} : Exception: ", fileName, ex);
                throw new IllegalArgumentException("Failed to load translation data from file: " + fileName, ex);
            }
        } else {
            log.error("ControlServiceCoordinator.loadStoredTranslationContext(): No translation context file has been set");
            throw new IllegalArgumentException("No translation context file has been set");
        }
        return _TC;
    }

    private String getTcFileName(@NonNull String appModelId, @NonNull String fileName) {
        appModelId = StringUtils.removeStart(appModelId, "/");
        return fileName.formatted(appModelId.replaceAll("[^\\p{L}\\d]", "_"));
    }

    private Map<String, Double> retrieveConstantsFromAppExecModel(String appExecModelId, TranslationContext _TC, EMS_STATE emsState) {
        Map<String, Double> constants = Collections.emptyMap();
        if (StringUtils.isNotBlank(appExecModelId)) {
            setCurrentEmsState(emsState, "Retrieving MVVs from App Exec model");

            try {
                log.debug("ControlServiceCoordinator.retrieveConstantsFromAppExecModel(): Retrieving MVVs from App Exec model: app-exec-model-id={}", appExecModelId);

                // Retrieve constant names from '_TC.MVV_CP' and values from a given App Exec model
                log.debug("ControlServiceCoordinator.retrieveConstantsFromAppExecModel(): Looking for MVV_CP's: {}", _TC.getMvvCP());
                constants = mvvService.getMatchingMetricVariableValues(appExecModelId, _TC);
                if (constants==null) constants = mvvService.getMetricVariableValues(appExecModelId, null);
                log.debug("ControlServiceCoordinator.retrieveConstantsFromAppExecModel(): MVVs retrieved from App Exec model: app-exec-model-id={}, MVVs={}", appExecModelId, constants);

            } catch (Exception ex) {
                log.error("ControlServiceCoordinator.retrieveConstantsFromAppExecModel(): EXCEPTION while retrieving MVVs from App Exec model: app-exec-model-id={}", appExecModelId, ex);
            }
        } else {
            log.error("ControlServiceCoordinator.retrieveConstantsFromAppExecModel(): No App Exec model have been provided");
        }
        return constants;
    }

    private void configureBrokerCep(String appModelId, TranslationContext _TC, Map<String, Double> constants, String upperwareGrouping) {
        setCurrentEmsState(EMS_STATE.INITIALIZING, "initializing Broker-CEP");

        try {
            // Initializing Broker-CEP module if necessary
            if (brokerCep == null) {
                log.debug("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: Initializing...");
                brokerCep = applicationContext.getBean(BrokerCepService.class);
                log.debug("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: Initializing...ok");
            }

            // Get event types for GLOBAL grouping (i.e. that of Upperware)
            log.debug("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: Upperware grouping: {}", upperwareGrouping);
            Set<String> eventTypeNames = _TC.getG2T().get(upperwareGrouping);
            log.debug("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: Configuration of Event Types: {}", eventTypeNames);
            if (eventTypeNames == null || eventTypeNames.isEmpty())
                throw new RuntimeException("Broker-CEP: No event types for GLOBAL grouping");

            // Clear any previous event types, statements or function definitions and register the new ones
            brokerCep.clearState();
            brokerCep.addEventTypes(eventTypeNames, EventMap.getPropertyNames(), EventMap.getPropertyClasses());

            log.debug("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: Constants: {}", constants);
            brokerCep.setConstants(constants);

            log.debug("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: Function definitions: {}", _TC.getFunctionDefinitions());
            brokerCep.addFunctionDefinitions(_TC.getFunctionDefinitions());

            Map<String, Set<String>> ruleStatements = _TC.getG2R().get(upperwareGrouping);
            log.debug("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: Configuration of EPL statements: {}", ruleStatements);
            if (ruleStatements != null) {
                int cnt = 0;
                for (Map.Entry<String, Set<String>> topicRules : ruleStatements.entrySet()) {
                    String topicName = topicRules.getKey();
                    for (String rule : topicRules.getValue()) {
                        brokerCep.getCepService().addStatementSubscriber(
                                new BrokerCepStatementSubscriber("Subscriber_" + cnt++, topicName, rule, brokerCep, passwordUtil)
                        );
                    }
                }
                log.debug("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: Added {} EPL statements", cnt);
            } else {
                log.warn("ControlServiceCoordinator.configureBrokerCep(): Broker-CEP: No EPL statements found for GLOBAL grouping");
            }
        } catch (Exception ex) {
            log.error("ControlServiceCoordinator.configureBrokerCep(): EXCEPTION while initializing Broker-CEP of Upperware: app-model-id={}", appModelId, ex);
        }
    }

    private void reconfigureBrokerCep(Map<String, Double> constants) {
        try {
            setCurrentEmsState(EMS_STATE.RECONFIGURING, "Reconfiguring Broker-CEP");

            // Initializing Broker-CEP module if necessary
            if (brokerCep == null) {
                log.debug("ControlServiceCoordinator.reconfigureBrokerCep(): Broker-CEP: Initializing...");
                brokerCep = applicationContext.getBean(BrokerCepService.class);
                log.debug("ControlServiceCoordinator.reconfigureBrokerCep(): Broker-CEP: Initializing...ok");
            }

            log.debug("ControlServiceCoordinator.reconfigureBrokerCep(): Passing constants to Broker-CEP: {}", constants);
            brokerCep.setConstants(constants);
        } catch (Exception ex) {
            log.error("ControlServiceCoordinator.reconfigureBrokerCep(): EXCEPTION while initializing Broker-CEP with constants: constants={}", constants, ex);
        }
    }

    private static void processPlaceholdersInMonitors(TranslationContext _TC, String brokerUrlForClients) {
        for (Monitor mon : _TC.getMON()) {
            if (mon.getSinks()!=null) {
                for (Sink s : mon.getSinks()) {
                    s.getConfiguration().entrySet().forEach(entry -> {
                        if (entry.getValue() != null)
                            entry.setValue(entry.getValue().replace("%{BROKER_URL}%", brokerUrlForClients));
                    });
                }
            }
        }
    }

    private void configureBaguetteServer(String appModelId, TranslationContext _TC, Map<String, Double> constants, String upperwareGrouping) {
        setCurrentEmsState(EMS_STATE.INITIALIZING, "Initializing Baguette Server");

        log.debug("ControlServiceCoordinator.configureBaguetteServer(): Re-configuring Baguette Server: app-model-id={}", appModelId);
        try {
            baguetteServer.setTopologyConfiguration(_TC, constants, upperwareGrouping, brokerCep);
        } catch (Exception ex) {
            log.error("ControlServiceCoordinator.configureBaguetteServer(): EXCEPTION while starting Baguette server: app-model-id={}", appModelId, ex);
        }
    }

    private void reconfigureBaguetteServer(Map<String, Double> constants) {
        setCurrentEmsState(EMS_STATE.RECONFIGURING, "Reconfiguring Baguette Server");

        log.debug("ControlServiceCoordinator.reconfigureBaguetteServer(): Re-configuring Baguette Server with constants: {}", constants);
        try {
            baguetteServer.sendConstants(constants);
        } catch (Exception ex) {
            log.error("ControlServiceCoordinator.reconfigureBaguetteServer(): EXCEPTION while configuring Baguette server: constants={}", constants, ex);
        }
    }

    private void startNetdataCollector(String appModelId) {
        // Stop any running Netdata collector instance
        if (netdataCollector!=null) {
            log.info("ControlServiceCoordinator.startNetdataCollector(): Stopping NetdataCollector: app-model-id={}", appModelId);
            try {
                netdataCollector.stop();
            } catch (Exception ex) {
                log.error("ControlServiceCoordinator.startNetdataCollector(): EXCEPTION while stopping NetdataCollector: app-model-id={}", appModelId, ex);
            }
        }

        // Starting new Netdata collector instance, if needed
        ServerCoordinator serverCoordinator = nodeRegistry.getCoordinator();
        if (! serverCoordinator.supportsAggregators()) {
            if (netdataCollector==null) {
                netdataCollector = applicationContext.getBean(ServerNetdataCollector.class);
            }
            log.info("ControlServiceCoordinator.startNetdataCollector(): Starting NetdataCollector: app-model-id={}", appModelId);
            try {
                netdataCollector.start();
            } catch (Exception ex) {
                log.error("ControlServiceCoordinator.startNetdataCollector(): EXCEPTION while starting NetdataCollector: app-model-id={}", appModelId, ex);
            }
        } else {
            log.info("ControlServiceCoordinator.startNetdataCollector(): NetdataCollector is not needed (will not start it): app-model-id={}", appModelId);
        }
    }

    private void configureMetaSolver(TranslationContext _TC, String jwtToken) {
        setCurrentEmsState(EMS_STATE.INITIALIZING, "Sending configuration to MetaSolver");

        // Check that MetaSolver configuration URL has been set
        if (StringUtils.isEmpty(properties.getMetasolverConfigurationUrl())) {
            log.warn("ControlServiceCoordinator.configureMetaSolver(): MetaSolver endpoint is empty. Skipping Metasolver configuration");
            return;
        }

        // Get scaling event and SLO topics from _TC
        Set<String> scalingTopics = new HashSet<>();
        scalingTopics.addAll(_TC.getE2A().keySet());
        scalingTopics.addAll(_TC.getSLO());
        log.debug("ControlServiceCoordinator.configureMetaSolver(): MetaSolver configuration: scaling-topics: {}", scalingTopics);

        // Get top-level metric topics from _TC
        Set<String> topLevelMetrics = _TC.getTopLevelMetricNames(true);
        log.debug("ControlServiceCoordinator.configureMetaSolver(): Top-Level metrics: {}", topLevelMetrics);
        Set<String> metricTopics = topLevelMetrics.stream()
                .filter(m -> !scalingTopics.contains(m))
                .collect(Collectors.toSet());
        log.debug("ControlServiceCoordinator.configureMetaSolver(): MetaSolver configuration: metric-topics: {}", metricTopics);

        // Let Metasolver plugins modify topics sets
        metasolverPlugins.forEach(p -> p.topicsCollected(_TC, scalingTopics, metricTopics));

        // Prepare subscription configurations
        String upperwareBrokerUrl = brokerCep != null ? brokerCep.getBrokerCepProperties().getBrokerUrlForClients() : null;
        boolean usesAuthentication = brokerCep.getBrokerCepProperties().isAuthenticationEnabled();
        String username = usesAuthentication ? brokerCep.getBrokerUsername() : null;
        String password = usesAuthentication ? brokerCep.getBrokerPassword() : null;
        String certificate = brokerCep.getBrokerCertificate();
        log.debug("ControlServiceCoordinator.configureMetaSolver(): Local Broker: uses-authentication={}, username={}, password={}, has-certificate={}",
                usesAuthentication, username, passwordUtil.encodePassword(password), StringUtils.isNotBlank(certificate));
        log.trace("ControlServiceCoordinator.configureMetaSolver(): Local Broker: broker-certificate={}", certificate);

        if (StringUtils.isBlank(upperwareBrokerUrl)) {
            log.warn("ControlServiceCoordinator.configureMetaSolver(): No Broker URL has been specified or Broker-CEP module is deactivated");
        }
        List<Map<String, String>> subscriptionConfigs = new ArrayList<>();
        for (String t : scalingTopics)
            subscriptionConfigs.add(_prepareSubscriptionConfig(_TC, upperwareBrokerUrl, username, password, certificate, t, "", "SCALE"));
        for (String t : metricTopics)
            subscriptionConfigs.add(_prepareSubscriptionConfig(_TC, upperwareBrokerUrl, username, password, certificate, t, "", "MVV"));
        log.debug("ControlServiceCoordinator.configureMetaSolver(): MetaSolver subscriptions configuration: {}", subscriptionConfigs);

        // Retrieve MVV to Current-Config MVV map
        Map<String, String> mvvMap = _TC.getMvvCP();
        log.debug("ControlServiceCoordinator.configureMetaSolver(): MetaSolver MVV configuration: {}", mvvMap);

        // Let Metasolver plugins modify MVV map
        metasolverPlugins.forEach(p -> p.mvvsCollected(_TC, mvvMap));

        // Prepare MetaSolver configuration
        Map<String,Object> msConfig = new HashMap<>();
        msConfig.put("subscriptions", subscriptionConfigs);
        msConfig.put("mvv", mvvMap);

        // POST configuration to MetaSolver
        String metaSolverEndpoint = properties.getMetasolverConfigurationUrl();
        Gson gson = new Gson();
        String json = gson.toJson(msConfig);
        log.debug("ControlServiceCoordinator.configureMetaSolver(): MetaSolver configuration in JSON: {}", json);

        try {
            log.info("ControlServiceCoordinator.configureMetaSolver(): Calling MetaSolver: endpoint={}", metaSolverEndpoint);
            ResponseEntity<String> response = webClient.post()
                    .uri(metaSolverEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, jwtToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(json)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
            String metaSolverResponse = (response!=null && response.getStatusCode().is2xxSuccessful()) ? response.getBody() : null;
            log.info("ControlServiceCoordinator.configureMetaSolver(): MetaSolver response: endpoint={}, status={},  message={}",
                    metaSolverEndpoint, response!=null ? response.getStatusCode() : null, metaSolverResponse);
        } catch (Exception ex) {
            log.error("ControlServiceCoordinator.configureMetaSolver(): Failed to call MetaSolver: endpoint={}, EXCEPTION: ", metaSolverEndpoint, ex);
        }
    }

    private void notifyOthers(String appModelId, ControlServiceRequestInfo requestInfo, @NonNull EMS_STATE emsState) {
        if (StringUtils.isNotBlank(requestInfo.getNotificationUri())) {
            setCurrentEmsState(emsState, "Notifying others");

            String notificationUri = requestInfo.getNotificationUri().trim();
            log.debug("ControlServiceCoordinator.notifyOthers(): Notifying others: {}", notificationUri);
            sendSuccessNotification(appModelId, requestInfo);
            log.debug("ControlServiceCoordinator.notifyOthers(): Others notified: {}", notificationUri);
        } else {
            log.warn("ControlServiceCoordinator.notifyOthers(): Notification URI is blank");
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    protected String _normalizeModelId(String modelId) {
        if (StringUtils.isBlank(modelId)) return modelId;
        modelId = modelId.trim();
        if (!modelId.startsWith("/")) modelId = "/"+modelId;
        return modelId;
    }

    protected Map<String, String> _prepareSubscriptionConfig(TranslationContext _TC, String url, String username, String password, String certificate, String topic, String clientId, String type) {
        Map<String, String> map = new HashMap<>();
        map.put("url", url);
        map.put("username", username);
        map.put("password", password);
        map.put("certificate", certificate);
        map.put("topic", topic);
        map.put("client-id", clientId);
        map.put("type", type);

        // Let Metasolver plugins modify subscription
        metasolverPlugins.forEach(p -> p.prepareSubscription(_TC, map));

        return map;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Notification methods
    // ------------------------------------------------------------------------------------------------------------

    private void sendSuccessNotification(String applicationId, ControlServiceRequestInfo requestInfo) {
        // Prepare success result notification
        Map<String,String> result = new LinkedHashMap<>();
        result.put("STATUS", "SUCCESS");

        // Prepare and send Notification
        try {
            sendAppModelNotification(applicationId, result, requestInfo);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void sendErrorNotification(String applicationId, ControlServiceRequestInfo requestInfo,
                                       String errorCode, String errorDescription)
    {
        // Prepare error result notification
        Map<String,String> result = new LinkedHashMap<>();
        result.put("STATUS", "ERROR");
        result.put("ERROR-CODE", errorCode);
        result.put("ERROR-DESCRIPTION", errorDescription);

        // Prepare and send App Model notification
        try {
            sendAppModelNotification(applicationId, result, requestInfo);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void sendAppModelNotification(String applicationId, Map<String,String> result, ControlServiceRequestInfo requestInfo) {
        // Create a new watermark
        Map<String,String> watermark = new LinkedHashMap<>();
        watermark.put("USER", "EMS");
        watermark.put("SYSTEM", "EMS");
        watermark.put("DATE", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(LocalDateTime.now()));
        String uuid = Objects.requireNonNullElse( requestInfo.getRequestUuid(), UUID.randomUUID().toString().toLowerCase() );
        watermark.put("UUID", uuid);

        // Create a new App Model notification
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("application-id", applicationId);
        request.put("result", request);
        request.put("watermark", watermark);

        // Send App Model notification
        sendAppModelNotification(request, requestInfo);
    }

    private void sendAppModelNotification(Map<String,Object> notification, ControlServiceRequestInfo requestInfo) {
        String notificationUri = requestInfo.getNotificationUri();
        String requestUuid = requestInfo.getRequestUuid();
        String jwtToken = requestInfo.getJwtToken();

        // Check if 'notificationUri' is blank
        if (StringUtils.isBlank(notificationUri)) {
            log.warn("ControlServiceCoordinator.sendAppModelNotification(): notificationUri not provided or is empty. No notification will be sent.");
            return;
        }
        notificationUri = notificationUri.trim();

        // Get Notification URL from control-service configuration
        String notificationUrl = properties.getNotificationUrl();
        if (StringUtils.isBlank(notificationUrl)) {
            log.warn("ControlServiceCoordinator.sendAppModelNotification(): notification-url property is empty. No notification will be sent.");
            return;
        }
        notificationUrl = notificationUrl.trim();

        // Fixing Notification URL parts
        if (notificationUrl.endsWith("/")) {
            notificationUrl = notificationUrl.substring(0, notificationUrl.length() - 1);
        }
        if (notificationUri.startsWith("/")) {
            notificationUri = notificationUri.substring(1);
        }

        // Call Notification URL endpoint
        String url = notificationUrl + "/" + notificationUri;
        log.info("ControlServiceCoordinator.sendAppModelNotification(): Invoking Notification endpoint: {}", url);
        log.trace("ControlServiceCoordinator.sendAppModelNotification(): JWT token: {}", jwtToken);

        ResponseEntity<String> response;
        response = webClient.post().
                uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, jwtToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Request-UUID", requestUuid)
                .bodyValue(notification)
                .retrieve()
                .toEntity(String.class)
                .block();

        if (response!=null) {
            String responseStatus = response.getStatusCode().toString();
            if (response.getStatusCode().is2xxSuccessful())
                log.info("ControlServiceCoordinator.sendAppModelNotification(): Notification endpoint invoked: {}, status={}, message={}", url, responseStatus, response.getBody());
            else
                log.info("ControlServiceCoordinator.sendAppModelNotification(): Notification endpoint invoked: {}, status={}, message={}", url, responseStatus, response.getBody());
        } else {
            log.warn("ControlServiceCoordinator.sendAppModelNotification(): Notification endpoint invoked: {}, response is NULL", url);
        }
    }

    public <T> HttpEntity<T> createHttpEntity(Class<T> notificationType, Object notification, String jwtToken) {
        HttpHeaders headers = createHttpHeaders(jwtToken);
        return new HttpEntity<T>(notificationType.cast(notification), headers);
    }

    private HttpHeaders createHttpHeaders(String jwtToken) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.isNotBlank(jwtToken)) {
            headers.set(HttpHeaders.AUTHORIZATION, jwtToken);
        }
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    // ------------------------------------------------------------------------------------------------------------
}
