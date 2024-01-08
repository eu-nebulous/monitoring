/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@ConditionalOnMissingBean(FilesController.class)
public class FilesDisabledController {

    public FilesDisabledController() {
        log.info("FilesDisabledController: File browsing is disabled");
    }

    @GetMapping({"/files", "/files/**"})
    public ResponseEntity<String> filesDisabled(HttpServletRequest request) {
        log.debug("filesDisabled(): --- client: {}:{}", request.getRemoteAddr(), request.getRemotePort());
        return ResponseEntity.badRequest().body("File browsing is disabled");
    }
}
