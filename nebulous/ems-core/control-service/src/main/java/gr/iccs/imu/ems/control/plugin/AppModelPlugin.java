/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.plugin;

import gr.iccs.imu.ems.control.controller.ControlServiceRequestInfo;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.util.Plugin;

/**
 * Executed before/after Application Model processing by ControlServiceCoordinator
 */
public interface AppModelPlugin extends Plugin {
    default void preProcessingNewAppModel(String appModelId, ControlServiceRequestInfo requestInfo) { }
    default void postProcessingNewAppModel(String appModelId, ControlServiceRequestInfo requestInfo, TranslationContext translationContext) { }
}
