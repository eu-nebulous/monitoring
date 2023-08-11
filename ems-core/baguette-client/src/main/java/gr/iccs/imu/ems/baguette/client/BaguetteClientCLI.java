/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client;

import gr.iccs.imu.ems.baguette.client.cluster.ClusterManager;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;

/**
 * Baguette Client Command-Line Interface
 */
@Slf4j
@Service
public class BaguetteClientCLI {
    private BaguetteClientProperties config;
    private String clientId;
    private String prompt = "CLI> ";

    @Autowired
    private CommandExecutor commandExecutor;
    @Autowired
    BrokerCepService brokerCepService;

    public void setConfiguration(BaguetteClientProperties config) {
        this.config = config;
        this.clientId = config.getClientId();
        if (StringUtils.isNotBlank(clientId))
            prompt = "CLI-"+ ClusterManager.getLocalHostName()+" > ";
        config.setExitCommandAllowed(true);
        log.trace("Sshc: cmd-exec: {}", commandExecutor);
        this.commandExecutor.setConfiguration(config);
    }

    public void run() throws IOException {
        run(System.in, System.out, System.err);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        out.print(prompt);
        out.flush();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            try {
                boolean exit = commandExecutor.execCmd(line.split("[ \t]+"), in, out, err);
                if (exit) break;
            } catch (Exception ex) {
                ex.printStackTrace(out);
                out.flush();
            }
            out.print(prompt);
            out.flush();
        }
    }
}
