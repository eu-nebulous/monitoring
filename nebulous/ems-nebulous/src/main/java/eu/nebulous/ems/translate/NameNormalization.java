/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class NameNormalization implements InitializingBean, Function<String,String> {
	private final NebulousEmsTranslatorProperties properties;

	@Override
	public void afterPropertiesSet() throws Exception {
		log.debug("NameNormalization: patterns: {}", properties.getNameNormalizationPatterns());
	}

	public String apply(String s) {
		if (properties.getNameNormalizationPatterns()==null) return s;
		final AtomicReference<String> ref = new AtomicReference<>(s);
		properties.getNameNormalizationPatterns().forEach((pattern,replacement) -> {
			ref.set( pattern.matcher(ref.get()).replaceAll(replacement) );
		});
		return ref.get();
	}
}