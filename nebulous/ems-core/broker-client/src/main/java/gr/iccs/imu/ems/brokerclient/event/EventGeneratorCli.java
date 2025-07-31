/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokerclient.event;

import com.google.gson.GsonBuilder;
import gr.iccs.imu.ems.brokerclient.BrokerClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
@Component
public class EventGeneratorCli {
    public void runCli(BrokerClient client, String initialTopic) {
        runCli(client, null, initialTopic);
    }

    public void runCli(BrokerClient client, String prompt, String topic) {
        if (client==null || ! client.isConnected())
            throw new IllegalArgumentException("A connected BrokerClient must be provided: "+client);
        if (prompt==null)
            prompt = "GEN> ";
        String type = "text";

        long interval = 1000;
        long howmany = 10;
        double lowerValue = 0;
        double upperValue = 100;
        int level = 1;

        Map<String, String> props = new HashMap<>();

        EventGenerator generator = new EventGenerator(client);
        generator.setBrokerUrl(client.getBrokerUrl());
        generator.setBrokerUsername(client.getBrokerUsername());
        generator.setDestinationName(topic);
        generator.setEventType(type);
        generator.setInterval(interval);
        generator.setHowMany(howmany);
        generator.setLowerValue(lowerValue);
        generator.setUpperValue(upperValue);
        generator.setLevel(level);
        generator.setEventProperties(props);

        InputStream in = System.in;
        BufferedReader rIn = new BufferedReader(new InputStreamReader(in));
        PrintStream out = System.out;
        PrintStream err = System.err;
        boolean keepRunning = true;
        while (keepRunning) {
            try {
                out.print(prompt);
                out.flush();
                String line = rIn.readLine();

                if (StringUtils.isBlank(line)) continue;
                String[] cmd = line.trim().split("[ \t\r\n]+");

                switch (cmd[0].toLowerCase()) {
                    case "info" ->
                            out.format("""
                                    Broker:  %s
                                    User:    %s
                                    Status:  %s
                                    Topic:   %s
                                    Type:    %s
                                    Lower:   %f
                                    Upper:   %f
                                    Level:   %d
                                    Props:   %s
                                    Delay:   %d
                                    Limit:   %d
                                    Count:   %d
                                    Retries: %d
                                    """,
                                    generator.getBrokerUrl(),
                                    generator.getBrokerUsername(),
                                    generator.isKeepRunning() ? "Running"
                                            : generator.isPaused() ? "Paused"
                                            : "Idle",
                                    generator.getDestinationName(),
                                    generator.getEventType(),
                                    generator.getLowerValue(),
                                    generator.getUpperValue(),
                                    generator.getLevel(),
                                    generator.getEventProperties().toString(),
                                    generator.getInterval(),
                                    generator.getHowMany(),
                                    generator.getCountSent(),
                                    generator.getRetriesLimit()
                            );
                    case "start" ->
                            generator.start();
                    case "stop" ->
                            generator.stop();
                    case "pause" ->
                            generator.pause();
                    case "resume" ->
                            generator.resume();
                    case "value" -> {
                        if (cmd.length<2)
                            out.println("  [ "+generator.getLowerValue()+" .. "+generator.getUpperValue()+" ]");
                        else {
                            double v = Double.parseDouble(requireNonBlank(cmd[1]));
                            generator.setLowerValue(v);
                            generator.setUpperValue(v);
                        }
                    }
                    case "lower" -> {
                        if (cmd.length < 2)
                            out.println("  " + generator.getLowerValue());
                        else
                            generator.setLowerValue(Double.parseDouble(requireNonBlank(cmd[1])));
                    }
                    case "upper" -> {
                        if (cmd.length < 2)
                            out.println("  " + generator.getUpperValue());
                        else
                            generator.setUpperValue(Double.parseDouble(requireNonBlank(cmd[1])));
                    }
                    case "level" -> {
                        if (cmd.length < 2)
                            out.println("  " + generator.getLevel());
                        else
                            generator.setLevel(Integer.parseInt(requireNonBlank(cmd[1])));
                    }
                    case "property" -> {
                        String subCmd = requireNonBlank(cmd[1]).trim().toLowerCase();
                        switch (subCmd) {
                            case "ls" ->
                                    generator.getEventProperties().forEach((k,v) -> out.format("  %s = %s\n", k, v));
                            case "get" -> {
                                String prop = requireNonBlank(cmd[2]).trim();
                                if (StringUtils.isNotBlank(prop)) {
                                    if (generator.getEventProperties().containsKey(prop)) {
                                        String value = generator.getEventProperties().get(prop);
                                        out.println("  " + prop + " = " + value);
                                    } else {
                                        out.println("  Not found property: " + prop);
                                    }
                                }
                            }
                            case "set" -> {
                                String prop = requireNonBlank(cmd[2]).trim();
                                if (StringUtils.isNotBlank(prop)) {
                                    String value = requireNonBlank(cmd[3]);
                                    String oldValue = generator.getEventProperties().put(prop, value);
                                    out.println("  "+prop+" = "+oldValue+" -> "+value);
                                }
                            }
                            case "del" -> {
                                String prop = requireNonBlank(cmd[2]).trim();
                                if (StringUtils.isNotBlank(prop)) {
                                    String value = generator.getEventProperties().remove(prop);
                                    out.println("  UNSET "+prop+" = "+value);
                                }
                            }
                            default ->
                                    err.println("Unknown property sub-command: "+subCmd);
                        }
                    }
                    case "interval" -> {
                        if (cmd.length < 2)
                            out.println("  " + generator.getInterval());
                        else
                            generator.setInterval(Long.parseLong(requireNonBlank(cmd[1])));
                    }
                    case "limit" -> {
                        if (cmd.length < 2)
                            out.println("  " + generator.getHowMany());
                        else
                            generator.setHowMany(Long.parseLong(requireNonBlank(cmd[1])));
                    }
                    case "count" ->
                            out.println("Current count: "+generator.getCountSent());
                    case "retries" -> {
                        if (cmd.length < 2)
                            out.println("  " + generator.getRetriesLimit());
                        else
                            generator.setRetriesLimit(Integer.parseInt(requireNonBlank(cmd[1])));
                    }
                    case "topic" -> {
                        if (cmd.length < 2)
                            out.println("  " + generator.getDestinationName());
                        else
                            generator.setDestinationName(requireNonBlank(cmd[1]));
                    }
                    case "type" -> {
                        if (cmd.length < 2)
                            out.println("  " + generator.getEventType());
                        else
                            generator.setEventType(requireNonBlank(cmd[1]));
                    }
                    case "list", "topics-list" -> {
                        client.getDestinationNames(null).forEach(s -> out.println("  "+s));
                    }
                    case "client", "client-info" -> {
                        out.println("Broker Url:       "+client.getBrokerUrl());
                        out.println("Broker Username:  "+client.getBrokerUsername());
                        out.println("Client connected: "+client.isConnected());
                        out.println("Client properties:\n"
                                + new GsonBuilder().setPrettyPrinting().create()
                                            .toJson(client.getClientProperties()));
                    }
                    case "help", "?" ->
                            out.println("""
                                    Commands + Arguments      Description
                                    --------------------      ----------------------------------------
                                    info                      Print current generator status
                                    start                     Start event generator
                                    stop                      Stop event generator
                                    pause                     Pause event generator
                                    resume                    Resume event generator
                                    value <double>            Get/Set one value (lower = upper)
                                    lower <double>            Get/Set lower value bound
                                    upper <double>            Get/Set upper value bound
                                    level <1 or 2 or 3>       Get/Set event level
                                    property ....
                                      ls                      List all properties
                                      get <property>          Get property value
                                      set <property> <value>  Set property value
                                      del <property>          Unset property
                                    interval <positive long>  Get/Set delay between event sends in millis
                                    limit <positive long>     Get/Set how many events to send
                                    count                     Current number of sent events
                                    retries                   Get/Set send retries limit
                                    topic <URL>               Get/Set the topic to send events
                                    type  <type>              Get/Set event type: text, map, object, bytes
                                    topics-list, list         List topics at broker
                                    client-info, client       Print client status and configuration
                                    help, ?                   This help page
                                    exit                      Exit generator CLI
                                    """);
                    case "exit" ->
                            keepRunning = false;
                    default ->
                            err.println("Unknown command: "+cmd[0]);
                }
                out.flush();
                err.flush();
            } catch (Exception ex) {
                ex.printStackTrace(err);
            }
        }
    }

    private static String requireNonBlank(String s) {
        if (StringUtils.isBlank(s))
            throw new IllegalArgumentException("Argument must not be null or blank");
        return s;
    }
}