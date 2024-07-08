/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.controller;

import eu.nebulous.ems.boot.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/boot")
@RequiredArgsConstructor
public class BootController {
    private final IndexService indexService;

    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Map<String, String>> all(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.all(): Received request");
        log.trace("BootController.all(): JWT token: {}", jwtToken);

        return indexService.getAll();
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public Set<String> list(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.list(): Received request");
        log.trace("BootController.list(): JWT token: {}", jwtToken);

        return indexService.getAppIds();
    }

    @GetMapping(value = "/get/{appId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getByAppId(@PathVariable("appId") String appId, @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.getByAppId(): Received request: app-id={}", appId);
        log.trace("BootController.getByAppId(): JWT token: {}", jwtToken);

        return indexService.getAppData(appId);
    }

    @GetMapping(value = "/get/{appId}/metric-model", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getMetricModelByAppId(@PathVariable("appId") String appId, @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.getMetricModelByAppId(): Received request: app-id={}", appId);
        log.trace("BootController.getMetricModelByAppId(): JWT token: {}", jwtToken);

        return Arrays.asList(indexService.getAppMetricModel(appId).split("\n"));
    }

    @GetMapping(value = "/get/{appId}/bindings", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getBindingsByAppId(@PathVariable("appId") String appId, @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.getBindingsByAppId(): Received request: app-id={}", appId);
        log.trace("BootController.getBindingsByAppId(): JWT token: {}", jwtToken);

        return indexService.getAppBindings(appId);
    }

    @DeleteMapping(value = "/delete/{appId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean deleteByAppId(@PathVariable("appId") String appId, @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.deleteByAppId(): Received request: app-id={}", appId);
        log.trace("BootController.deleteByAppId(): JWT token: {}", jwtToken);

        return indexService.deleteAppData(appId);
    }

    @DeleteMapping(value = "/purge", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean purge(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.purge(): Received request");
        log.trace("BootController.purge(): JWT token: {}", jwtToken);

        return indexService.deleteAll();
    }

    @DeleteMapping(value = "/purge-with-files", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean purgeFiles(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.purgeFiles(): Received request");
        log.trace("BootController.purgeFiles(): JWT token: {}", jwtToken);

        return indexService.deleteAll(true);
    }

    @DeleteMapping(value = "/purge-unused", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean purgeUnusedFiles(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("BootController.purgeUnusedFiles(): Received request");
        log.trace("BootController.purgeUnusedFiles(): JWT token: {}", jwtToken);

        return indexService.deleteUnused();
    }

    @DeleteMapping(value = "/purge-older-than/{before}/{deleteFiles}", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean purgeAppsOlderThan(@PathVariable("before") String before,
                                      @PathVariable("deleteFiles") boolean deleteFiles,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException
    {
        log.debug("BootController.purgeAppsOlderThan(): Received request");
        log.debug("BootController.purgeAppsOlderThan(): Args: before={}, deleteFiles={}", before, deleteFiles);
        log.trace("BootController.purgeAppsOlderThan(): JWT token: {}", jwtToken);

        before = before.trim();
        Instant beforeInstant;
        if (StringUtils.isNumeric(before)) {
            if (before.length()>10)
                beforeInstant = Instant.ofEpochMilli(Long.parseLong(before));
            else
                beforeInstant = Instant.ofEpochSecond(Long.parseLong(before));
        } else {
            beforeInstant = Instant.parse(before);
        }
        log.debug("BootController.purgeAppsOlderThan(): before-instant: {}", beforeInstant);

        return indexService.deleteAppsBefore(beforeInstant, deleteFiles);
    }

    @PutMapping(value = "/add-app/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> addApp(@PathVariable("id") String id,
                                      @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException
    {
        log.debug("BootController.addApp(): Received request");
        log.trace("BootController.addApp(): Args: appId={}", id);
        log.trace("BootController.addApp(): JWT token: {}", jwtToken);

        indexService.addAppData(id);
        Map<String, String> data = indexService.getAppData(id);
        log.debug("BootController.purgeAppsOlderThan(): Result: {}", data);
        return data;
    }
}
