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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ControlServiceController {

    private final ControlServiceCoordinator coordinator;

    @Getter
    private List<String> controllerEndpoints;
    @Getter
    private List<String> controllerEndpointsShort;

    // ------------------------------------------------------------------------------------------------------------
    // Application Model methods
    // ------------------------------------------------------------------------------------------------------------

    @PostMapping(value = { "/appModel", "/appModelJson" }, consumes = MediaType.APPLICATION_JSON_VALUE)
    public String newAppModel(@RequestBody String requestStr,
                              @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.debug("ControlServiceController.newAppModel(): Received request: {}", requestStr);
        log.trace("ControlServiceController.newAppModel(): JWT token: {}", jwtToken);

        // Extract Ids from request
        Map<String, String> appIds = extractAppIds(requestStr);
        String appModelId = appIds.get("appModelId");
        String appExecModelId = appIds.get("appExecModelId");
        String applicationId = appIds.get("applicationId");

        // Start translation and component reconfiguration in a worker thread
        coordinator.processAppModel(appModelId, appExecModelId, ControlServiceRequestInfo.create(applicationId, null, null, jwtToken, null));
        log.debug("ControlServiceController.newAppModel(): Model translation dispatched to a worker thread");

        return "OK";
    }

    private Map<String,String> extractAppIds(String requestStr) {
        // Use Gson to get model id's from request body (in JSON format)
        JsonObject jObj = new Gson().fromJson(requestStr, JsonObject.class);
        String appModelId = Optional.ofNullable(jObj.get("app-model-id")).map(je -> stripQuotes(je.toString())).orElse(null);
        if (StringUtils.isBlank(appModelId))
            appModelId = Optional.ofNullable(jObj.get("applicationId")).map(je -> stripQuotes(je.toString())).orElse(null);
        String appExecModelId = Optional.ofNullable(jObj.get("app-exec-model-id")).map(je -> stripQuotes(je.toString())).orElse(null);
        if (StringUtils.isBlank(appExecModelId))
            appExecModelId = Optional.ofNullable(jObj.get("cp-model-id")).map(je -> stripQuotes(je.toString())).orElse(null);
        log.info("ControlServiceController.newAppModel():  App model id from request: {}", appModelId);
        log.info("ControlServiceController.newAppModel(): Exec model id from request: {}", appExecModelId);

        // Check parameters
        if (StringUtils.isBlank(appModelId)) {
            log.warn("ControlServiceController.newAppModel(): Request does not contain an app. model id");
            throw new RestControllerException(400, "Request does not contain an application id");
        }

        // Get applicationId (if provided)
        String applicationId = Optional.ofNullable(jObj.get("applicationId")).map(je -> stripQuotes(je.toString())).orElse(null);
        if (StringUtils.isBlank(applicationId)) {
            applicationId = appModelId;
            log.warn("ControlServiceController.newAppModel(): No 'applicationId' found. Using App model id instead: {}", applicationId);
        } else {
            log.info("ControlServiceController.newAppModel(): Found 'applicationId': {}", applicationId);
        }

        // Get app model from request (if provided)
        String appModel = Optional.ofNullable(jObj.get("app-model")).map(JsonElement::getAsString).orElse("");

        return Map.of("appModelId", StringUtils.defaultIfBlank(appModelId, ""),
                "appExecModelId", StringUtils.defaultIfBlank(appExecModelId, ""),
                "applicationId", StringUtils.defaultIfBlank(applicationId, ""),
                "appModel", StringUtils.defaultIfBlank(appModel, ""));
    }

    // ------------------------------------------------------------------------------------------------------------

    @PostMapping(value = {"/appExecModel", "/appExecModelJson"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public String newAppExecModel(@RequestBody String requestStr,
                             @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.debug("ControlServiceController.newAppExecModel(): Received request: {}", requestStr);
        log.trace("ControlServiceController.newAppExecModel(): JWT token: {}", jwtToken);

        // Use Gson to get model id's from request body (in JSON format)
        JsonObject jobj = new Gson().fromJson(requestStr, JsonObject.class);
        String appExecModelId = Optional.ofNullable(jobj.get("app-exec-model-id")).map(je -> stripQuotes(je.toString())).orElse(null);
        log.info("ControlServiceController.newAppExecModel(): App execution model id from request: {}", appExecModelId);

        // Check parameters
        if (StringUtils.isBlank(appExecModelId)) {
            log.warn("ControlServiceController.newAppExecModel(): Request does not contain an App execution model id");
            throw new RestControllerException(400, "Request does not contain an App execution model id");
        }

        // Start App Exec model processing in a worker thread
        coordinator.processAppExecModel(appExecModelId, ControlServiceRequestInfo.create(null, null, jwtToken));
        log.debug("ControlServiceController.newAppExecModel(): App Execution Model processing dispatched to a worker thread");

        return "OK";
    }

    @PostMapping(value = {"/cpConstants", "/appConstants"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public String setConstants(@RequestBody String requestStr,
                             @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.debug("ControlServiceController.setConstants(): Received request: {}", requestStr);
        log.trace("ControlServiceController.setConstants(): JWT token: {}", jwtToken);

        // Use Gson to get constants from request body (in JSON format)
        Type type = new TypeToken<Map<String,Double>>(){}.getType();
        Map<String, Double> constants = new Gson().fromJson(requestStr, type);
        log.info("ControlServiceController.setConstants(): Constants from request: {}", constants);

        // Start App Exec model processing in a worker thread
        coordinator.setConstants(constants, ControlServiceRequestInfo.create(null, null, jwtToken));
        log.debug("ControlServiceController.setConstants(): Constants set");

        return "OK";
    }

    @GetMapping(value = {"/cpConstants", "/appConstants"})
    public Map<String,Double> getConstants(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.debug("ControlServiceController.getConstants(): Received request");
        log.trace("ControlServiceController.getConstants(): JWT token: {}", jwtToken);

        Map<String,Double> constants = coordinator.getConstants(ControlServiceRequestInfo.create(null, null, jwtToken));
        log.info("ControlServiceController.getConstants(): Constants from Broker-CEP: {}", constants);

        return constants;
    }

    /*@RequestMapping(value = "/test/**", method = {GET, POST})
    public String testNotification(HttpServletRequest request, @RequestBody(required = false) String body,
                             @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        String path = request.getRequestURI().split("/test/", 2)[1];
        Map<String, String> headers = Collections.list(request.getHeaderNames()).stream()
                .collect(Collectors.toMap(h -> h, request::getHeader));
        log.warn("--------------  TEST endpoint: --------------------------------------------------------");
        log.warn("--------------  TEST endpoint: Verb/URL: {} {}", request.getMethod(), UriUtils.decode(path, StandardCharsets.UTF_8));
        log.warn("--------------  TEST endpoint:  headers: {}", headers);
        log.warn("--------------  TEST endpoint:     body: {}", body);
        log.warn("--------------  TEST endpoint:      JWT: {}", jwtToken);
        return "OK";
    }*/

    // ---------------------------------------------------------------------------------------------------
    // Translator results methods
    // ---------------------------------------------------------------------------------------------------

    @GetMapping(value = "/translator/currentAppModel")
    public String getCurrentAppModel(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.debug("ControlServiceController.getCurrentAppModel(): Received request");
        log.trace("ControlServiceController.getCurrentAppModel(): JWT token: {}", jwtToken);

        String currentAppModelId = coordinator.getCurrentAppModelId();
        log.info("ControlServiceController.getCurrentAppModel(): Current App model: {}", currentAppModelId);

        return currentAppModelId;
    }

    @GetMapping(value = "/translator/currentAppExecModel")
    public String getCurrentAppExecModel(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.debug("ControlServiceController.getCurrentAppExecModel(): Received request");
        log.trace("ControlServiceController.getCurrentAppExecModel(): JWT token: {}", jwtToken);

        String currentAppExecModelId = coordinator.getCurrentAppExecModelId();
        log.info("ControlServiceController.getCurrentAppExecModel(): Current App Exec model: {}", currentAppExecModelId);

        return currentAppExecModelId;
    }

    @GetMapping(value = "/translator/currentTranslationContext", produces = MediaType.APPLICATION_JSON_VALUE)
    public TranslationContext getCurrentTranslationContext(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.debug("ControlServiceController.getCurrentTranslationContext(): Received request");
        log.trace("ControlServiceController.getCurrentTranslationContext(): JWT token: {}", jwtToken);

        TranslationContext _TC = coordinator.getTranslationContextOfAppModel(coordinator.getCurrentAppModelId());
        log.info("ControlServiceController.getCurrentAppExecModel(): Current TC model: {}", _TC.getModelName());

        return _TC;
    }

    @PostMapping(value = "/translator/translate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> translateModel(@RequestBody String requestStr,
                                              @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken)
    {
        log.debug("ControlServiceController.translateModel(): Received request: {}", requestStr);
        log.trace("ControlServiceController.translateModel(): JWT token: {}", jwtToken);

        // Extract Ids and model from request
        Map<String, String> appIds = extractAppIds(requestStr);
        String appModelId = appIds.get("appModelId");
        String appExecModelId = appIds.get("appExecModelId");
        String applicationId = appIds.get("applicationId");
        String appModel = appIds.get("appModel");

        // Start translation
        AtomicReference<Object> state = new AtomicReference<>();
        TranslationContext _TC = coordinator.translateAppModel(appModelId, appModel,
                ControlServiceRequestInfo.create(applicationId, null, null, jwtToken, state::set));
        log.info("ControlServiceController.translateModel(): EMS State: {}", state.get());
        log.info("ControlServiceController.translateModel(): TC model: {}", _TC!=null ? _TC.getModelName() : null);
        log.debug("ControlServiceController.translateModel(): TC: {}", _TC);

        Map<String,Object> result = new LinkedHashMap<>((Map<String,Object>)state.get());
        if (_TC!=null) result.put("translationContext", _TC);
        return result;
    }

    // ---------------------------------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------------------------------

    protected String stripQuotes(String s) {
        return (s != null && s.startsWith("\"") && s.endsWith("\"")) ? s.substring(1, s.length() - 1) : s;
    }

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        RequestMappingHandlerMapping requestMappingHandlerMapping = applicationContext
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping
                .getHandlerMethods();
        //map.forEach((key, value) -> log.info("..... {} {}", key, value));

        controllerEndpoints = map.keySet().stream()
                .filter(Objects::nonNull)
                .map(RequestMappingInfo::getPatternValues)
                .flatMap(Set::stream)
                .collect(Collectors.toList());
        log.debug("ControlServiceController.handleContextRefresh: controller-endpoints: {}", controllerEndpoints);

        controllerEndpointsShort = controllerEndpoints.stream()
                .map(s -> s.startsWith("/") ? s.substring(1) : s)
                .map(s -> s.indexOf("/") > 0 ? s.split("/", 2)[0] + "/**" : s)
                .map(e -> "/" + e.replaceAll("\\{.*", "**"))
                .distinct()
                .collect(Collectors.toList());
        log.debug("ControlServiceController.handleContextRefresh: controller-endpoints-short: {}", controllerEndpointsShort);
    }
}
