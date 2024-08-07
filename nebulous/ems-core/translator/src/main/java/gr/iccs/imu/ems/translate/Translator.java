/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate;

import java.util.Map;

public interface Translator {
    TranslationContext translate(String modelPath);
    default TranslationContext translate(String modelPath, String applicationId) {
        return translate(modelPath);
    }
    default TranslationContext translate(String modelPath, String applicationId, Map<String,Object> additionalArguments) {
        return translate(modelPath);
    }

    default String getModel(String modelPath) {
        return null;
    }
    default String addModel(String modelPath, String modelStr) {
        return null;
    }
    default boolean removeModel(String modelPath) {
        return false;
    }
}