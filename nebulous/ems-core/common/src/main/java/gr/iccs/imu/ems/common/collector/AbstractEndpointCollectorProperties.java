/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.collector;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

@Slf4j
@Data
public class AbstractEndpointCollectorProperties implements InitializingBean {
    private boolean enable;
    private long delay;
    private String url;
    private String urlOfNodesWithoutClient;
    private boolean skipLocal = false;
    private boolean createTopic;
    private List<String> allowedTopics;

    private int errorLimit;     // num of consecutive errors. Zero or negative value will immediately trigger self-healing

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("AbstractEndpointCollectorProperties: {}", this);
    }
}
