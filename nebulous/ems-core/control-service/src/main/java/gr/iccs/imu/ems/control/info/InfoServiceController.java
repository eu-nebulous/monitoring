/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

//XXX: TODO: Temporarily disabled logviewer: import com.logviewer.data2.LogFormat;
//XXX: TODO: Temporarily disabled logviewer: import com.logviewer.logLibs.LogConfigurationLoader;
//XXX: TODO: Temporarily disabled logviewer: import com.logviewer.springboot.LogViewerSpringBootConfig;
import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.controller.ManagementCoordinator;
import gr.iccs.imu.ems.control.plugin.WebAdminPlugin;
import gr.iccs.imu.ems.control.properties.InfoServiceProperties;
import gr.iccs.imu.ems.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.QueryParam;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
//XXX: TODO: Temporarily disabled logviewer: import org.springframework.context.annotation.Bean;
//XXX: TODO: Temporarily disabled logviewer: import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

//XXX: TODO: Temporarily disabled logviewer: import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
//XXX: TODO: Temporarily disabled logviewer: @Import(LogViewerSpringBootConfig.class)
public class InfoServiceController implements InitializingBean {

    private final InfoServiceProperties properties;
    private final ControlServiceCoordinator coordinator;
    private final ManagementCoordinator managementCoordinator;
    private final IEmsInfoService emsInfoService;
    private final List<WebAdminPlugin> webAdminPlugins;
    private List<Object> restCallCommands;
    private Map<String, Map<String, List<WebAdminPlugin.RestCallFormField>>> restCallForms;

    @Override
    public void afterPropertiesSet() throws Exception {
        initAdditionalRestCommands();
    }

    /*XXX: TODO: Temporarily disabled logviewer
    @Bean
    public LogConfigurationLoader getLogConfigurationLoader() {
        // Initialize Log-Viewer log paths
        List<Path> logPaths = properties.getLogViewerFiles();
        if (logPaths==null || logPaths.size()==0)
            return null;
        return () -> {
            LinkedHashMap<Path,LogFormat> logConf = new LinkedHashMap<>();
            logPaths.forEach(p -> logConf.put(p, null));
            log.info("LogConfigurationLoader: log-paths: {}", logConf);
            return logConf;
        };
    }*/

