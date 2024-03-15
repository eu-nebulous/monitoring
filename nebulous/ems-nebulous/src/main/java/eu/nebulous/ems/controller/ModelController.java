/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.controller;

import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.controller.ControlServiceRequestInfo;
import gr.iccs.imu.ems.control.controller.RestControllerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ModelController {

    private final NebulousEmsTranslatorProperties translatorProperties;
    private final ControlServiceCoordinator coordinator;

    @RequestMapping(value = "/loadAppModel", method = POST)
    public String loadAppModel(@RequestBody Map<String,String> request,
                              @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String jwtToken) throws IOException {
        log.debug("ModelController.loadAppModel(): Received request: {}", request);
        log.trace("ModelController.loadAppModel(): JWT token: {}", jwtToken);

        // Get information from request
        String applicationId = request.getOrDefault("application-id", "model-"+System.currentTimeMillis());
        String applicationName = request.getOrDefault("application-name", applicationId);
        String modelString = request.getOrDefault("model", null);
        if (StringUtils.isBlank(modelString))
            modelString = request.getOrDefault("body", null);
        String modelFile = request.getOrDefault("model-path", "").toString();
        log.info("ModelController.loadAppModel(): Request info: app-id={}, app-name={}, model-path={}, model={}",
                applicationId, applicationName, modelFile, modelString);

        // Check parameters
        if (StringUtils.isBlank(applicationId)) {
            log.warn("ModelController.loadAppModel(): Request does not contain an application id");
            throw new RestControllerException(400, "Request does not contain an application id");
        }
        if (StringUtils.isBlank(modelString)) {
            log.warn("ModelController.loadAppModel(): Request does not contain a model");
            throw new RestControllerException(400, "Request does not contain a model");
        }

        // Store model in the disk
        if (StringUtils.isNotBlank(modelString)) {
            modelFile = StringUtils.isBlank(modelFile) ? getModelFile(applicationId) : modelFile;
            storeModel(modelFile, modelString);
        }

        // Start translation and reconfiguration in a worker thread
        coordinator.processAppModel(modelFile, null,
                ControlServiceRequestInfo.create(applicationId, null, null, jwtToken, null));
        log.debug("ModelController.loadAppModel(): Model translation dispatched to a worker thread");

        return "OK";
    }

    private String getModelFile(String appId) {
        return String.format("model-%s--%d.yml", appId, System.currentTimeMillis());
    }

    private void storeModel(String fileName, String modelStr) throws IOException {
        Path path = Paths.get(translatorProperties.getModelsDir(), fileName);
        Files.writeString(path, modelStr);
        log.info("ModelController.storeModel(): Stored metric model in file: {}", path);
    }
}
