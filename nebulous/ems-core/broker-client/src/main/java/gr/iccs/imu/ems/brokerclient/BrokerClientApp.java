/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokerclient;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.google.gson.Gson;
import gr.iccs.imu.ems.brokerclient.event.EventGenerator;
import gr.iccs.imu.ems.brokerclient.event.EventGeneratorCli;
import gr.iccs.imu.ems.brokerclient.event.EventMap;
import gr.iccs.imu.ems.util.LogsUtil;
import jakarta.jms.*;
import jakarta.jms.Queue;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class BrokerClientApp {

    private static boolean filterAMQMessages = true;
    private static boolean isRecording = false;
    private static boolean autoReconnect = false;
    private static File recordFile;
    private static Writer recordWriter;
    private static RECORD_FORMAT recordFormat;
    private static CSVPrinter csvPrinter;
    private static JsonGenerator jsonGenerator;
    private static long playbackInterval = -1;
    private static long playbackDelay = -1;
    private static double playbackSpeed = 1.0;
    private static Gson gson = new Gson();
    private static boolean printAsJson = true;

    private enum RECORD_FORMAT { CSV, JSON }

    public static void main(String args[]) throws java.io.IOException, JMSException, ScriptException {
        log.info("Broker Client for EMS, v.{}", BrokerClientApp.class.getPackage().getImplementationVersion());
        if (args.length==0 || "help".equalsIgnoreCase(args[0])) {
            usage(args);
            return;
        }

        int aa=0;
        String command = args[aa++];

        String logLevel = (args.length>aa && args[aa].startsWith("-LL"))
                ? args[aa++].substring(3).trim() : null;
        if (StringUtils.isNotBlank(logLevel))
            LogsUtil.setLogLevel(BrokerClientApp.class.getPackage().getName(), logLevel);

        filterAMQMessages = args.length>aa && args[aa].startsWith("-Q") ? false : true;
        if (!filterAMQMessages) aa++;

        String destinationFilters = (args.length>aa && args[aa].startsWith("-FD"))
                ? args[aa++].substring(3).trim() : null;
        String propertyFilters = (args.length>aa && args[aa].startsWith("-FP"))
                ? args[aa++].substring(3).trim() : null;

        printAsJson = args.length>aa && args[aa].equals("-NPJ") ? false : true;
        if (!printAsJson) aa++;

        autoReconnect = args.length>aa && args[aa].equals("-AR");
        if (autoReconnect) aa++;

        String username = args.length>aa && args[aa].startsWith("-U") ? args[aa++].substring(2) : null;
        String password = username!=null && args.length>aa && args[aa].startsWith("-P") ? args[aa++].substring(2) : null;
        if (StringUtils.isNotBlank(username) && password == null) {
            password = new String(System.console().readPassword("Enter broker password: "));
        }

        // Pre-process Record commands
        if ("record".equalsIgnoreCase(command)) {
            isRecording = true;
            command = "receive";
        }
        if ("s_record".equalsIgnoreCase(command)) {
            isRecording = true;
            command = "subscribe";
        }

        // list destinations
        if ("list".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            log.info("BrokerClientApp: Listing destinations:");
            BrokerClient client = BrokerClient.newClient(username, password);
            client.getDestinationNames(url).forEach(d -> log.info("    {}", d));
        } else
        // send an event
        if ("publish".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            String topic = args[aa++];
            String type = args[aa].startsWith("-T") ? args[aa++].substring(2) : "text";
            String value = args[aa++];
            String level = args[aa++];
            EventMap event = new EventMap(Double.parseDouble(value), Integer.parseInt(level), System.currentTimeMillis());
            sendEvent(url, username, password, topic, type, event, collectProperties(args, aa));
        } else
        if ("publish2".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            String topic = args[aa++];
            String type = args[aa].startsWith("-T") ? args[aa++].substring(2) : "text";
            boolean processPlaceholders = !args[aa].startsWith("-PP") || Boolean.parseBoolean(args[aa++].substring(3));
            String payload = args[aa++];
            payload = getPayload(payload, processPlaceholders);
            EventMap event = gson.fromJson(payload, EventMap.class);
            sendEvent(url, username, password, topic, type, event, collectProperties(args, aa));
        } else
        if ("publish3".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            String topic = args[aa++];
            String type = args[aa].startsWith("-T") ? args[aa++].substring(2) : "text";
            boolean processPlaceholders = !args[aa].startsWith("-PP") || Boolean.parseBoolean(args[aa++].substring(3));
            String payload = args[aa++];
            payload = getPayload(payload, processPlaceholders);
            Map<String, String> properties = collectProperties(args, aa);
            if ("map".equalsIgnoreCase(type)) {
                EventMap event = gson.fromJson(payload, EventMap.class);
                sendEvent(url, username, password, topic, type, event, properties);
            } else {
                sendEvent(url, username, password, topic, type, payload, properties);
            }
        } else
        // receive events from topic
        if ("receive".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            String topic = args[aa++];

            if (isRecording)
                initRecording(args, aa);

            BrokerClient.ON_EXCEPTION onException = (args.length>aa && args[aa].startsWith("-OE"))
                    ? BrokerClient.ON_EXCEPTION.valueOf(args[aa++].substring(3))
                    : BrokerClient.ON_EXCEPTION.LOG_AND_IGNORE;

            log.debug("BrokerClientApp: Subscribing to topic: {}", topic);
            log.debug("BrokerClientApp: on-exception setting: {}", onException);
            BrokerClient client = BrokerClient.newClient(username, password);
            if (! autoReconnect)
                client.receiveEvents(url, topic, getMessageListener(destinationFilters, propertyFilters), onException);
            else
                client.receiveEventsWithAutoReconnect(url, topic, getMessageListener(destinationFilters, propertyFilters), onException);
        } else
        // playback events
        if ("playback".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            initPlayback(args, aa);
            playbackEvents(url, username, password);
        } else
        // subscribe to topic
        if ("subscribe".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            String topic = args[aa++];

            if (isRecording)
                initRecording(args, aa);

            BrokerClient.ON_EXCEPTION onException = (args.length>aa && args[aa].startsWith("-OE"))
                    ? BrokerClient.ON_EXCEPTION.valueOf(args[aa++].substring(3))
                    : BrokerClient.ON_EXCEPTION.LOG_AND_IGNORE;

            log.debug("BrokerClientApp: Receiving from topic: {}", topic);
            log.debug("BrokerClientApp: On-exception setting: {}", onException);
            BrokerClient client = BrokerClient.newClient(username, password);
            MessageListener listener;
            if (!autoReconnect) {
                client.subscribe(url, topic, listener = getMessageListener(destinationFilters, propertyFilters), onException);
            } else {
                client.subscribeWithAutoReconnect(url, topic, listener = getMessageListener(destinationFilters, propertyFilters), onException, (exitCode) -> {
                    log.debug("BrokerClientApp: Exit with code {}", exitCode);
                    System.exit(exitCode);
                });
            }

            log.info("BrokerClientApp: Hit ENTER to exit");
            try {
                System.in.read();
            } catch (Exception ignored) {}
            log.debug("BrokerClientApp: Closing connection...");

            client.stopRunning(true, 5000L);
            client.unsubscribe(listener);
            client.closeConnection();
            log.debug("BrokerClientApp: Exiting...");
            System.exit(0);

        } else
        // start event generator
        if ("generator".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            String topic = args[aa++];
            String type = args[aa].startsWith("-T") ? args[aa++].substring(2) : "text";
            long interval = Long.parseLong(args[aa++]);
            long howmany = Long.parseLong(args[aa++]);
            double lowerValue = Double.parseDouble(args[aa++]);
            double upperValue = Double.parseDouble(args[aa++]);
            int level = Integer.parseInt(args[aa++]);
            Map<String, String> props = collectProperties(args, aa);

            BrokerClient client = BrokerClient.newClient();
            client.openConnection(url, username, password, true);
            EventGenerator generator = new EventGenerator(client);
            //generator.setClient(client);
            generator.setBrokerUrl(url);
            generator.setDestinationName(topic);
            generator.setEventType(type);
            generator.setInterval(interval);
            generator.setHowMany(howmany);
            generator.setValues(lowerValue, upperValue);
            generator.setLevel(level);
            generator.setEventProperties(props);
            generator.run();
            client.closeConnection();
        } else
        // Run generator CLI
        if ("generator-cli".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            String initialTopic = args[aa++];

            BrokerClient client = BrokerClient.newClient();
            client.openConnection(url, username, password, true);

            new EventGeneratorCli(client, initialTopic).runCli();
            client.closeConnection();
        } else
        // Run generator CLI with Remote Control
        if ("generator-rc".equalsIgnoreCase(command)) {
            String url = processUrlArg( args[aa++] );
            String rcTopic = args[aa++];

            BrokerClient.ON_EXCEPTION onException = (args.length>aa && args[aa].startsWith("-OE"))
                    ? BrokerClient.ON_EXCEPTION.valueOf(args[aa++].substring(3))
                    : BrokerClient.ON_EXCEPTION.LOG_AND_IGNORE;

            log.debug("BrokerClientApp: Receiving from remote control topic: {}", rcTopic);
            log.debug("BrokerClientApp: On-exception setting: {}", onException);

            // Connect to message broker
            BrokerClient client = BrokerClient.newClient(username, password);
            client.openConnection(url, username, password, true);

            // Initialize Generator CLI and subscribe to remote control topic
            final Lock lock = new ReentrantLock();
            final Condition condition = lock.newCondition();
            EventGeneratorCli cli = new EventGeneratorCli(client, null);
            MessageListener messageListener = message -> {
                try {
                    if (message instanceof ActiveMQTextMessage textMessage) {
                        String body = textMessage.getText();
                        if (StringUtils.isNotBlank(body)) {
                            String remoteCommand = body.trim();
                            log.info("RC> {}", remoteCommand);
                            boolean keepRunning = cli.runCommand(remoteCommand);
                            if (! keepRunning) {
                                lock.lock();
                                try {
                                    condition.signal(); // Wakes up one waiting thread
                                } finally {
                                    lock.unlock();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Remote Control message cause exception: message: {}", message);
                    log.warn("Remote Control message cause exception: EXCEPTION: ", e);
                }
            };

            if (!autoReconnect) {
                client.subscribe(url, rcTopic, messageListener, onException);
            } else {
                client.subscribeWithAutoReconnect(url, rcTopic, messageListener, onException, (exitCode) -> {
                    log.debug("BrokerClientApp: Exit with code {}", exitCode);
                    System.exit(exitCode);
                });
            }
            log.info("BrokerClientApp: Running in Remote Control mode");

            // Wait for remote command exit
            lock.lock();
            try {
                condition.await(); // Thread waits here
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } finally {
                lock.unlock();
            }

            // Disconnect
            client.unsubscribe(messageListener);
            client.closeConnection();
            log.debug("BrokerClientApp: Exiting...");

        } else
        // Run JS script
        if ("js".equalsIgnoreCase(command)) {
            ScriptEngineManager manager = new ScriptEngineManager();
            String engineName = "nashorn";
            if (aa<args.length && args[aa].startsWith("-E")) {
                String tmp = args[aa].substring(2).trim();
                if (StringUtils.isNotBlank(tmp)) engineName = tmp;
                else {
                    log.info("Available Script engines:");
                    manager.getEngineFactories().forEach(s->{
                        log.info("  Engine: {} {}, {}, Language: {} {}, Mime: {}, Ext: {}",
                            s.getEngineName(), s.getEngineVersion(), s.getNames(),
                            s.getLanguageName(), s.getLanguageVersion(),
                            s.getMimeTypes(), s.getExtensions());
                    });
                }
                aa++;
            }

            ScriptEngine engine = manager.getEngineByName(engineName);
            Bindings bindings = engine.createBindings();
            String scriptFile = args[aa++];

            ArrayList<String> jsArgs = new ArrayList<>();
            for (; aa<args.length; aa++) jsArgs.add(args[aa]);
            bindings.put("args", jsArgs);

            engine.eval("""
                            var BrokerClient = Java.type('gr.iccs.imu.ems.brokerclient.BrokerClient');
                            var EventMap = Java.type('event.gr.iccs.imu.ems.brokerclient.EventMap');
                            var System = Java.type('java.lang.System');
                            load('%s')",
                            """.formatted(scriptFile),
                    bindings
            );

        } else
        // error
        {
            log.error("BrokerClientApp: Unknown command: {}", command);
            usage(args);
        }
        log.debug("BrokerClientApp: Exit");
    }

    private static String getPayload(String payload, boolean processPlaceholders) throws IOException {
        if (payload==null) return null;
        String payloadTrim = payload.trim();
        if (StringUtils.startsWith(payloadTrim, "@")) {
            payload = Files.readString(Paths.get(StringUtils.substring(payloadTrim, 1)));
        }
        if ("-".equals(payloadTrim)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            payload = reader.lines().collect(Collectors.joining("\n"));
        }
        if (processPlaceholders) {
            payload = payload
                    .replaceAll("%TIMESTAMP%|%TS%", ""+System.currentTimeMillis());

        }
        return payload;
    }

    private static Map<String, String> collectProperties(String[] args, int aa) {
        return Arrays.stream(args, aa, args.length)
                .map(s->s.split("[=:]",2))
                .filter(p->StringUtils.isNotBlank(p[0]))
                .collect(Collectors.toMap(
                        p->p[0].trim(),
                        p->p.length>1 ? p[1] : ""
                ));
    }

    private static String processUrlArg(String url) {
        url = url.replace("%KAP%", "daemon=true&trace=false&useInactivityMonitor=false&connectionTimeout=0&keepAlive=true");
        log.debug("BrokerClientApp: Effective URL: {}", url);
        return url;
    }

    private static void sendEvent(String url, String username, String password, String topic, String type, Serializable payload, Map<String,String> properties) throws JMSException, IOException {
        log.info("BrokerClientApp: Publishing event: {}", payload);
        BrokerClient client = BrokerClient.newClient(username, password);
        client.publishEvent(url, topic, type, payload, properties);
        log.debug("BrokerClientApp: Event payload: {}", payload);
    }

    private static MessageListener getMessageListener(String destinationFiltersStr, String propertyFiltersStr) {
        final List<Pattern> destinationPatterns;
        if (StringUtils.isNotBlank(destinationFiltersStr)) {
            destinationPatterns = Arrays.stream(destinationFiltersStr.split(";"))
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .map(Pattern::compile)
                    .toList();
        } else {
            destinationPatterns = null;
        }
        log.trace("BrokerClientApp: Destination filters: {}", destinationFiltersStr);
        log.trace("BrokerClientApp: Destination filter patterns: {}", destinationPatterns);

        final Map<Pattern, Pattern> propertyPatterns;
        if (StringUtils.isNotBlank(propertyFiltersStr)) {
            propertyPatterns = Arrays.stream(propertyFiltersStr.split(";"))
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .map(s -> s.split("=", 2))
                    .filter(sp -> StringUtils.isNotBlank(sp[0]))
                    .collect(Collectors.toMap(
                            sp -> Pattern.compile(sp[0].trim()),
                            sp -> sp.length > 1 ? Pattern.compile(sp[1].trim()) : Pattern.compile("."),
                            (k1, k2) -> k2,
                            HashMap::new
                    ));
        } else {
            propertyPatterns = null;
        }
        log.trace("BrokerClientApp: Property filters: {}", propertyFiltersStr);
        log.trace("BrokerClientApp: Property filter patterns: {}", propertyPatterns);

        return message -> {
            try {
                // get message destination
                String destinationName = getDestinationName(message);

                // filter out Advisory messages
                if (filterAMQMessages && StringUtils.startsWithIgnoreCase(destinationName, "ActiveMQ.")) {
                    log.trace("BrokerClientApp:  - {}: ActiveMQ message filtered out: {}", destinationName, message);
                    log.debug("AMQ: {}:\n{}", destinationName, message);
                    return;
                }

                // filter by destination name
                if (destinationPatterns!=null) {
                    boolean matches = false;
                    for (Pattern p: destinationPatterns) {
                        log.trace("BrokerClientApp:  - {}: Checking if Destination name against pattern: {}", destinationName, p);
                        if (p.matcher(destinationName).find()) {
                            log.trace("BrokerClientApp:  - {}: Destination name MATCHED pattern: {}", destinationName, p);
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        log.trace("BrokerClientApp:  - {}: Message filtered out because destination name doesn't match any filter: {}", destinationName, message);
                        return;
                    }
                } else
                    log.trace("BrokerClientApp:  - {}: Destination message accepted: No destination filters specified", destinationName);

                // get properties as string
                String properties;
                Map<String, String> propertiesMap;
                if (message instanceof  ActiveMQMessage amqMessage) {
                    try {
                        properties = amqMessage.getProperties()
                                .entrySet().stream()
                                .map(x -> x.getKey() + "=" + x.getValue())
                                .collect(Collectors.joining(",", "{", "}"));
                        propertiesMap = amqMessage.getProperties().entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey, x -> x.getValue()==null ? "" : x.getValue().toString()
                        ));
                    } catch (Exception e) {
                        propertiesMap = null;
                        properties = "ERROR "+e.getMessage();
                        log.error("BrokerClientApp:  - {}: ERROR while reading properties: ", destinationName, e);
                    }
                } else {
                    //properties = "Not an ActiveMQ message";
                    Enumeration en = message.getPropertyNames();
                    propertiesMap = new HashMap<>();
                    while (en.hasMoreElements()) {
                        String pName = en.nextElement().toString();
                        Object pVal = message.getObjectProperty(pName);
                        if (pVal!=null)
                            propertiesMap.put(pName, pVal.toString());
                        else
                            propertiesMap.put(pName, null);
                    }
                    properties = propertiesMap.toString();
                }

                // filter by property name/value
                if (propertyPatterns!=null && propertiesMap!=null) {
                    boolean matches = false;
                    for (Map.Entry<Pattern,Pattern> pat : propertyPatterns.entrySet()) {
                        log.trace("BrokerClientApp:  - {}: Checking Property names against pattern: {}", destinationName, pat.getKey());
                        for (Map.Entry<String,String> prop : propertiesMap.entrySet()) {
                            log.trace("BrokerClientApp:  - {}: Checking Property '{}' against pattern: {}", destinationName, prop.getKey(), pat.getKey());
                            if (pat.getKey().matcher(prop.getKey()).find()) {
                                log.trace("BrokerClientApp:  - {}: Property '{}' matched pattern: {}", destinationName, prop.getKey(), pat.getKey());
                                if (pat.getValue().matcher(prop.getValue()).find()) {
                                    log.trace("BrokerClientApp:  - {}: Property value MATCHED pattern: {}", destinationName, pat.getValue());
                                    matches = true;
                                }
                            }
                        }
                    }
                    if (!matches) {
                        log.trace("BrokerClientApp:  - {}: Message filtered out because its properties don't match any filter: {}", destinationName, message);
                        return;
                    }
                } else
                    log.trace("BrokerClientApp:  - {}: Message accepted: No property filters specified", destinationName);

                // print message body and info
                if (message instanceof ObjectMessage objMessage) {
                    Object obj = objMessage.getObject();
                    String objClass = obj!=null ? obj.getClass().getName() : null;
                    log.trace("BrokerClientApp:  - {}: Received object message: {}: {}", destinationName, objClass, obj);
                    log.info("OBJ: {}: {}: properties: {}\n{}", destinationName, objClass, properties, asJson(obj));
                } else if (message instanceof MapMessage mapMessage) {
                    Enumeration en = mapMessage.getMapNames();
                    Map<Object,Object> map = new HashMap<>();
                    while (en.hasMoreElements()) {
                        String k = en.nextElement().toString();
                        map.put(k, mapMessage.getObject(k));
                    }
                    log.trace("BrokerClientApp:  - {}: Received map message: {}", destinationName, map);
                    log.info("MAP: {}: properties: {}\n{}", destinationName, properties, asJson(map));
                } else if (message instanceof BytesMessage bytesMessage) {
                    byte[] bytes = new byte[(int)bytesMessage.getBodyLength()];
                    bytesMessage.readBytes(bytes);
                    //String str = new String(bytes);
                    Object obj;
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                         ObjectInputStream is = new ObjectInputStream(bis))
                    {
                        obj = is.readObject();
                    } catch (Exception e) {
                        obj = bytes;
                    }
                    log.trace("BrokerClientApp:  - {}: Received bytes message: {}", destinationName, bytes);
                    log.info("BYTES: {}: properties: {}\n{}\n{}", destinationName, properties, bytes, asJson(obj));
                } else if (message instanceof TextMessage textMessage) {
                    String text = textMessage.getText();
                    log.trace("BrokerClientApp:  - {}: Received text message: {}", destinationName, text);
                    log.info("TXT: {}: properties: {}\n{}", destinationName, properties, asJson(text));
                } else {
                    log.trace("BrokerClientApp:  - {}: Received message: {}", destinationName, message);
                    log.info("MSG: {}: properties: {}\n{}", destinationName, properties, message);
                }

                // record message to file
                recordEvent(message);

            } catch (JMSException je) {
                log.warn("BrokerClientApp: onMessage: EXCEPTION: ", je);
            }
        };
    }

    private static String asJson(Object obj) {
        if (obj==null) return null;
        if (!printAsJson) return obj.toString();
        if (obj instanceof String s)
            obj = gson.fromJson(s, Map.class);
        return gson.toJson(obj);
    }

    private static int initRecording(String[] args, int aa) throws IOException {
        // Process recording command line arguments
        String format = null;
        if (args[aa].startsWith("-M"))
            format = args[aa++].substring(2).toLowerCase();
        boolean append = "-A".equalsIgnoreCase(args[aa]); if (append) aa++;
        boolean overwrite = "-O".equalsIgnoreCase(args[aa]);  if (overwrite) aa++;
        if (append && overwrite)
            throw new IllegalArgumentException("Options -A (append) and -O (overwrite) cannot be used together");
        String fileName = args[aa++];
        File file = Paths.get(fileName).toFile();
        String ext = StringUtils.substringAfterLast(file.getName(), ".");
        if (StringUtils.isNotBlank(format)) {
            if (!("csv".equalsIgnoreCase(format) || "json".equalsIgnoreCase(format)))
                throw new IllegalArgumentException("Unsupported recording format: "+format);
            else if ("csv".equalsIgnoreCase(format)) recordFormat = RECORD_FORMAT.CSV;
            else if ("json".equalsIgnoreCase(format)) recordFormat = RECORD_FORMAT.JSON;
        }
        else if ("csv".equalsIgnoreCase(ext)) recordFormat = RECORD_FORMAT.CSV;
        else if ("txt".equalsIgnoreCase(ext)) recordFormat = RECORD_FORMAT.CSV;
        else if ("json".equalsIgnoreCase(ext)) recordFormat = RECORD_FORMAT.JSON;
        else {
            log.warn("Unknown file extension. Assuming CSV");
            recordFormat = RECORD_FORMAT.CSV;
        }
        recordFile = file;

        // Check if record file exists
        if (!append && !overwrite) {
            if (file.exists()) {
                throw new IllegalArgumentException("Record file exists and neither -A (append) or -O (overwrite) flag has been set");
            }
        }

        // Initialize recording
        log.info("Record format: {}", recordFormat);
        log.info("Record file:   {}", recordFile);
        log.info("Start recording...");

        recordWriter = new BufferedWriter(new FileWriter(file, append));
        if (recordFormat==RECORD_FORMAT.CSV) {
            if (recordFile.length()==0)
                csvPrinter = new CSVPrinter(recordWriter, CSVFormat.DEFAULT
                        .builder().setHeader("Timestamp", "Destination", "Mime", "Type", "Contents", "Properties").build());
            else
                csvPrinter = new CSVPrinter(recordWriter, CSVFormat.DEFAULT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { csvPrinter.close(true); recordWriter.close(); } catch (IOException e) { log.error("BrokerClientApp: EXCEPTION while closing record file: ", e); }
                log.info("Recording stopped");
            }));
        } else
        if (recordFormat==RECORD_FORMAT.JSON) {
            jsonGenerator = new JsonFactory()
                    .createGenerator(recordWriter)
                    .setPrettyPrinter(new DefaultPrettyPrinter());
            jsonGenerator.writeStartArray();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { jsonGenerator.writeEndArray(); jsonGenerator.close(); recordWriter.close(); } catch (IOException e) { log.error("BrokerClientApp: EXCEPTION while closing record file: ", e); }
                log.info("Recording stopped");
            }));
        } else
            throw new IllegalArgumentException("Unsupported recording format: "+recordFormat);

        return aa;
    }

    private static void recordEvent(Message message) {
        if (!isRecording) return;

        try {
            long timestamp = message.getJMSTimestamp();
            String destinationName = getDestinationName(message);
            String mime = //amqMessage.getJMSXMimeType();
                    message.getClass().getName();
            String type;

            String content;
            if (message instanceof TextMessage textMessage) {
                type = BrokerClient.MESSAGE_TYPE.TEXT.name();
                content = textMessage.getText();
            } else
            if (message instanceof ObjectMessage objectMessage) {
                type = BrokerClient.MESSAGE_TYPE.OBJECT.name();
                Object obj = objectMessage.getObject();
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     ObjectOutputStream oos = new ObjectOutputStream(baos))
                {
                    oos.writeObject(obj);
                    byte[] bytes = baos.toByteArray();
                    content = Base64.getEncoder().encodeToString(bytes);
                }
            } else
            if (message instanceof MapMessage mapMessage) {
                type = BrokerClient.MESSAGE_TYPE.MAP.name();
                /*content = ((ActiveMQMapMessage)amqMessage).getContentMap()
                        .entrySet().stream()
                        .map(x -> x.getKey() + "=" + x.getValue())
                        .collect(Collectors.joining(",", "{", "}"));*/
                Enumeration en = mapMessage.getMapNames();
                Map<Object,Object> map = new LinkedHashMap<>();
                while (en.hasMoreElements()) {
                    String k = en.nextElement().toString();
                    map.put(k, mapMessage.getObject(k));
                }
                content = gson.toJson(map);
            } else
            if (message instanceof BytesMessage bytesMessage) {
                type = BrokerClient.MESSAGE_TYPE.BYTES.name();
                //byte[] bytes = amqMessage.getContent().getData();
                byte[] bytes = new byte[(int)bytesMessage.getBodyLength()];
                bytesMessage.readBytes(bytes);
                content = Base64.getEncoder().encodeToString(bytes);
            } else {
                throw new IllegalArgumentException("Unexpected message type: " + message.getClass());
            }

            // Get message properties
            Enumeration en = message.getPropertyNames();
            Map<Object,Object> propertiesMap = new LinkedHashMap<>();
            while (en.hasMoreElements()) {
                String k = en.nextElement().toString();
                propertiesMap.put(k, message.getStringProperty(k));
            }
            String properties = gson.toJson(propertiesMap);

            /*String properties = amqMessage.getProperties()
                    .entrySet().stream()
                    .map(x -> x.getKey() + "=" + x.getValue())
                    .collect(Collectors.joining(",", "{", "}"));*/

            // Record message data
            log.trace("REC> timestamp={}, topic={}, mime={}, type={}, contents={}, properties={}", timestamp, destinationName, mime, type, content, properties);
            if (recordFormat==RECORD_FORMAT.CSV) {
                csvPrinter.printRecord(timestamp, destinationName, mime, type, content, properties);
                csvPrinter.flush();
            } else
            if (recordFormat==RECORD_FORMAT.JSON) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeNumberField("timestamp", timestamp);
                jsonGenerator.writeStringField("destination", destinationName);
                jsonGenerator.writeStringField("mime", mime);
                jsonGenerator.writeStringField("type", type);
                jsonGenerator.writeStringField("content", content);
                jsonGenerator.writeStringField("properties", properties);
                jsonGenerator.writeEndObject();
                jsonGenerator.flush();
            }

        } catch (Exception e) {
            log.error("BrokerClientApp: EXCEPTION during RECORDING: ", e);
        }
    }

    private static int initPlayback(String[] args, int aa) throws IOException {
        // Process recording command line arguments
        playbackInterval = -1L;
        playbackDelay = -1L;
        int startAa = aa;
        if (args[aa].startsWith("-I")) {
            playbackInterval = Long.parseLong(args[aa++].substring(2).toLowerCase());
            if (playbackInterval<0) throw new IllegalArgumentException("Playback Interval cannot be negative: "+playbackInterval);
        }
        if (args[aa].startsWith("-D")) {
            playbackDelay = Long.parseLong(args[aa++].substring(2).toLowerCase());
            if (playbackDelay<0) throw new IllegalArgumentException("Playback Delay cannot be negative: "+playbackDelay);
        }
        if (args[aa].startsWith("-S")) {
            playbackSpeed = Double.parseDouble(args[aa++].substring(2).toLowerCase());
            if (playbackSpeed<=0) throw new IllegalArgumentException("Playback Speed cannot be negative or zero: "+playbackSpeed);
        }
        if (aa-startAa>1)
            throw new IllegalArgumentException("You cannot use -I, -D, -S switches at the same time");

        String format = null;
        if (args[aa].startsWith("-M"))
            format = args[aa++].substring(2).toLowerCase();
        String fileName = args[aa++];
        File file = Paths.get(fileName).toFile();
        String ext = StringUtils.substringAfterLast(file.getName(), ".");
        if (StringUtils.isNotBlank(format)) {
            if (!("csv".equalsIgnoreCase(format) || "json".equalsIgnoreCase(format)))
                throw new IllegalArgumentException("Unsupported recording format: "+format);
            else if ("csv".equalsIgnoreCase(format)) recordFormat = RECORD_FORMAT.CSV;
            else if ("json".equalsIgnoreCase(format)) recordFormat = RECORD_FORMAT.JSON;
        }
        else if ("csv".equalsIgnoreCase(ext)) recordFormat = RECORD_FORMAT.CSV;
        else if ("txt".equalsIgnoreCase(ext)) recordFormat = RECORD_FORMAT.CSV;
        else if ("json".equalsIgnoreCase(ext)) recordFormat = RECORD_FORMAT.JSON;
        else {
            log.warn("Unknown file extension. Assuming CSV");
            recordFormat = RECORD_FORMAT.CSV;
        }
        recordFile = file;

        // Initialize recording
        log.info("Playback format: {}", recordFormat);
        log.info("Playback file:   {}", recordFile);

        return aa;
    }

    private static long playbackEvents(String url, String username, String password) throws IOException, JMSException {
        AtomicLong countSuccess = new AtomicLong();
        AtomicLong countFail = new AtomicLong();

        BrokerClient client = BrokerClient.newClient();
        client.openConnection(url, username, password, true);

        boolean useInterval = (playbackInterval>=0);
        boolean useDelay = (playbackDelay>=0);

        log.info("Start playback...");
        long startTm = System.currentTimeMillis();
        final long[] prevValues = {-1L, -1L, -1L};   // Previous Event Timestamp, Previous System time, Last sleep time

        if (recordFormat==RECORD_FORMAT.CSV)
            playbackEventsFromCsv(client, prevValues, useInterval, useDelay, countSuccess, countFail, url);
        else if (recordFormat==RECORD_FORMAT.JSON)
            playbackEventsFromJson(client, prevValues, useInterval, useDelay, countSuccess, countFail, url);
        else
            throw new IllegalArgumentException("Unsupported or missing recording format: "+recordFormat);

        long endTm = System.currentTimeMillis();
        long count = countSuccess.get() + countFail.get();

        client.closeConnection();

        printPlaybackStatistics(endTm - startTm, countSuccess, countFail);

        return count;
    }

    private static void playbackEventsFromCsv(BrokerClient client, long[] prevValues, boolean useInterval, boolean useDelay,
                                              AtomicLong countSuccess, AtomicLong countFail, String url)
            throws IOException, JMSException
    {
        CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(new BufferedReader(new FileReader(recordFile)))
                .forEach(rec -> {
                    // read event data
                    long timestamp = Long.parseLong(rec.get("Timestamp"));
                    String destinationName = rec.get("Destination");
                    String mime = rec.get("Mime");
                    String type = rec.get("Type");
                    String contents = rec.get("Contents");
                    String properties = rec.get("Properties");

                    log.trace("REPLAY> Event data: timestamp={}, destination={}, mime={}, type={}, content={}, properties={}",
                            timestamp, destinationName, mime, type, contents, properties);

                    // read event properties
                    Map<String, String> propertiesMap = getPropertiesFromString(properties);

                    /*if (properties.startsWith("{") && properties.endsWith("}"))
                        properties = properties.substring(1, properties.length()-1);
                    Map<String, String> propertiesMap = Arrays.stream(properties.split(","))
                            .filter(StringUtils::isNotBlank)
                            .map(p -> p.split("=",2))
                            .collect(Collectors.toMap(p->p[0], p->p.length>1 ? p[1] : ""));*/

                    // wait and send
                    try {
                        waitAndSend(client, prevValues, useInterval, useDelay, url,
                                timestamp, destinationName, type, contents, propertiesMap, countSuccess, countFail);
                    } catch (Exception e) {
                        log.error("REPLAY> EXCEPTION: Ignoring record entry: timestamp={}, destination={}, mime={}, type={}, content={}, properties={}\n",
                                timestamp, destinationName, mime, type, contents, properties, e);
                    }
                });
    }

    private static void playbackEventsFromJson(BrokerClient client, long[] prevValues, boolean useInterval, boolean useDelay,
                                               AtomicLong countSuccess, AtomicLong countFail, String url)
            throws JMSException, IOException
    {
        Reader playbackReader = new BufferedReader(new FileReader(recordFile));
        JsonParser jsonParser = new JsonFactory().createParser(playbackReader);

        if (jsonParser.nextToken() == JsonToken.START_ARRAY) {
            while (jsonParser.nextToken() == JsonToken.START_OBJECT) {
                // read event data
                long timestamp = -1L;
                String destinationName = null;
                String mime = null;
                String type = null;
                String contents = null;
                String properties = "";

                while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = jsonParser.getCurrentName();
                    jsonParser.nextToken();
                    if ("timestamp".equals(fieldName)) timestamp = jsonParser.getLongValue();
                    else if ("destination".equals(fieldName)) destinationName = jsonParser.getText();
                    else if ("mime".equals(fieldName)) mime = jsonParser.getText();
                    else if ("type".equals(fieldName)) type = jsonParser.getText();
                    else if ("content".equals(fieldName)) contents = jsonParser.getText();
                    else if ("properties".equals(fieldName)) properties = jsonParser.getText();
                    else
                        log.warn("REPLAY> UNKNOWN JSON field at event #{}: {}", countSuccess.get()+countFail.get()+1, fieldName);
                }

                log.trace("REPLAY> Event data: timestamp={}, destination={}, mime={}, type={}, content={}, properties={}",
                        timestamp, destinationName, mime, type, contents, properties);

                // read event properties
                Map<String, String> propertiesMap = getPropertiesFromString(properties);

                /*if (properties.startsWith("{") && properties.endsWith("}"))
                    properties = properties.substring(1, properties.length()-1);
                Map<String, String> propertiesMap = Arrays.stream(properties.split(","))
                        .map(p -> p.split("=",2))
                        .collect(Collectors.toMap(p->p[0], p->p[1]));*/

                // wait and send
                try {
                    waitAndSend(client, prevValues, useInterval, useDelay, url,
                            timestamp, destinationName, type, contents, propertiesMap, countSuccess, countFail);
                } catch (Exception e) {
                    log.error("REPLAY> EXCEPTION: Ignoring record entry: timestamp={}, destination={}, mime={}, type={}, content={}, properties={}\n",
                            timestamp, destinationName, mime, type, contents, properties, e);
                }
            }
        }

        jsonParser.close();
        playbackReader.close();
    }

    private static Map<String, String> getPropertiesFromString(String properties) {
        LinkedHashMap<String,String> result = new LinkedHashMap<>();
        gson.fromJson(properties, Map.class).forEach((k,v) -> {
            if (k!=null && v!=null)
                result.put(k.toString(), v.toString());
        });
        return result;
    }

    private static void waitAndSend(BrokerClient client, long[] prevValues, boolean useInterval, boolean useDelay, String url,
                                    long timestamp, String destinationName, String type, String contents, Map<String,String> propertiesMap,
                                    AtomicLong countSuccess, AtomicLong countFail)
            throws IOException, ClassNotFoundException
    {
        // prepare event payload
        Serializable payload;
        if ("TEXT".equalsIgnoreCase(type)) {
            payload = contents;
        } else
        if ("OBJECT".equalsIgnoreCase(type)) {
            byte[] bytes = Base64.getDecoder().decode(contents);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                 ObjectInputStream ois = new ObjectInputStream(bais))
            {
                payload = (Serializable) ois.readObject();
            }
        } else
        if ("MAP".equalsIgnoreCase(type)) {
            payload = gson.fromJson(contents, EventMap.class);
        } else
        if ("BYTES".equalsIgnoreCase(type)) {
            payload = Base64.getDecoder().decode(contents);
        } else {
            throw new IllegalArgumentException("Unexpected message type: " + type);
        }

        // calculate wait time and sleep
        if (prevValues[1]>0) {
            // calculate wait time
            long sleepTime = 0;
            long now = System.currentTimeMillis();
            if (useInterval) {
                log.trace("REPLAY> Interval: now={}, prev={}, playback={}", now, prevValues[1], playbackInterval);
                prevValues[1] = prevValues[1] + playbackInterval;
                sleepTime = prevValues[1] - now;
                log.trace("REPLAY>         : sleep={}, new-prev={}", sleepTime, prevValues[1]);
            } else if (useDelay) {
                log.trace("REPLAY> Delay: now={}, playback={}", now, playbackDelay);
                sleepTime = playbackDelay;
            } else {
                long diff = (long)((timestamp - prevValues[0]) / playbackSpeed);
                log.trace("REPLAY> Recorded: diff={}, now={}, prev={}", diff, now, prevValues[1]);
                prevValues[0] = timestamp;
                prevValues[1] += diff;
                sleepTime = prevValues[1] - now;
                log.trace("REPLAY>         : sleep={}, new-prev={}", sleepTime, prevValues[1]);
            }
            prevValues[2] = sleepTime;
            // wait to send
            try {
                log.debug("REPLAY>  sleep={}", sleepTime);
                if (sleepTime > 1)
                    Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException("Playback interrupted");
            }
        } else {
            prevValues[0] = timestamp;
            prevValues[1] = System.currentTimeMillis();
        }

        // send event
        long counter = countSuccess.get()+countFail.get()+1;
        try {
            log.info("BrokerClientApp: Replay event #{}: Payload: {}", counter, payload);
            log.trace("BrokerClientApp: Publishing {} event: {}", type, payload);
            client.publishEvent(url, destinationName, type, payload, propertiesMap);
            log.debug("BrokerClientApp: Event payload: {}", payload);
            countSuccess.getAndIncrement();
        } catch (Exception e) {
            log.error("BrokerClientApp: EXCEPTION while playing back event #{}: ", counter, e);
            countFail.getAndIncrement();
        }
    }

    private static void printPlaybackStatistics(long duration, AtomicLong countSuccess, AtomicLong countFail) {
        long count = countSuccess.get() + countFail.get();
        log.info("Playback completed in {}ms", duration);
        log.info("        Sent: {}", countSuccess.get());
        log.info("      Failed: {}", countFail.get());
        log.info("       Total: {}", count);
        log.info("   Send Rate: {}e/s", 1000d * count / (duration));
        log.info("  Mean Delay: {}s", count<=1 ? "N/A" : (duration) / 1000d / (count-1) );
    }

    private static String getDestinationName(Message message) throws JMSException {
        Destination d = message.getJMSDestination();
        if (d instanceof Topic topic) {
            return topic.getTopicName();
        } else
        if (d instanceof Queue queue) {
            return queue.getQueueName();
        } else
            throw new IllegalArgumentException("Argument is not a JMS destination: "+d);
    }

    protected static void usage(String[] args) {
        if (args.length>1 && "help".equalsIgnoreCase(args[0]) &&
                StringUtils.equalsAnyIgnoreCase(args[1].trim(), "ALL", "FULL", "EXTENDED"))
            usageExtended();
        else
            usageBrief();
    }

    protected static void usageBrief() {
        log.info("Usage: ");
        log.info("client help [ALL|FULL|EXTENDED]");
        log.info("client list [-U<USERNAME> [-P<PASSWORD]] <URL>");
        log.info("client publish  [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC> [-T<MSG-TYPE>] <VALUE> <LEVEL> [<PROPERTY>]*");
        log.info("client publish2 [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC> [-T<MSG-TYPE>] [-PP<true|false>] <JSON-PAYLOAD|-|@file>  [<PROPERTY>]*");
        log.info("client publish3 [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC> [-T<MSG-TYPE>] [-PP<true|false>] <TEXT-PAYLOAD|-|@file>  [<PROPERTY>]*");
        log.info("client receive   [-Q] [-NJP] [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC_LIST> [-OE<ON-EXCEPTION-ACTION>]");
        log.info("client subscribe [-Q] [-NJP] [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC_LIST> [-OE<ON-EXCEPTION-ACTION>]");
        log.info("client record    [-Q] [-NJP] [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC_LIST> [-Mcsv|-Mjson] <REC-FILE> ");
        log.info("client playback  [-U<USERNAME> [-P<PASSWORD]] <URL> [-Innn|-Dnnn|-Sd[.d]] [-Mcsv|-Mjson] <REC-FILE> ");
        log.info("client generator [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC> [-T<MSG-TYPE>] <INTERVAL> <HOWMANY> <LOWER-VALUE> <UPPER-VALUE> <LEVEL>  [<PROPERTY>]*");
        log.info("client generator-cli [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC>");
        log.info("client generator-rc  [-U<USERNAME> [-P<PASSWORD]] <URL> <TOPIC> [-OE<ON-EXCEPTION-ACTION>]");
        log.info("client js [-E<engine-name>] <JS-file> ");
        log.info("  More Flags: -LL<level> -Q -FD<regex> -FP<regex>=<regex>;... -NPJ");
        log.info("    <URL>: (tcp:|ssl|amqp:)//<ADDRESS>:<PORT>[?[%KAP%][&...additional properties]*]   KAP: Keep-Alive Properties ");
        log.info("    <TOPIC_LIST>: <TOPIC>[,<TOPIC>]*");
        log.info("    <MSG-TYPE>: text, object, bytes, map");
        log.info("    <PROPERTY>: <Property name>=<Property value>  (use quotes if needed)");
    }

    protected static void usageExtended() {
        log.info("""
            Usage:
              client help
              client <COMMAND> <FLAGS> <URL>
              client <COMMAND> <FLAGS> <URL> <TOPIC> <PARAMETERS>
              client js <PARAMETERS>
            
            Arguments:
              <COMMAND>: The action to be performed (e.g., publish, subscribe, record).
              <FLAGS>:   Flags common to all commands.
              <URL>:     The URL of the broker (e.g., tcp://address:port, ssl://address:port, amqp://address:port).
                         Format: (tcp:|ssl|amqp:)//<ADDRESS>:<PORT>[?[%KAP%][&...additional properties]*]
                         KAP: Expands to default Keep-Alive Properties
              <TOPIC>:   The topic(s) for publishing or subscribing to messages.
              <PARAMETERS>: Additional parameters depending on the command.
        
            Flags:
              -LL<log_level>       Set the log level. Options: ALL, TRACE, DEBUG, INFO, WARN, ERROR
              -Q                   Exclude topics starting with 'ActiveMQ.'
              -FD<D_FILTER>        Include messages whose destination matches <D_FILTER> (regex)
              -FP<P_FILTERS>       Include messages where at least one property matches any of the <P_FILTERS>
              -NPJ                 Print messages using `.toString()`. Default: Pretty-print JSON
              -U<USERNAME>         Broker username
              -P<PASSWORD>         Broker password
            
            Pattern Matching:
              <D_FILTER>:  Regular expression for matching destination or part of it.
              <P_FILTERS>: Semicolon-separated list of property filters in the format '<P_NAME> [= <P_VALUE>]'.
              <P_NAME>:    Regular expression for matching property name or part of it.
              <P_VALUE>:   Regular expression for matching property value or part of it. If omitted it always matches.
        
            Commands:
              help [ALL|FULL|EXTENDED]
                Display this help message.
        
              list <FLAGS> <URL>
                List available topics (Not available with AMQP).
        
              publish <FLAGS> <URL> <TOPIC> [-T<MSG-TYPE>] <VALUE> <LEVEL> [<PROPERTY>*]...
                Publish a message to a topic.
                -T<MSG-TYPE>: Message type (optional). Type of message (text, object, bytes, map).
                <PROPERTY>: Optional message property in 'name=value' format.
        
              publish2 <FLAGS> <URL> <TOPIC> [-T<MSG-TYPE>] [-PP<true|false>] <JSON-PAYLOAD|-|@file> [<PROPERTY>*]...
                Publish JSON payload to a topic.
                -PP<true|false>: Process placeholders in payload (default 'true').
                -: Read payload from stdin.
                @file: Read payload from file.
        
              publish3 <FLAGS> <URL> <TOPIC> [-T<MSG-TYPE>] [-PP<true|false>] <TEXT-PAYLOAD|-|@file> [<PROPERTY>*]...
                Publish text payload to a topic.
                Same options as publish2, but for text payload.
        
              receive <FLAGS> <URL> <TOPIC_LIST> [-OE<ON-EXCEPTION-ACTION>]
                Receive messages from one or more topics. Hit Ctrl+C to exit.
                <TOPIC_LIST>: Comma-separated list of topics.
                -OE<ON-EXCEPTION-ACTION>: Behaviour if an error occurs. Options: IGNORE, LOG_AND_IGNORE, THROW, LOG_AND_THROW
        
              subscribe <FLAGS> <URL> <TOPIC_LIST> [-OE<ON-EXCEPTION-ACTION>]
                Subscribe to one or more topics. Hit ENTER to exit.
                -OE<ON-EXCEPTION-ACTION>: Behaviour if an error occurs. Options: IGNORE, LOG_AND_IGNORE, THROW, LOG_AND_THROW
        
              record <FLAGS> <URL> <TOPIC_LIST> [-Mcsv|-Mjson] [-A|-O] <REC-FILE>
                Record messages from specified topics to a file.
                -Mcsv or -Mjson: Recording format.
                -A: Append to the recording file if it exists.
                -O: Overwrite the recording file if it exists.
        
              playback <FLAGS> <URL> [-Innn|-Dnnn|-Sd[.d]] [-Mcsv|-Mjson] <REC-FILE>
                Playback recorded messages from a file.
                -Innn: Playback interval between event sends, in millis
                -Dnnn: Playback delay between event sends, in millis
                -Sd[.d]: Playback using the recorded intervals between events.
                         Playback speed can be set using d[.d] factor (e.g. -S2.5 means x2.5 faster playback)
        
              generator <FLAGS> <URL> <TOPIC> [-T<MSG-TYPE>] <INTERVAL> <HOWMANY> <LOWER-VALUE> <UPPER-VALUE> <LEVEL> [<PROPERTY>]...
                Generate and publish messages to a topic at regular intervals.
                <INTERVAL>:  Millis between message sends.
                <HOWMANY>:   Number of messages to generate. Use a negative number for infinite messages.
                <LOWER-VALUE>, <UPPER-VALUE>:  Message metric values are randomly picked from this range.
                <LEVEL>:     Message metric level.
        
              generator-cli <FLAGS> <URL> <TOPIC>
                Start an interactive shell for controlling event generator.
                Additional help available in the CLI. Type 'help'
        
              generator-rc <FLAGS> <URL> <COMMANDS-TOPIC> [-OE<ON-EXCEPTION-ACTION>]
                Start a remote control session for controlling event generator with messages.
                -OE<ON-EXCEPTION-ACTION>: Behaviour if an error occurs. Options: IGNORE, LOG_AND_IGNORE, THROW, LOG_AND_THROW
        
              js [-E<engine-name>] <JS-file>
                Execute a JavaScript file with a specific engine.
            """);
    }
}