    @GetMapping("/info/metrics/get")
    public Mono<Map<String,Object>> serverMetricsGet(HttpServletRequest request, @AuthenticationPrincipal UserDetails user) {
        log.info("serverMetricsGet(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        Map<String,Object> message = createServerMetricsResult(null, -1L, user);
        log.debug("serverMetricsGet(): message={}", message);
        return Mono.just(message);
    }

    @GetMapping("/info/metrics/stream")
    public Flux<ServerSentEvent<Map<String,Object>>> serverMetricsStream(
            @QueryParam("interval") Optional<Integer> interval, HttpServletRequest request, @AuthenticationPrincipal UserDetails user)
    {
        String sid = UUID.randomUUID().toString();
        log.info("serverMetricsStream(): interval={} --- client: {}:{}, Stream-Id: {}",
                interval, request.getRemoteAddr(), request.getRemotePort(), sid);
        int intervalInSeconds = interval.orElse(-1);
        if (intervalInSeconds<1) intervalInSeconds = properties.getMetricsStreamUpdateInterval();
        log.debug("serverMetricsStream(): effective-interval={}", intervalInSeconds);

        return Flux.interval(Duration.ofSeconds(intervalInSeconds))
                .onBackpressureDrop()
                .map(sequence -> {
                    Map<String,Object> message = createServerMetricsResult(sid, sequence, user);
                    log.debug("serverMetricsStream(): seq={}, id={}, message={}", sequence, sid, message);
                    return ServerSentEvent.<Map<String,Object>> builder()
                            .id(String.valueOf(sequence))
                            .event(properties.getMetricsStreamEventName())
                            .data(message)
                            .build();
                });
    }

    @GetMapping("/info/metrics/clear")
    public String serverMetricsClear(HttpServletRequest request) {
        log.info("serverMetricsClear(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        emsInfoService.clearServerMetricValues();
        emsInfoService.clearClientMetricValues();
        return "CLEARED-SERVER-METRICS";
    }

    // ------------------------------------------------------------------------

    @GetMapping("/info/client-metrics/get/{clientIds}")
    public Mono<Map<String,Object>> clientMetricsGet(
            @PathVariable List<String> clientIds, HttpServletRequest request)
    {
        log.info("clientMetricsGet(): baguette-client-ids={} --- client: {}:{}", clientIds, request.getRemoteAddr(), request.getRemotePort());
        Map<String,Object> message = createClientMetricsResult(null, -1L, clientIds);
        log.debug("clientMetricsGet(): message={}", message);
        return Mono.just(message);
    }

    @GetMapping("/info/client-metrics/stream/{clientIds}")
    public Flux<ServerSentEvent<Map<String,Object>>> clientMetricsStream(
            @PathVariable List<String> clientIds,
            @QueryParam("interval") Integer interval,
            HttpServletRequest request)
    {
        String sid = UUID.randomUUID().toString();
        log.info("clientMetricsStream(): interval={}, baguette-client-ids={} --- client: {}:{}, Stream-Id: {}",
                interval, clientIds, request.getRemoteAddr(), request.getRemotePort(), sid);
        int intervalInSeconds = interval!=null ? interval : -1;
        if (intervalInSeconds<1) intervalInSeconds = properties.getMetricsStreamUpdateInterval();
        log.debug("clientMetricsStream(): effective-interval={}", intervalInSeconds);

        return Flux.interval(Duration.ofSeconds(intervalInSeconds))
                .onBackpressureDrop()
                .map(sequence -> {
                    Map<String,Object> message = createClientMetricsResult(sid, sequence, clientIds);
                    log.debug("clientMetricsStream(): seq={}, id={}, message={}", sequence, sid, message);
                    return ServerSentEvent.<Map<String,Object>> builder()
                            .id(String.valueOf(sequence))
                            .event(properties.getMetricsStreamEventName())
                            .data(message)
                            .build();
                });
    }

    @GetMapping("/info/client-metrics/clear/{clientIds}")
    public String clientMetricsClear(@PathVariable List<String> clientIds, HttpServletRequest request) {
        log.info("clientMetricsClear(): baguette-client-ids={} --- client: {}:{}",
                clientIds, request.getRemoteAddr(), request.getRemotePort());
        emsInfoService.clearClientMetricValues();
        return "CLEARED-CLIENT-METRICS";
    }

    // ------------------------------------------------------------------------

    @GetMapping("/info/all-metrics/get/{clientIds}")
    public Mono<Map<String,Object>> allMetricsGet(
            @PathVariable List<String> clientIds, HttpServletRequest request, @AuthenticationPrincipal UserDetails user)
    {
        log.info("allMetricsGet(): baguette-client-ids={} --- client: {}:{}",
                clientIds, request.getRemoteAddr(), request.getRemotePort());
        Map<String,Object> message1 = createServerMetricsResult(null, -1L, user);
        Map<String,Object> message2 = createClientMetricsResult(null, -1L, clientIds);
        Map<String,Object> message = new LinkedHashMap<>();
        message.put("ems", message1);
        message.put("clients", message2);
        log.debug("allMetricsGet(): message={}", message);
        return Mono.just(message);
    }

    @GetMapping("/info/all-metrics/stream/{clientIds}")
    public Flux<ServerSentEvent<Map<String,Object>>> allMetricsStream(
            @PathVariable List<String> clientIds,
            @QueryParam("interval") Integer interval,
            HttpServletRequest request,
            @AuthenticationPrincipal UserDetails user)
    {
        String sid = UUID.randomUUID().toString();
        log.info("allMetricsStream(): interval={}, baguette-client-ids={} --- client: {}:{}, Stream-Id: {}",
                interval, clientIds, request.getRemoteAddr(), request.getRemotePort(), sid);
        int intervalInSeconds = interval!=null ? interval : -1;
        if (intervalInSeconds<1) intervalInSeconds = properties.getMetricsStreamUpdateInterval();
        log.debug("allMetricsStream(): effective-interval={}", intervalInSeconds);

        return Flux.interval(Duration.ofSeconds(intervalInSeconds))
                .onBackpressureDrop()
                .map(sequence -> {
                    Map<String,Object> message1 = createServerMetricsResult(sid, sequence, user);
                    Map<String,Object> message2 = createClientMetricsResult(sid, sequence, clientIds);
                    Map<String,Object> message = new LinkedHashMap<>();
                    message.put("ems", message1);
                    message.put("clients", message2);
                    log.debug("allMetricsStream(): seq={}, id={}, message={}", sequence, sid, message);
                    return ServerSentEvent.<Map<String,Object>> builder()
                            .id(String.valueOf(sequence))
                            .event(properties.getMetricsStreamEventName())
                            .data(message)
                            .build();
                });
    }

    @GetMapping("/info/all-metrics/clear")
    public String allMetricsClear(HttpServletRequest request) {
        log.info("allMetricsClear(): client: {}:{}",
                request.getRemoteAddr(), request.getRemotePort());
        emsInfoService.clearServerMetricValues();
        emsInfoService.clearClientMetricValues();
        return "CLEARED-ALL-METRICS";
    }

    // ------------------------------------------------------------------------

    public Map<String,Object> createServerMetricsResult(String sid, long sequence, UserDetails userDetails) {
        log.trace("createServerMetricsResult: BEGIN: sid={}, seq={}", sid, sequence);
        Map<String, Object> metrics = new LinkedHashMap<>(emsInfoService.getServerMetricValues());

        addMetricsFromEnvVars(metrics);
        addAuthenticationInfo(metrics, userDetails);
        addRestCallCommands(metrics);

        metrics.put(".stream-id", sid);
        metrics.put(".sequence", sequence);
        log.trace("createMetricsResult: {}", metrics);
        log.trace("createServerMetricsResult: END: sid={}, seq={} ==> {}", sid, sequence, metrics);
        return metrics;
    }

    public Map<String,Object> createClientMetricsResult(String sid, long sequence, @NonNull List<String> clientIds) {
        log.trace("createClientMetricsResult: BEGIN: sid={}, seq={}, client-ids={}", sid, sequence, clientIds);
        Map<String, Object> metrics = emsInfoService.getClientMetricValues();
        log.trace("createClientMetricsResult: metrics: {}", metrics);
        if (metrics!=null && clientIds.size()>0 && !clientIds.contains("*")) {
            clientIds = clientIds.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(s->s.startsWith("#") ? s : "#"+s)
                    .collect(Collectors.toList());
            log.trace("createClientMetricsResult(): CLIENT-FILTER: PREPARE: client-ids: {}", clientIds);
            metrics = new LinkedHashMap<>(metrics);
            log.trace("createClientMetricsResult(): CLIENT-FILTER: BEFORE: metrics: {}", metrics);
            metrics.keySet().retainAll(clientIds);
            log.trace("createClientMetricsResult(): CLIENT-FILTER: AFTER: metrics: {}", metrics);
        }

        // Add client info in results
        Map<String, Map<String, String>> clientsInfo = managementCoordinator.clientMap();
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            Map<String, String> info = clientsInfo.get(entry.getKey());
            Object o = entry.getValue();
            if (o instanceof Map) {
                StrUtil.castToMapStringObject(o)
                        .put("client-info", info);
            }
        }

        Map<String,Object> clientMetrics = new LinkedHashMap<>();
        clientMetrics.put("client-metrics", metrics);
        clientMetrics.put(".stream-id", sid);
        clientMetrics.put(".sequence", sequence);
        log.trace("createClientMetricsResult: END: sid={}, seq={} ==> {}", sid, sequence, clientMetrics);
        return clientMetrics;
    }

    protected void addMetricsFromEnvVars(Map<String, Object> metrics) {
        // Process configured env. var. prefixes
        for (String prefix : properties.getEnvVarPrefixes()) {
            prefix = prefix.trim();
            if (StringUtils.isNotBlank(prefix)) {
                // Check for processing switches (at the end of the prefix)
                boolean trimPrefix = false;
                boolean underscoreToDash = false;
                boolean uppercase = false;
                boolean lowercase = false;
                int len = prefix.length();
                while (len>0) {
                    char ch = prefix.charAt(len-1);
                    if (ch=='/') { trimPrefix = true; len--; }
                    else if (ch=='-') { underscoreToDash = true; len--; }
                    else if (ch=='^') { uppercase = true; len--; }
                    else if (ch=='~') { lowercase = true; len--; }
                    else break;
                }

                // Check env. vars against the prefix (and its switches)
                if (len>0) {
                    if (prefix.length()!=len) prefix = prefix.substring(0, len);

                    final String _prefix = prefix;
                    final boolean _trimPrefix = trimPrefix;
                    final boolean _underscoreToDash = underscoreToDash;
                    final boolean _uppercase = uppercase;
                    final boolean _lowercase = lowercase;
                    System.getenv().forEach((varName,varValue) -> {
                        if (StringUtils.startsWithIgnoreCase(varName, _prefix)) {
                            // Process switches
                            String varNameOriginal = varName;
                            if (_trimPrefix) varName = varName.substring(_prefix.length());
                            if (_underscoreToDash) varName = varName.replace("_", "-");
                            if (_uppercase) varName = varName.toUpperCase();
                            if (_lowercase) varName = varName.toLowerCase();

                            // Add env. var. in the metrics map
                            log.debug("addMetricsFromEnvVars: Adding env. var. {} in metrics map as: {} = {}", varNameOriginal, varName, varValue);
                            metrics.put(varName, varValue);
                        }
                    });
                }
            }
        }
    }

    private void addAuthenticationInfo(Map<String, Object> metrics, UserDetails userDetails) {
        log.debug("addAuthenticationInfo: user-details: {}", userDetails);
        if (userDetails!=null && StringUtils.isNotBlank(userDetails.getUsername())) {
            String username = userDetails.getUsername();
            metrics.put(".authentication-username", username);
            log.debug("addAuthenticationInfo: Adding username from session: {}", username);
        }
    }

    private void initAdditionalRestCommands() {
        if (webAdminPlugins==null) return;
        final List<Object> commandGroups = new ArrayList<>();
        final Set<WebAdminPlugin.RestCallForm> formsSet = new HashSet<>();
        webAdminPlugins.stream().filter(Objects::nonNull).forEach(plugin->{
            WebAdminPlugin.RestCallCommandGroup commandGroup = plugin.restCallCommands();
            List<WebAdminPlugin.RestCallCommand> cmdList = commandGroup.getCommands();
            if (cmdList!=null && ! cmdList.isEmpty() && StringUtils.isNotBlank(commandGroup.getId())) {
                commandGroups.add( Map.of(
                        "id", commandGroup.getId(),
                        "text", commandGroup.getText(),
                        "priority", commandGroup.getPriority(),
                        "disabled", Boolean.toString(commandGroup.isDisabled()),
                        "options", cmdList.stream().filter(Objects::nonNull).map(cmd -> Map.of(
                                "id", cmd.getId(),
                                "text", cmd.getText(),
                                "url", cmd.getUrl(),
                                "method", cmd.getMethod(),
                                "form", (cmd.getForm() != null && StringUtils.isNotBlank(cmd.getForm().getId()))
                                        ? cmd.getForm().getId() : cmd.getFormId(),
                                "priority", Integer.toString(cmd.getPriority()),
                                "disabled", Boolean.toString(cmd.isDisabled())
                        )).toList()

                ) );
                formsSet.addAll( cmdList.stream()
                        .filter(Objects::nonNull)
                        .map(WebAdminPlugin.RestCallCommand::getForm)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                );
            }
        });
        restCallCommands = commandGroups;
        restCallForms = formsSet.stream().collect(Collectors.toMap(
                WebAdminPlugin.RestCallForm::getId,
                f -> Collections.singletonMap("fields", f.getFields())
        ));
    }

    private void addRestCallCommands(Map<String, Object> metrics) {
        log.debug("addRestCallCommands: rest-call-commands: {}", restCallCommands);
        log.debug("addRestCallCommands:    rest-call-forms: {}", restCallForms);
        if (restCallCommands!=null && restCallForms!=null) {
            metrics.put(".rest-call-commands", restCallCommands);
            metrics.put(".rest-call-forms", restCallForms);
            log.debug("addRestCallCommands: Added rest-call commands and forms");
        }
    }
}
