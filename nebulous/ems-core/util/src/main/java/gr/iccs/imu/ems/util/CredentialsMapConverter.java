
/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import com.google.gson.Gson;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 *  Converts a String to a CredentialsMap
 */
@Slf4j
@Component
@ConfigurationPropertiesBinding
public class CredentialsMapConverter implements Converter<String, CredentialsMap> {
    private Gson gson;

    public CredentialsMapConverter() {
        gson = new Gson();
    }

    @Override
    public CredentialsMap convert(@NonNull String s) {
        if (StringUtils.isNotBlank(s)) {
            try {
                CredentialsMap credentialsMap = gson.fromJson(s.trim(), CredentialsMap.class);
                log.debug("CredentialsMapConverter: result: {}", credentialsMap);
                return credentialsMap;
            } catch (Throwable t) {
                log.debug("CredentialsMapConverter: JSON input: {}", s);
                log.error("CredentialsMapConverter: EXCEPTION while parsing JSON input: ", t);
                throw new IllegalArgumentException(t);
            }
        }
        throw new IllegalArgumentException("Input is blank: "+s);
    }
}
