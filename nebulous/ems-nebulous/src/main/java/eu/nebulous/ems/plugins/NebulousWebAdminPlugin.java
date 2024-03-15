/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.plugins;

import gr.iccs.imu.ems.control.plugin.WebAdminPlugin;
import org.springframework.stereotype.Service;

@Service
public class NebulousWebAdminPlugin implements WebAdminPlugin {
    public RestCallCommandGroup restCallCommands() {
        RestCallForm form = RestCallForm.builder()
                .id("load-app-model-form")
                .field( RestCallFormField.builder().name("application-id").text("Application Id").build() )
                .field( RestCallFormField.builder().name("application-name").text("Application Name").build() )
                .field( RestCallFormField.builder().name("model-path").text("Model Name").build() )
                .field( RestCallFormField.builder().name("model").text("Model").build() )
                .build();
        return RestCallCommandGroup.builder()
                .id("nebulous-group")
                .text("Nebulous-related API")
                .priority(0)
                .command(RestCallCommand.builder()
                        .id("load-model").text("Load App. Model")
                        .method("POST").url("/loadAppModel")
                        .form(form)
                        .priority(10).build())
                .build();
    }
}
