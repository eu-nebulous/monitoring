/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.plugin;

import gr.iccs.imu.ems.util.Plugin;

import java.util.Map;

/**
 * Executed right before application model translation, to modify/convert/preprocess the given model.
 * When TranslationContext is loaded from TC file PostTranslationPlugin plugins are NOT executed.
 */
public interface PreTranslationPlugin extends Plugin {
    /**
     * Call to preprocessModel() can either modify the given model file in-place or create a new
     * model file with the modified model version.
     *
     * @param modelPath
     * @param applicationId
     * @param additionalArguments
     * @return Returns the path to a modified model version or null if no new model file is created
     */
    String preprocessModel(String modelPath, String applicationId, Map<String,Object> additionalArguments);
}
