/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.plugin;

import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.util.Plugin;

import java.util.Map;
import java.util.Set;

/**
 * Executed during Metasolver configuration generation
 */
public interface MetasolverPlugin extends Plugin {
    default void topicsCollected(TranslationContext translationContext, Set<String> scalingTopics, Set<String> metricTopics) { }

    default void prepareSubscription(TranslationContext translationContext, Map<String, String> subscriptionConfigMap) { }

    default void mvvsCollected(TranslationContext translationContext, Map<String, String> mvvMap) { }
}
