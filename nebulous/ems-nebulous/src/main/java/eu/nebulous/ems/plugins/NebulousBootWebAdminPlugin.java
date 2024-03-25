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

import java.util.Collection;
import java.util.Set;

@Service
public class NebulousBootWebAdminPlugin implements WebAdminPlugin {
    public RestCallCommandGroup restCallCommands() {
        RestCallForm form = RestCallForm.builder()
                .id("boot-app-id-form")
                .field( RestCallFormField.builder().name("application-id").text("Application Id").build() )
                .build();
        return RestCallCommandGroup.builder()
                .id("nebulous-boot-group")
                .text("Nebulous EMS Boot API")
                .priority(1)
                .commands( createCommands(form) )
                .build();
    }

    private Collection<? extends RestCallCommand> createCommands(RestCallForm form) {
        int i = 0;
        return Set.of(
                RestCallCommand.builder()
                        .id("boot-all").text("Index contents")
                        .method("GET").url("/boot/all")
                        .priority(i++).build(),
                RestCallCommand.builder()
                        .id("boot-list").text("App Ids")
                        .method("GET").url("/boot/list")
                        .priority(i++).build(),
                RestCallCommand.builder()
                        .id("boot-app-data").text("App Data")
                        .method("GET").url("/boot/get/{application-id}")
                        .form(form)
                        .priority(i++).build(),
                RestCallCommand.builder()
                        .id("boot-metric-model").text("App Metric Model")
                        .method("GET").url("/boot/get/{application-id}/metric-model")
                        .form(form)
                        .priority(i++).build(),
                RestCallCommand.builder()
                        .id("boot-bindings").text("App Bindings")
                        .method("GET").url("/boot/get/{application-id}/bindings")
                        .form(form)
                        .priority(i++).build(),
                RestCallCommand.builder()
                        .id("boot-delete-app").text("Delete App Data")
                        .method("DELETE").url("/boot/delete/{application-id}")
                        .form(form)
                        .priority(i++).build(),
                RestCallCommand.builder()
                        .id("boot-delete-all").text("Purge Index")
                        .method("DELETE").url("/boot/purge")
                        .priority(i++).build(),
                RestCallCommand.builder()
                        .id("boot-delete-all-with-files").text("Purge Index and files")
                        .method("DELETE").url("/boot/purge-with-files")
                        .priority(i++).build()
        );
    }
}
