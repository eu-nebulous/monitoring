/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.broker;

import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.filter.DestinationMapEntry;
import org.apache.activemq.security.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple AMQ broker authorization plugin
 */
@Slf4j
public class SimpleBrokerAuthorizationPlugin implements BrokerPlugin {
    public final static String ADMIN_GROUP = "admins";
    public final static String RW_USER_GROUP = "users_RW";
    public final static String RO_USER_GROUP = "users_RO";
    public final static String ALL_GROUPS = RO_USER_GROUP+","+RW_USER_GROUP+","+ADMIN_GROUP;
    public final static String RWUSER_ADMIN_GROUPS = RW_USER_GROUP+","+ADMIN_GROUP;

    private AuthorizationMap map;

    public SimpleBrokerAuthorizationPlugin() {
        _prepareAuthorizationMap();
    }

    public Broker installPlugin(Broker broker) {
        if (map == null) {
            throw new IllegalArgumentException("You must configure a 'map' property");
        }
        return new AuthorizationBroker(broker, map);
    }

    public AuthorizationMap getMap() {
        return map;
    }

    public void setMap(AuthorizationMap map) {
        this.map = map;
    }

    private void _prepareAuthorizationMap() {
        try {
            // prepare authorization entry for 'ActiveMQ.Advisory' topics
            AuthorizationEntry mapEntry1 = new AuthorizationEntry();
            mapEntry1.setTopic("ActiveMQ.Advisory.>");
            mapEntry1.setRead(ALL_GROUPS);
            mapEntry1.setWrite(ALL_GROUPS);
            mapEntry1.setAdmin(ALL_GROUPS);

            // prepare authorization entry for all topics
            AuthorizationEntry mapEntry = new AuthorizationEntry();
            mapEntry.setTopic(">");
            mapEntry.setRead(ALL_GROUPS);
            mapEntry.setWrite(RWUSER_ADMIN_GROUPS);
            mapEntry.setAdmin(ADMIN_GROUP);

            // prepare authorization map entries
            List<DestinationMapEntry> mapEntries = new ArrayList<>();
            mapEntries.add(mapEntry1);
            mapEntries.add(mapEntry);

            // prepare authorization map
            DefaultAuthorizationMap defaultAuthorizationMap = new DefaultAuthorizationMap();
            defaultAuthorizationMap.setAuthorizationEntries(mapEntries);

            map = defaultAuthorizationMap;
        } catch (Exception ex) {
            log.error("BrokerConfig.SimpleAuthorizationPlugin._updateAuthorizationBroker(): EXCEPTION: ", ex);
            throw new RuntimeException(ex);
        }
    }
}
