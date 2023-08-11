/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.controller;

import gr.iccs.imu.ems.brokercep.EventCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BrokerCepController {

    private final EventCache eventCache;

    @RequestMapping(value = { "/brokercep/last-events/{howmany}", "/brokercep/last-events" }, method=GET)
    public Collection<EventCache.CacheEntry> getLastEvents(@PathVariable(required = false) Integer howmany) {
        log.info("BrokerCepController.getLastEvents(): howmany={}", howmany);

        List<EventCache.CacheEntry> cache = eventCache.asList();
        return howmany!=null && howmany >0 && howmany<cache.size()
                ? cache.stream().toList().subList(cache.size()-howmany, cache.size())
                : cache;
    }
}
