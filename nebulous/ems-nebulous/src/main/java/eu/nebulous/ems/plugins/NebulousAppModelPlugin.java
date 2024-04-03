/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.plugins;

import gr.iccs.imu.ems.control.controller.ControlServiceRequestInfo;
import gr.iccs.imu.ems.control.plugin.AppModelPlugin;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NebulousAppModelPlugin implements AppModelPlugin {
    @Override
    public void preProcessingNewAppModel(String appModelId, ControlServiceRequestInfo requestInfo) {
        log.debug("NebulousAppModelPlugin: Nothing to do. Args: appModelId={}, requestInfo={}", appModelId, requestInfo);
    }

    @Override
    public void postProcessingNewAppModel(String appModelId, ControlServiceRequestInfo requestInfo, TranslationContext translationContext) {
        log.debug("NebulousAppModelPlugin: Nothing to do. Args: appModelId={}, requestInfo={}", appModelId, requestInfo);
    }
}
