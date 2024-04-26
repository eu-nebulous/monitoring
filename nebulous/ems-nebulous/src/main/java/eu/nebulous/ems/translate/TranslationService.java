/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate;

import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.control.controller.ControlServiceRequestInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {
	private final ControlServiceCoordinator controlServiceCoordinator;
	private final NebulousEmsTranslator translator;

    public boolean storeModel(String appId, String modelFile, String modelStr) {
		// If 'model' string is provided, store it in a file
		if (StringUtils.isNotBlank(modelStr)) {
			// Store metric model in a new file
			translator.addModel(modelFile, modelStr);
			log.info("Stored metric model in file: app-id={}, file={}", appId, modelFile);
			return true;
		} else {
			log.warn("Metric Model is empty");
			return false;
		}
	}

    public String translateModel(String appId, String modelFile, String modelStr) {
        // If 'model' string is provided, store it in a file
		if (StringUtils.isNotBlank(modelStr)) {
			// Store metric model in a new file
			translator.addModel(modelFile, modelStr);
			log.info("Stored metric model in file: app-id={}, file={}", appId, modelFile);

			// Validate metric model
			boolean valid;
			ArrayList<String> errors = new ArrayList<>();
			try {
				log.info("Validating metric model in file: app-id={}, file={}", appId, modelFile);
				valid = (controlServiceCoordinator.translateAppModel(modelFile, null, ControlServiceRequestInfo.EMPTY) != null);
				log.info("Valid metric model in file: app-id={}, file={}", appId, modelFile);
			} catch (Throwable t) {
				log.warn("Error while validating metric model in file: app-id={}, file={}\n", appId, modelFile, t);
				valid = false;
				Throwable tmp = t;
				while (tmp!=null) {
					errors.add(tmp.getMessage());
					tmp = tmp.getCause();
				}
			}

			return valid ? "OK" : "ERROR: "+String.join(" | ", errors);
		} else {
			log.warn("Metric Model is empty");
			return "ERROR: Metric Model is empty";
		}
	}
}