/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
abstract class AbstractHelper {
    protected MetricModelAnalyzer.AdditionalTranslationContextData $$(TranslationContext _TC) {
        return _TC.$(MetricModelAnalyzer.AdditionalTranslationContextData.class);
    }

    void reset() {
        // Invoked at the beginning of 'MetricModelAnalyzer.analyzeModel()' to reset helpers
        // Subclasses may override this
    }
}