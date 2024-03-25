/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.plugin;

import gr.iccs.imu.ems.util.Plugin;
import lombok.*;

import java.util.List;

/**
 * WebAdmin plugin
 */
public interface WebAdminPlugin extends Plugin {
    RestCallCommandGroup restCallCommands();

    @Getter
    @Builder
    @AllArgsConstructor
    class RestCallCommandGroup {
        @NonNull
        private String id;
        @NonNull
        private String text;
        private int priority;
        private boolean disabled;
        @Singular
        private List<RestCallCommand> commands;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    class RestCallCommand {
        @NonNull
        private String id;
        @NonNull
        private String text;
        @NonNull
        private String url;
        @Builder.Default
        private String method = "GET";
        @Builder.Default
        private String formId = RestCallForm.EMPTY_FORM.id;
        @Builder.Default
        private RestCallForm form = RestCallForm.EMPTY_FORM;
        private int priority;
        private boolean disabled;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    class RestCallForm {
        public final static RestCallForm EMPTY_FORM = new RestCallForm("_EMPTY_FORM_", List.of());

        @NonNull
        private String id;
        @Singular
        private List<RestCallFormField> fields;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    class RestCallFormField {
        @NonNull
        private String name;
        @NonNull
        private String text;
        private String defaultValue;
        private boolean function;
    }
}
