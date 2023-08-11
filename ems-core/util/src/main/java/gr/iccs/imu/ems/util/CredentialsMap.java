/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import gr.iccs.imu.ems.util.password.PasswordEncoder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.stream.Collectors;

/**
 *  CredentialsMap is a HashMap for storing username/passwords in-memory.
 *  It includes a preferred key (i.e. username), and overrides 'toString()' method in order to password-encode entry values.
 */
@Slf4j
public class CredentialsMap extends HashMap<String,String> {
    @Getter
    private PasswordEncoder passwordEncoder;
    @Getter
    private String preferredKey;

    public CredentialsMap() {
        this(PasswordUtil.getDefaultPasswordEncoder());
    }

    public CredentialsMap(PasswordEncoder pe) {
        this.passwordEncoder = pe;
    }

    public String put(String key, String value, boolean preferred) {
        if (preferred) preferredKey = key;
        return super.put(key, value);
    }

    public String remove(String key) {
        if (key.equals(preferredKey)) preferredKey = null;
        return super.remove(key);
    }

    public boolean hasPreferredPair() {
        return preferredKey!=null;
    }

    public CredentialsMap.Entry<String, String> getPreferredPair() {
        if (preferredKey==null) return null;
        return new CredentialsMap.SimpleEntry<>(preferredKey, get(preferredKey));
    }

    public String toString() {
        return entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, e -> passwordEncoder.encode(e.getValue())))
                .toString();
    }
}
