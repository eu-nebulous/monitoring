/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util;

import gr.iccs.imu.ems.control.controller.ControlServiceCoordinator;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.TranslationContextProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationContextProviderHelper implements InitializingBean, TranslationContextProvider {
    private final ControlServiceCoordinator coordinator;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("TranslationContextProviderHelper: Initialized");
    }

    @Override
    public TranslationContext getDefaultTranslationContext() {
        log.debug("TranslationContextProviderHelper.getDefaultTranslationContext: BEGIN");
        String id = coordinator.getCurrentAppModelId();
        log.debug("TranslationContextProviderHelper.getDefaultTranslationContext: Default TC Id: {}", id);
        TranslationContext translationContext = coordinator.getTranslationContextOfAppModel(id);
        log.trace("TranslationContextProviderHelper.getDefaultTranslationContext: END: {}", translationContext);
        return translationContext;
    }

    @Override
    public TranslationContext getTranslationContext(String id) {
        log.debug("TranslationContextProviderHelper.getTranslationContext: BEGIN: id: {}", id);
        TranslationContext translationContext = coordinator.getTranslationContextOfAppModel(id);
        log.trace("TranslationContextProviderHelper.getTranslationContext: END: {}", translationContext);
        return translationContext;
    }
}
