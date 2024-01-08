/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.plugin.noop;

import gr.iccs.imu.ems.control.plugin.PostTranslationPlugin;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import gr.iccs.imu.ems.translate.TranslationContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NoopPostTranslationPlugin implements PostTranslationPlugin {
    @PostConstruct
    public void created() {
        log.debug("NoopPostTranslationPlugin: CREATED");
    }

    @Override
    public void processTranslationResults(TranslationContext translationContext, TopicBeacon topicBeacon) {
        log.debug("NoopPostTranslationPlugin.processTranslationResults(): INVOKED");
    }
}
