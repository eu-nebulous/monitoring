/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.iccs.imu.ems.baguette.client.cluster.*;
import gr.iccs.imu.ems.brokercep.BrokerCepService;
import gr.iccs.imu.ems.brokercep.BrokerCepStatementSubscriber;
import gr.iccs.imu.ems.brokercep.cep.CepService;
import gr.iccs.imu.ems.brokercep.event.EventMap;
import gr.iccs.imu.ems.brokerclient.event.EventGenerator;
import gr.iccs.imu.ems.brokerclient.properties.BrokerClientProperties;
import gr.iccs.imu.ems.common.collector.CollectorContext;
import gr.iccs.imu.ems.common.misc.EventConstant;
import gr.iccs.imu.ems.common.misc.SystemResourceMonitor;
import gr.iccs.imu.ems.util.*;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.Member;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static gr.iccs.imu.ems.util.GroupingConfiguration.BrokerConnectionConfig;

/**
 * Command Executor
 */
@Slf4j
@Service
public class CommandExecutor {

    private static String getConfigDir() {
        String confDir = System.getenv("EMS_CONFIG_DIR");
        if (StringUtils.isBlank(confDir)) confDir = System.getProperty("EMS_CONFIG_DIR");
        if (StringUtils.isBlank(confDir)) confDir = "conf";
        return confDir;
    }

    private final static String DEFAULT_CONF_DIR = getConfigDir();
    private final static String DEFAULT_ID_FILE = DEFAULT_CONF_DIR + "/cached-id.properties";
    private static final int DEFAULT_ID_LENGTH = 32;
    private final static String DEFAULT_KEYSTORE_DIR = DEFAULT_CONF_DIR;

    public final static String EVENT_CLUSTER_NODE_ADDED = "CLUSTER_NODE_ADDED";
    public final static String EVENT_CLUSTER_NODE_REMOVED = "CLUSTER_NODE_REMOVED";

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private BaguetteClient baguetteClient;
    @Autowired
    private BrokerCepService brokerCepService;
    @Autowired
    private BrokerClientProperties brokerClientProperties;
    @Autowired
    private PasswordUtil passwordUtil;
    @Autowired
    @Getter
    private EventBus<String,Object,Object> eventBus;

    private BaguetteClientProperties config;
    private String idFile;

    private InputStream in;
    private PrintStream out;
    private PrintStream err;
    private String clientId;

    @Getter
    private ClientConfiguration clientConfiguration;
    @Getter
    private final Map<String, GroupingConfiguration> groupings = new LinkedHashMap<>();
    private GroupingConfiguration activeGrouping;

    private final AtomicLong subscriberCount = new AtomicLong(0);
    private final Map<String,List<BrokerCepStatementSubscriber>> groupingsSubscribers = new LinkedHashMap<>();

    private final Map<String, EventGenerator> eventGenerators = new HashMap<>();

    @Autowired
    private ClusterManagerProperties clusterManagerProperties;
    @Getter
    private ClusterManager clusterManager;
    private ClusterTest clusterTest;
    private boolean clusterKeystoreInitialized = false;
    private String clusterKeystoreFile;
    private String clusterKeystoreType;
    private String clusterKeystorePassword;

    @Getter private String globalGrouping;
    @Getter private String aggregatorGrouping;
    @Getter private String nodeGrouping;

    private Thread serverWatcherThread;
    private boolean captureInputLine;
    @Getter private String lastInputLine;

    @Autowired
    private TaskScheduler taskScheduler;
    private ScheduledFuture<?> statsSendTask;
    @Autowired
    private SystemResourceMonitor systemResourceMonitor;

    public CommandExecutor() {
        initializeClientId();
    }

    public void setConfiguration(BaguetteClientProperties config) {
        log.trace("CommandExecutor: brokerCepService: {}", brokerCepService);
        log.trace("CommandExecutor: config: {}", config);
        this.config = config;
        this.idFile = DEFAULT_ID_FILE;
        initializeClientId();
    }

    private void initializeClientId() {
        if (config!=null && StringUtils.isNotBlank(config.getClientId())) {
            clientId = config.getClientId().trim();
            saveClientId(clientId);
        }
        if (StringUtils.isBlank(clientId))
            clientId = loadCachedClientId();
        if (StringUtils.isBlank(clientId)) {
            this.clientId = RandomStringUtils.randomAlphanumeric(DEFAULT_ID_LENGTH);
            saveClientId(clientId);
        }
    }

    void communicateWithServer(InputStream in, PrintStream out, PrintStream err) throws IOException {
        log.trace("communicateWithServer(): BEGIN");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
            log.trace("communicateWithServer(): WHILE START: {}", line);
            if (captureInputLine) {
                lastInputLine = line;
                log.trace("communicateWithServer(): captureInputLine: {}", line);
                captureInputLine = false;
                continue;
            }
            line = line.trim();
            if (StringUtils.startsWithIgnoreCase(line, "CLUSTER-KEY")) {
                String[] s = line.split(" ", 2);
                log.info("Cluster key from Server: {} {}", s[0], s.length>1 ? passwordUtil.encodePassword(s[1]) : "");
            } else
                log.debug("Server input: {}", line);

            try {
                log.trace("communicateWithServer(): Calling execCmd: {}", line);
                boolean exit = execCmd(line.split("[ \t]+"), in, out, err);
                log.trace("communicateWithServer(): Exit code: {}", exit);
                if (exit) break;
            } catch (Exception ex) {
                log.error("communicateWithServer(): EXCEPTION: ", ex);
                // Report exception back to server
                err.println(ex);
                ex.printStackTrace(err);
                err.flush();
            }
            log.trace("communicateWithServer(): WHILE END");
        }
        log.trace("communicateWithServer(): END");
    }

    public void executeCommand(String command) throws IOException, InterruptedException {
        String[] args = command.split(" ");
        execCmd(args, baguetteClient.getClient().getIn(), baguetteClient.getClient().getOut(), baguetteClient.getClient().getOut());

        // Wait for server response/input if needed
        while (captureInputLine) {
            log.trace("Waiting for server input...");
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        log.trace("Server input: {}", lastInputLine);
    }

    boolean executeCommand(String line, InputStream in, PrintStream out, PrintStream err) throws IOException, InterruptedException {
        return execCmd(line.split("[ \t]+"), in, out, err);
    }

    boolean execCmd(String[] args, InputStream in, PrintStream out, PrintStream err) throws IOException, InterruptedException {
        if (args == null || args.length == 0) return false;
        String cmd = args[0].toUpperCase();
        args[0] = "";

        this.in = in;
        this.out = out;
        this.err = err;

        if ("EXIT".equals(cmd)) {
            boolean canExit = config != null && config.isExitCommandAllowed();
            if (canExit) {
                if (clusterManager != null && clusterManager.isRunning())
                    clusterManager.leaveCluster();
                return true;    // Signal 'Sshc' to quit
            } else {
                final String mesg = "Exit is not allowed. Ignoring EXIT command";
                log.warn(mesg);
                out.println(mesg);
            }
        } else if ("SET-LOG-LEVEL".equals(cmd)) {
            if (args.length < 3) return false;
            String loggerName = args[1].trim();
            String newLevel = args[2].trim();
            if (StringUtils.equalsAnyIgnoreCase(newLevel, "-", "null", "inherit")) newLevel = null;
            log.info("SET LOG LEVEL TO: {}, for logger: {}", newLevel, loggerName);
            LogsUtil.setLogLevel(loggerName, newLevel);
        } else if ("GET-LOG-LEVEL".equals(cmd)) {
            if (args.length < 2) return false;
            String loggerName = args[1].trim();
            if (loggerName.endsWith("*")) {
                log.info("LISTING LOG LEVELS FOR: {}", loggerName);
                final String prefix = StringUtils.stripEnd(loggerName, "*");
                Objects.<Map<String,String>>requireNonNullElse(LogsUtil.getLoggers(prefix), Map.of())
                        .entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> log.info("  {} {}", String.format("%-5s", entry.getValue()), entry.getKey()));

            } else {
                log.info("LOG LEVEL FOR LOGGER: {} --> {}", loggerName, LogsUtil.getLogLevel(loggerName));
            }

        } else if ("CONNECT".equals(cmd)) {
            if (serverWatcherThread!=null) {
                log.warn("Already connected");
                return false;
            }
            baguetteClient.startSshClient(false);
            serverWatcherThread = new Thread(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(baguetteClient.getClient().getIn())));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        log.info(line);
                    }
                } catch (Exception ex) {
                    if (baguetteClient.getClient()!=null)
                        log.warn("Exception in serverWatcherThread: ", ex);
                    else
                        log.debug("serverWatcherThread has exited");
                }
                serverWatcherThread = null;
            });
            serverWatcherThread.start();
        } else if ("DISCONNECT".equals(cmd)) {
            if (serverWatcherThread==null) {
                log.warn("Not connected");
                return false;
            }
            baguetteClient.stopSshClient();
            serverWatcherThread = null;

        } else if ("SEND".equals(cmd)) {
            StringBuilder sb = new StringBuilder();
            for (int i=1; i<args.length; i++)
                sb.append(args[i]).append(" ");
            String cmdLine = sb.toString();
            log.info("SEND: {}", cmdLine);
            lastInputLine = null;
            captureInputLine = true;
            baguetteClient.getClient().getOut().println(cmdLine);

        } else if ("CLIENT".equals(cmd)) {
            // Information from server. Don't do anything
        } else if ("ECHO".equals(cmd)) {
            // Server echoes back client command. Don't do anything
        } else if ("HEARTBEAT".equals(cmd)) {
            // Respond to server with OK
            out.println("OK");

        } else if ("GET-ID".equals(cmd)) {
            log.info("GET ID: {}", clientId);
            out.println(clientId);
        } else if ("SET-ID".equals(cmd)) {
            if (args.length < 2) return false;
            String id = args[1].trim();
            log.info("SET ID: {}", id);
            saveClientId(id);

        } else if ("WRITE-CONFIGURATION".equals(cmd)) {
            String fileName = (args.length>1) ? args[1].trim() : DEFAULT_CONF_DIR + "/config-export.json";
            ConfigurationContents contents = ConfigurationContents.builder()
                    .timestamp(System.currentTimeMillis())
                    .clientId(this.clientId)
                    .activeGrouping(this.activeGrouping.getName())
                    .groupings(this.groupings)
                    .build();

            ObjectMapper mapper = new ObjectMapper();
            File file = Paths.get(fileName).toFile();
            mapper.writer().writeValue(file, contents);
            log.info("Current configuration saved to file: {}", file.getPath());

        } else if ("READ-CONFIGURATION".equals(cmd)) {
            String fileName = (args.length>1) ? args[1].trim() : DEFAULT_CONF_DIR + "/config-export.json";
            File file = Paths.get(fileName).toFile();
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            ObjectMapper mapper = new ObjectMapper();
            ConfigurationContents config = mapper.readValue(content, ConfigurationContents.class);
            log.debug("Configuration read from file: {}\n{}", file, config);

            // Clear current state
            clearGroupings();

            // Initialize current state
            String newId = config.getClientId();
            if (StringUtils.isNotBlank(newId))
                saveClientId(newId);

            config.getGroupings().forEach(groupings::put);

            String activeConf = config.getActiveGrouping();
            if (StringUtils.isNotBlank(activeConf))
                setActiveGrouping(activeConf);

            log.info("Current configuration loaded from file: {}", file.getPath());

        } else if ("LIST-GROUPING-CONFIGS".equals(cmd)) {
            log.info("Configured groupings: {}", groupings.keySet());
            out.println(String.join(", ", groupings.keySet()));
        } else if ("CLEAR-GROUPING-CONFIGS".equals(cmd)) {
            clearGroupings();
        } else if ("GET-GROUPING-CONFIG".equals(cmd)) {
            if (args.length < 2) return false;
            GroupingConfiguration grouping = groupings.get(args[1].trim());
            log.info("{}", grouping);
            out.printf("%s\n", grouping);
        } else if ("SET-CLIENT-CONFIG".equals(cmd)) {
            if (args.length < 2) return false;
            String configStr = String.join(" ", args).trim();
            log.trace("client-config-base64: {}", configStr);
            setClientConfiguration(configStr);
        } else if ("SET-GROUPING-CONFIG".equals(cmd)) {
            if (args.length < 2) return false;
            String configStr = String.join(" ", args).trim();
            log.trace("grouping-config-base64: {}", configStr);
            setGroupingConfiguration(configStr);
        } else if ("GET-ACTIVE-GROUPING".equals(cmd)) {
            String activeGroupingName = activeGrouping != null ? activeGrouping.getName() : "-";
            log.info("Active grouping: {}", activeGroupingName);
            out.println(activeGroupingName);
        } else if ("SET-ACTIVE-GROUPING".equals(cmd)) {
            if (args.length < 2) return false;
            String newGrouping = String.join(" ", args).trim();
            log.trace("new-active-grouping: {}", newGrouping);
            setActiveGrouping(newGrouping);
        } else if ("SET-CONSTANTS".equals(cmd)) {
            if (args.length < 2) return false;
            String configStr = String.join(" ", args).trim();
            log.trace("constants-base64: {}", configStr);
            setConstants(configStr);
        } else if ("SEND-LOCAL-EVENT".equals(cmd)) {
            if (args.length < 2) return false;
            String destination = args[1].trim();
            double value = args.length > 2 ? Double.parseDouble(args[2].trim()) : Math.random() * 1000;
            log.trace("Sending local event: destination={}, metricValue={}", destination, value);
            sendLocalEvent(destination, value);
        } else if ("SEND-EVENT".equals(cmd)) {
            if (args.length < 3) return false;
            String connection = args[1].trim();
            String destination = args[2].trim();
            double value = args.length > 3 ? Double.parseDouble(args[3].trim()) : Math.random() * 1000;
            log.trace("Sending event: connection={}, destination={}, metricValue={}", connection, destination, value);
            sendEvent(connection, destination, value);
        } else if ("GENERATE-EVENTS-START".equals(cmd)) {
            if (args.length < 5) return false;
            String destination = args[1].trim();
            long interval = Long.parseLong(args[2].trim());
            double lower = Double.parseDouble(args[3].trim());
            double upper = Double.parseDouble(args[4].trim());

            if (eventGenerators.get(destination) == null) {
                /*
                EventGenerator generator = applicationContext.getBean(EventGenerator.class);
                generator.setBrokerUrl(brokerCepService.getBrokerCepProperties().getBrokerUrlForClients());
                generator.setBrokerUsername(brokerCepService.getBrokerUsername());
                generator.setBrokerPassword(brokerCepService.getBrokerPassword());
                */
                EventGenerator generator = new EventGenerator((destinationName, event) ->
                        sendEvent(null, destinationName, event)==CollectorContext.PUBLISH_RESULT.SENT);
                generator.setDestinationName(destination);
                generator.setLevel(1);
                generator.setInterval(interval);
                generator.setLowerValue(lower);
                generator.setUpperValue(upper);
                eventGenerators.put(destination, generator);
                generator.start();
            }
        } else if ("GENERATE-EVENTS-STOP".equals(cmd)) {
            if (args.length < 2) return false;
            String destination = args[1].trim();
            EventGenerator generator = eventGenerators.remove(destination);
            if (generator != null) {
                generator.stop();
            }
        } else if ("CLUSTER-KEY".equals(cmd)) {
            if (args.length<5) {
                log.error("Too few arguments");
                return false;
            }

            setClusterKeystore(args[1], args[2], args[3], args[4]);

        } else if ("CLUSTER-JOIN".equals(cmd)) {
            if (clusterManager!=null && clusterManager.isRunning()) {
                log.error("Cluster is running. Leave cluster first");
                return false;
            }

            // Check and collect arguments
            if (args.length<5) {
                log.error("Too few arguments");
                out.println("Too few arguments (CLUSTER-JOIN)");
                return false;
            }
            List<String> argsList = new ArrayList<>(Arrays.asList(args));
            argsList.remove(0); // Discard command part
            String clusterId = argsList.remove(0);
            String groupings = argsList.remove(0);
            boolean startElection = Boolean.parseBoolean(
                    StringUtils.substringAfter(argsList.remove(0), "start-election="));
            String localNodeAddress = argsList.remove(0);
            List<String> otherNodeAddresses = argsList.isEmpty() ? null : argsList;
            log.info("CLUSTER-JOIN ARGS: cluster-id={}, groupings={}, local-node={}, other-nodes={}",
                    clusterId, groupings, localNodeAddress, otherNodeAddresses);

            // Setup groupings
            String[] grpPart = groupings.split(":");
            globalGrouping = grpPart[0];
            aggregatorGrouping = grpPart[1];
            nodeGrouping = grpPart[2];
            log.info("CLUSTER-JOIN ARGS: Groupings: global={}, aggregator={}, node={}",
                    globalGrouping, aggregatorGrouping, nodeGrouping);

            // Initialize cluster properties
            if (clusterManagerProperties==null)
                clusterManagerProperties = new ClusterManagerProperties();
            clusterManagerProperties.setClusterId(clusterId);

            if (clusterManagerProperties.getTls().isEnabled()) {
                log.debug("Cluster TLS is enabled");
                if (clusterKeystoreInitialized) {
                    log.debug("Cluster TLS Keystore has been initialized");
                    clusterManagerProperties.getTls().setKeystore(clusterKeystoreFile);
                    clusterManagerProperties.getTls().setKeystorePassword(clusterKeystorePassword);
                    clusterManagerProperties.getTls().setTruststore(clusterKeystoreFile);
                    clusterManagerProperties.getTls().setTruststorePassword(clusterKeystorePassword);
                }
            }

            clusterManagerProperties.getLocalNode().setAddress(localNodeAddress);
            clusterManagerProperties.setMemberAddresses(otherNodeAddresses);
            log.debug("Cluster properties:  {}", clusterManagerProperties);

            // Initialize cluster manager
            if (clusterManager==null) {
                clusterManager = applicationContext.getBean(ClusterManager.class);
                clusterManager.setProperties(clusterManagerProperties);
            }

            // Join/start cluster
            clusterManager.initialize(clusterManagerProperties, new ClusterNodeCallback(this));
            //clusterManager.setCallback(new TestCallback(clusterManager.getLocalAddress()));
            clusterManager.joinCluster( startElection );
            clusterManager.waitToJoin();
            log.info("Joined to cluster");

            // Set this node's broker connection configuration (Used if it becomes the Aggregator)
            String brokerConnConfig = getBrokerConfigurationAsString();
            clusterManager.getLocalMember().properties().setProperty("aggregator-connection-configuration", brokerConnConfig);

            // Update forwards to Aggregator (if any)
            List<Member> aggregators = clusterManager.getBrokerUtil().getBrokers();
            if (aggregators.size()==1) {
                String newConfig = aggregators.get(0).properties().getProperty("aggregator-connection-configuration", "");
                if (StringUtils.isNotBlank(newConfig)) {
                    setBrokerConfigurationFromString(newConfig);
                } else {
                    log.error("CLUSTERING ERROR: Aggregator broker connection config. is not available: {}", aggregators.get(0));
                }
            } else if (aggregators.isEmpty()) {
                log.info("No Aggregators found. Waiting for Baguette Server command");
            } else {
                log.error("CLUSTERING ERROR: Many Aggregators found! {}", aggregators);
            }

            // Update node status based on current grouping
            if (activeGrouping==null)
                clusterManager.getBrokerUtil().setLocalStatus(BrokerUtil.NODE_STATUS.NOT_SET);
            else if (activeGrouping.getName().equals(aggregatorGrouping))
                clusterManager.getBrokerUtil().setLocalStatus(BrokerUtil.NODE_STATUS.AGGREGATOR);
            else
                clusterManager.getBrokerUtil().setLocalStatus(BrokerUtil.NODE_STATUS.CANDIDATE);

        } else if ("CLUSTER-TEST".equals(cmd)) {

            if (args.length<2 || "START".equalsIgnoreCase(args[1])) {
                if (clusterManager==null) {
                    log.error("Cluster has not been initialized. Run CLUSTER-JOIN first");
                    return false;
                }
                long interval = Math.max(100L, (args.length>=3)
                        ? Long.parseLong(args[2])
                        : clusterManagerProperties.getTestInterval());
                clusterTest = new ClusterTest(clusterManager);
                clusterTest.startTest(interval);
            } else if ("STOP".equalsIgnoreCase(args[1])) {
                if (clusterTest==null) {
                    log.error("Cluster test is not running");
                    return false;
                }
                clusterTest.stopTest();
                clusterTest = null;
            } else {
                log.error("Unknown command option: {} {}", cmd, args[1]);
            }

        } else if ("CLUSTER-LEAVE".equals(cmd)) {
            if (clusterManager==null) {
                log.error("Cluster has not been initialized. Run CLUSTER-JOIN first");
                return false;
            }
            if (! clusterManager.isRunning()) {
                log.error("Cluster is not running. Join cluster first");
                return false;
            }

            clusterManager.leaveCluster();

            if (clusterTest!=null) {
                clusterTest.stopTest();
                clusterTest = null;
            }
            log.info("Left cluster");

        } else if ("CLUSTER-SHELL".equals(cmd)) {
            if (clusterManager==null) {
                log.error("Cluster has not been initialized. Run CLUSTER-JOIN first");
                return false;
            }
            ClusterCLI cli = clusterManager.getCli();
            cli.setIn(in);
            cli.setOut(out);
            cli.setErr(err);
            cli.setPromptUpdate(true);
            log.info("Cluster CLI starts");
            cli.run();
            log.info("Cluster CLI ended");
        } else if ("CLUSTER-EXEC".equals(cmd)) {
            if (args.length < 2) {
                log.error("No cluster command specified");
                return false;
            }
            if (clusterManager==null) {
                log.error("Cluster has not been initialized. Run CLUSTER-JOIN first");
                return false;
            }
            ClusterCLI cli = clusterManager.getCli();
            cli.setIn(in);
            cli.setOut(out);
            cli.setErr(err);
            String[] args1 = Arrays.stream(args, 1, args.length).toArray(String[]::new);
            String cmd1 = String.join(" ", args1);
            try {
                log.info("Cluster executes command: {}", cmd1);
                cli.executeCommand(cmd1, args1);
			} catch (Exception ex) {
				log.error("Cluster: Exception caught while executing command: {}\nException ", cmd1, ex);
            }

        } else if ("GET-LOCAL-NODE-CERTIFICATE".equals(cmd)) {
            String localAddress = ClusterManager.getLocalHostAddress();
            String localHostname = ClusterManager.getLocalHostName();
            String nlChar = (args.length > 1) ? args[1].trim() : null;
            try {
                log.info("Retrieving this node certificate from keystore:");
                String cert = brokerCepService.getBrokerCertificate();
                if (cert!=null && StringUtils.isNotBlank(nlChar))
                    cert = cert.replace("\r\n", nlChar).replace("\n", nlChar);
                log.info("{} {} {}", localAddress, localHostname, cert);
                out.println(localAddress+" "+localHostname+" "+cert);
            } catch (Exception e) {
                log.error("Exception while retrieving local node certificate: ", e);
            }

        } else if ("ADD-TRUSTED-NODE".equals(cmd)) {
            if (args.length < 4) return false;
            String nodeAlias = args[1];
            String nlChar = args[2];
            String nodeCert = String.join(" ",
                    Arrays.asList(args).subList(3, args.length)).replace(nlChar, "\n");
            try {
                log.info("Adding/Updating trusted node certificate in truststore: {}\nCertificate: {}", nodeAlias, nodeCert);
                brokerCepService.addOrReplaceCertificateInTruststore(nodeAlias, nodeCert);
                log.info("Truststore updated: {}", nodeAlias);
            } catch (Exception e) {
                log.error("Exception while updating truststore: ", e);
            }

        } else if ("DEL-TRUSTED-NODE".equals(cmd)) {
            if (args.length < 2) return false;
            String nodeAlias = args[1];
            try {
                log.info("Deleting trusted node certificate from truststore: {}", nodeAlias);
                brokerCepService.deleteCertificateFromTruststore(nodeAlias);
                log.info("Truststore updated: {}", nodeAlias);
            } catch (Exception e) {
                log.error("Exception while updating truststore: ", e);
            }

        } else if ("COLLECTOR".equals(cmd)) {
            if (args.length < 2) {
                log.warn("Too few arguments");
                out.println("Too few arguments");
                return false;
            }
            String operation = args[1];
            String target = args.length==3 ? args[2] : null;
            boolean all = ("*".equalsIgnoreCase(target) || "ALL".equalsIgnoreCase(target));
            if ("LIST".equalsIgnoreCase(operation)) {
                String list = baguetteClient.getCollectorsList().stream()
                        .map(c->" - "+c.getClass().getName())
                        .collect(Collectors.joining("\n"));
                log.info("BaguetteClient: Listing Collectors:\n{}", list);
                out.printf("Listing Collectors:\n%s\n", list);
            } else
            if ("START".equalsIgnoreCase(operation)) {
                if (target==null) {
                    log.warn("Too few arguments");
                    out.println("Too few arguments");
                    return false;
                }
                log.info("BaguetteClient: Starting Collector: {}...", target);
                baguetteClient.getCollectorsList().stream()
                        .filter(c -> all || c.getClass().getName().equals(target))
                        .peek(c -> log.debug(" - Starting collector: {}...", c.getClass().getName()))
                        .forEach(IClientCollector::start);
                log.info("BaguetteClient: Starting Collector: {}... done", target);
            } else
            if ("STOP".equalsIgnoreCase(operation)) {
                if (target==null) {
                    log.warn("Too few arguments");
                    out.println("Too few arguments");
                    return false;
                }
                log.info("BaguetteClient: Stopping Collector: {}...", target);
                baguetteClient.getCollectorsList().stream()
                        .filter(c -> all || c.getClass().getName().equals(target))
                        .peek(c -> log.debug(" - Stopping collector: {}...", c.getClass().getName()))
                        .forEach(IClientCollector::stop);
                log.info("BaguetteClient: Stopping Collector: {}... done", target);
            } else
                log.error("BaguetteClient: Unknown Collector operation: {}", operation);

        } else if ("SHOW-CONFIG".equals(cmd)) {
            log.info("BaguetteClient: configuration:\n{}", config);
            log.info("Cluster: configuration:\n{}", clusterManagerProperties);
        } else if ("GET-STATS".equals(cmd)) {
            getStatistics(args[1]);
        } else if ("COLLECT-STATS".equals(cmd)) {
            collectStatistics();
        } else if ("SEND-STATS".equals(cmd)) {
            if (args.length < 2) {
                log.warn("Too few arguments");
                out.println("Too few arguments");
                return false;
            }
            String operation = args[1];

            if ("START".equalsIgnoreCase(operation))
                sendStatisticsStart();
            else if ("STOP".equalsIgnoreCase(operation))
                sendStatisticsStop();
            else if ("CLEAR".equalsIgnoreCase(operation))
                clearStatistics();
            else {
                log.error("BaguetteClient: Unknown STATS operation: {}", operation);
            }

        } else if ("CLEAR-STATS".equals(cmd)) {
            clearStatistics();
        } else if ("SEND-CLIENT-PROPERTY".equals(cmd)) {
            if (args.length < 2) {
                log.warn("Too few arguments");
                out.println("Too few arguments");
                return false;
            }
            String propName = args[1];
            String propValue = args.length==3 ? args[2] : null;
            sendClientProperty(propName, propValue);
        } else {
            args[0] = cmd;
            String line = String.join(" ", args);
            if (StringUtils.isNotBlank(line))
                log.warn("UNKNOWN COMMAND: {}", line);
        }
        return false;
    }

    private void setClusterKeystore(String ksFile, String ksType, String ksPassword, String ksBase64) {
        String ksDir = clusterManagerProperties.getTls().getKeystoreDir();
        if (StringUtils.isBlank(ksDir)) ksDir = DEFAULT_KEYSTORE_DIR;
        if (!ksDir.endsWith("/")) ksDir += "/";
        this.clusterKeystoreInitialized = true;
        this.clusterKeystoreFile = ksDir + ksFile;
        this.clusterKeystoreType = ksType;
        this.clusterKeystorePassword = ksPassword;
        String clusterKeystoreBase64 = ksBase64;
        log.info("Cluster Keystore: file: {}", clusterKeystoreFile);
        log.info("                  type: {}", clusterKeystoreType);
        log.info("              password: {}", passwordUtil.encodePassword(clusterKeystorePassword));
        log.debug("        Base64 content: {}", passwordUtil.encodePassword(clusterKeystoreBase64));
        try {
            KeystoreUtil
                    .getKeystore(clusterKeystoreFile, clusterKeystoreType, clusterKeystorePassword)
                    .passwordUtil(passwordUtil)
                    .createIfNotExist()
                    .writeBase64ToFile(clusterKeystoreBase64);
        } catch (Exception e) {
            log.error("Exception while creating cluster keystore", e);
        }
    }

    /*protected Properties _base64ToProperties(String paramsStr) {
        paramsStr = new String(Base64.getDecoder().decode(paramsStr), StandardCharsets.UTF_8);
        //log.trace("params-str:  {}", paramsStr);
        Properties params = new Properties();
        try {
            params.load(new StringReader(paramsStr));
            return params;
        } catch (IOException e) {
            log.error("Could not deserialize parameters: ", e);
        }
        return null;
    }*/

    protected synchronized void setClientConfiguration(String configStr) {
        try {
            // Update Baguette client configuration
            log.debug("Received serialization of client configuration: {}", configStr);
            ClientConfiguration config = (ClientConfiguration) SerializationUtil.deserializeFromString(configStr);
            ClientConfiguration oldConfig = clientConfiguration;
            if (oldConfig!=null) {
                log.debug("Old client config.: {}", oldConfig);
            }
            synchronized (groupings) {
                if (oldConfig!=null && (config.getCollectorConfigurations()==null || config.getCollectorConfigurations().isEmpty())) {
                    config.setCollectorConfigurations(oldConfig.getCollectorConfigurations());
                    log.trace("Copied collector-configs from old client config.: \n{}", oldConfig.getCollectorConfigurations());
                }
                config.setNodesWithoutClient(new LinkedHashSet<>( config.getNodesWithoutClient() ));
                config.setPodInfo(new LinkedHashSet<>( config.getPodInfo()!=null ? config.getPodInfo() : Set.of() ));
                clientConfiguration = config;
            }
            log.debug("New client config.: {}", config);
            HashMap<String,ClientConfiguration> payload = new HashMap<>();
            payload.put("new", clientConfiguration);
            payload.put("old", oldConfig);
            eventBus.send(EventConstant.EVENT_CLIENT_CONFIG_UPDATED, payload, this);

            // Configure collection from additional addresses
            if (this.config.getCollectFromAdditionalAddresses()!=null) {
                this.config.getCollectFromAdditionalAddresses().stream()
                        .filter(StringUtils::isNotBlank)
                        .forEach(address -> clientConfiguration.getNodesWithoutClient().add(address));
            }
            // Configure collection from localhost
            boolean collectFromLocal;
            if (this.config.isAutodetectCollectFromLocal()) {
                // Auto-detect if we should collect from localhost too
                // If running in a POD, turn-off collection from localhost
                collectFromLocal = StringUtils.isBlank(System.getenv("POD_NAME"));
            } else {
                // Use configuration setting
                collectFromLocal = this.config.isCollectFromLocal();
            }
            log.debug("collectFromLocal={}", collectFromLocal);
            if (collectFromLocal) {
                if (!clientConfiguration.getNodesWithoutClient().contains("localhost")
                    && !clientConfiguration.getNodesWithoutClient().contains("127.0.0.1")
                    && !clientConfiguration.getNodesWithoutClient().contains("::1"))
                {
                    clientConfiguration.getNodesWithoutClient().add("localhost");
                }
            }

            // Update collectors' configurations
            Map<String, List<Map<String, Serializable>>> collectorConfigs = clientConfiguration.getCollectorConfigurations();
            log.debug("collectorConfigs={}", collectorConfigs);
            applicationContext.getBean(BaguetteClient.class).getCollectorsList().forEach(collector -> {
                List<Map<String, Serializable>> cc = collectorConfigs.get(collector.getName());
                if (cc!=null)
                    collector.setConfiguration(cc);
            });

        } catch (Exception ex) {
            log.error("Exception while deserializing received Client configuration: ", ex);
        }
    }

    protected synchronized void setGroupingConfiguration(String configStr) {
        try {
            log.debug("Received serialization of Grouping configuration: {}", configStr);
            GroupingConfiguration grouping = (GroupingConfiguration) SerializationUtil.deserializeFromString(configStr);
            GroupingConfiguration oldGrouping = groupings.get(grouping.getName());
            if (oldGrouping!=null) {
                log.debug("Old grouping config.: {}", oldGrouping);
            }
            synchronized (groupings) {
                groupings.put(grouping.getName(), grouping);
            }
            log.debug("New grouping config.: {}", grouping);

        } catch (Exception ex) {
            log.error("Exception while deserializing received Grouping configuration: ", ex);
        }
    }

    protected synchronized void setConstants(String configStr) {
        try {
            log.debug("Received serialization of Constants: {}", configStr);
            Map<String, Object> all = StrUtil.castToMapStringObject(
                    SerializationUtil.deserializeFromString(configStr));
            Map<String, Double> constants = StrUtil.castToMapStringObject(all.get("constants"))
                    .entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey, y -> (Double) y.getValue()
                    ));
            log.debug("Received Constants: {}", constants);

            if (activeGrouping != null) {
                log.info("SETTING CONSTANTS: {}", constants);
                activeGrouping.setConstants(constants);
                brokerCepService.setConstants(constants);
                log.debug("New constants set: {}", constants);
            } else {
                log.warn("No active grouping. Constants will be ignored");
            }

        } catch (Exception ex) {
            log.error("Exception while deserializing received Constants: ", ex);
        }
    }

    protected synchronized void clearGroupings() {
        // Clear state of all groupings
        log.info("Old active grouping: {}", activeGrouping!=null ? activeGrouping.getName(): null);
        log.info("Clearing all groupings...");
        activeGrouping = null;
        brokerCepService.clearState();
        groupingsSubscribers.clear();
        log.info("Clearing all groupings completed");
    }

    protected synchronized void setActiveGrouping(String newGroupingName) {
        // Checking if new grouping is valid
        GroupingConfiguration newGrouping = groupings.get(newGroupingName);
        if (newGrouping == null) {
            log.error("setActiveGrouping: Grouping specified does not exist: {}", newGroupingName);
            return;
        }
        if ("GLOBAL".equalsIgnoreCase(newGroupingName)) {
            throw new IllegalArgumentException("BUG: GLOBAL grouping configuration must have never been set");
        }

        // Figure out if we need to add or remove groupings
        boolean addGroupings = true;
        String activeGroupingName = "()";
        if (activeGrouping != null) {
            activeGroupingName = activeGrouping.getName();
            int diff = GROUPING.valueOf(activeGroupingName).compareTo(GROUPING.valueOf(newGroupingName));
            log.trace("setActiveGrouping: Grouping difference: {}", diff);
            if (diff == 0) {
                log.info("No need to switch grouping. Active grouping is: {}", newGroupingName);
                return;
            }
            addGroupings = diff > 0;
        }

        // Add or Remove groupings between active and new grouping
        if (addGroupings) {
            log.info("Need to add groupings from {} to {}", activeGroupingName, newGroupingName);
            addGroupingsTill(newGroupingName);
        } else {
            log.info("Need to remove groupings from {} to {}", activeGroupingName, newGroupingName);
            removeGroupingsTill(newGroupingName);
        }

        // Complete active grouping switch
        activeGrouping = groupings.get(newGroupingName);
        log.info("Active grouping switch completed: {} -> {}", activeGroupingName, newGroupingName);
        String oldGroupingName = activeGroupingName;
        activeGroupingName = newGroupingName;

        // Notify Baguette Server about grouping change
        log.info("NOTIFY-GROUPING-CHANGE: {}", newGroupingName);
        out.println("-NOTIFY-GROUPING-CHANGE: "+newGroupingName);

        // If Aggregator notify Baguette Server
        if (clusterManager!=null && GROUPING.valueOf(aggregatorGrouping)==GROUPING.valueOf(newGroupingName)) {
            log.info("Notifying Baguette Server i am the new aggregator");
            out.println("CLUSTER AGGREGATOR "+clientId);
        }

        // Notify collectors for the active grouping change
        final String finalActiveGroupingName = activeGroupingName;
        baguetteClient.getCollectorsList()
                .forEach(c -> c.activeGroupingChanged(oldGroupingName, finalActiveGroupingName));
    }

    protected synchronized void addGroupingsTill(String newGroupingName) {
        // Get available grouping names (in reverse order, i.e. from PER_INSTANCE to PER_CLOUD)
        List<String> availableGroupings = GROUPING.getNames().stream()
                .filter(groupings::containsKey).collect(Collectors.toList());
        Collections.reverse(availableGroupings);
        log.info("addGroupingsTill: Available grouping configurations: {}", availableGroupings);

        // Get groupings between active and new grouping
        int start = 0;
        if (activeGrouping != null) {
            start = availableGroupings.indexOf(activeGrouping.getName()) + 1;
            log.trace("addGroupingsTill: active-grouping-index + 1: {}", start);
        }
        int end = availableGroupings.indexOf(newGroupingName)+1;
        log.trace("addGroupingsTill: new-grouping-index + 1: {}", end);
        log.trace("addGroupingsTill: grouping-range: [{}..{})", start, end);
        List<String> groupingsToAdd = availableGroupings.subList(start, end);
        log.debug("addGroupingsTill: groupings-to-add: {}",groupingsToAdd);

        // Collect and merge settings of groupings between active and new
        Set<String> eventTypes = new LinkedHashSet<>();
        Map<String,Double> constants = new HashMap<>();
        Set<FunctionDefinition> functionDefinitions = new LinkedHashSet<>();
        Map<String,Map<String, Set<String>>> rules = new LinkedHashMap<>();
        for (String groupingName : groupingsToAdd) {
            log.debug("addGroupingsTill: Merging settings of grouping: {}", groupingName);
            GroupingConfiguration grouping = groupings.get(groupingName);

            // Add event types
            Set<String> et = grouping.getEventTypeNames();
            eventTypes.addAll(et);
            log.trace("addGroupingsTill: + Grouping event types: {}", et);
            // Add constants
            Map<String, Double> con = grouping.getConstants();
            constants.putAll(con);
            log.trace("addGroupingsTill: +   Grouping constants: {}", con);
            // Add function definitions
            Set<FunctionDefinition> fd = grouping.getFunctionDefinitions();
            functionDefinitions.addAll(fd);
            log.trace("addGroupingsTill: +  Grouping func. defs: {}", fd);
            // List cep rules
            Map<String, Set<String>> rl = grouping.getRules();
            rules.put(groupingName, rl);
            log.trace("addGroupingsTill: +    Grouping rule map: {}", rl);
        }
        log.debug("addGroupingsTill: = Collected event types: {}", eventTypes);
        log.debug("addGroupingsTill: =   Collected constants: {}", constants);
        log.debug("addGroupingsTill: =  Collected func. defs: {}", functionDefinitions);
        log.debug("addGroupingsTill: =   Collected rule maps: {}", rules);

        // Apply merged settings
        brokerCepService.addEventTypes(eventTypes, EventMap.getPropertyNames(), EventMap.getPropertyClasses());
        brokerCepService.setConstants(constants);
        brokerCepService.addFunctionDefinitions(functionDefinitions);

        // Apply rules-per-topic of new grouping
        rules.forEach((groupingName, grpRules) -> {
            log.debug("addGroupingsTill: Processing rule map: {}", grpRules);
            if (grpRules != null) {
                for (Map.Entry<String, Set<String>> topicRules : grpRules.entrySet()) {
                    String topic = topicRules.getKey();
                    log.info("addGroupingsTill: Processing settings of topic: {}", topic);
                    for (String rule : topicRules.getValue()) {
                        // Add EPL statement subscriber
                        String subscriberName = "Subscriber_" + subscriberCount.getAndIncrement();
                        log.info("addGroupingsTill: + Adding subscriber for EPL statement: subscriber-name={}, topic={}, rule={}", subscriberName, topic, rule);
                        BrokerCepStatementSubscriber statementSubscriber =
                                new BrokerCepStatementSubscriber(subscriberName, topic, rule, brokerCepService, passwordUtil, Collections.emptySet());
                        brokerCepService.getCepService().addStatementSubscriber(
                                statementSubscriber
                        );
                        groupingsSubscribers.computeIfAbsent(groupingName, s -> new LinkedList<>()).add(statementSubscriber);
                    }
                    log.trace("addGroupingsTill: Added to groupingsSubscribers: {}", groupingsSubscribers);
                }
            }
        });
        log.trace("addGroupingsTill: Final groupingsSubscribers: {}", groupingsSubscribers);

        // Clear forward-to-groupings settings of (old) active grouping
        clearActiveGroupingForwards();

        // Set forward-to-topic settings of new grouping (active to-be)
        setGroupingForwards(newGroupingName);

        // Update truststore certificates from grouping settings
        updateCertificates(groupings.get(newGroupingName));
    }

    protected synchronized void removeGroupingsTill(String newGroupingName) {
        // Get available grouping names (in normal order, i.e. from PER_CLOUD to PER_INSTANCE)
        List<String> availableGroupings = GROUPING.getNames().stream()
                .filter(groupings::containsKey).collect(Collectors.toList());
        log.info("removeGroupingsTill: Available grouping configurations: {}", availableGroupings);

        // Get groupings between active and new grouping
        int start = availableGroupings.indexOf(activeGrouping.getName());
        log.trace("removeGroupingsTill: active-grouping-index: {}", start);

        int end = availableGroupings.indexOf(newGroupingName);
        log.trace("removeGroupingsTill: new-grouping-index: {}", end);
        log.trace("removeGroupingsTill: grouping-range: [{}..{})", start, end);
        List<String> groupingsToRemove = availableGroupings.subList(start, end);
        log.debug("removeGroupingsTill: groupings-to-remove: {}",groupingsToRemove);

        // Remove subscribers and topics of groupings higher than new grouping
        LinkedHashSet<String> eventTypes = new LinkedHashSet<>();
        final CepService cepService = brokerCepService.getCepService();
        for (String groupingName : groupingsToRemove) {
            log.debug("removeGroupingsTill: Clearing settings of grouping: {}", groupingName);
            GroupingConfiguration grouping = groupings.get(groupingName);
            eventTypes.addAll(grouping.getEventTypeNames());
            groupingsSubscribers.get(groupingName).forEach(cepService::removeStatementSubscriber);
            groupingsSubscribers.remove(groupingName);
        }
        eventTypes.forEach(s->brokerCepService.getBrokerCepBridge().removeConsumerOf(s));

        // Clear forward-to-topic settings of (old) active grouping
        clearActiveGroupingForwards();

        // Set forward-to-topic settings of new grouping (active to-be)
        setGroupingForwards(newGroupingName);
    }

    private void clearActiveGroupingForwards() {
        if (activeGrouping==null) {
            log.debug("clearActiveGroupingForwards: No active grouping");
            return;
        }
        log.debug("clearActiveGroupingForwards: Clearing forward-to-grouping settings of active grouping: {}", activeGrouping.getName());
        log.trace("clearActiveGroupingForwards: Clearing groupingsSubscribers: BEFORE: {}", groupingsSubscribers);
        List<BrokerCepStatementSubscriber> subscribers = groupingsSubscribers.get(activeGrouping.getName());
        log.trace("clearActiveGroupingForwards: Clearing subscribers of grouping: {}: {}", activeGrouping.getName(), subscribers);
        if (subscribers!=null) {
            for (BrokerCepStatementSubscriber subscriber : subscribers) {
                log.debug("clearActiveGroupingForwards: - Clearing forward-to-grouping settings for: subscriber={}, topic={}, forwards={}",
                        subscriber.getName(), subscriber.getTopic(), subscriber.getForwardToGroupings());
                subscriber.setForwardToGroupings(Collections.emptySet());
            }
        }
        log.trace("clearActiveGroupingForwards: Clearing groupingsSubscribers: AFTER: {}", groupingsSubscribers);
    }

    private void setGroupingForwards(String newGroupingName) {
        GroupingConfiguration newGrouping = groupings.get(newGroupingName);
        final Map<String,Set<BrokerConnectionConfig>> topicFwdUrls = new HashMap<>();
        for (Map.Entry<String, Set<String>> topicRules : newGrouping.getRules().entrySet()) {
            String topic = topicRules.getKey();
            log.info("setGroupingForwards: Processing settings of topic: {}", topic);

            // Build forward-to-groupings set for current topic
            Set<BrokerConnectionConfig> forwardToGroupings = new HashSet<>();
            Set<String> connections = newGrouping.getConnections().get(topic);
            log.info("setGroupingForwards: + Adding connections for topic: {} --> {}", topic, connections);
            if (connections != null) {
                for (String fwdToGrouping : connections) {
                    BrokerConnectionConfig fwdBrokerConn = newGrouping.getBrokerConnections().get(fwdToGrouping);
                    forwardToGroupings.add(fwdBrokerConn);
                }
            }
            log.info("setGroupingForwards: = forwardToGroupings of topic {}: {}", topic, forwardToGroupings);
            topicFwdUrls.put(topic, forwardToGroupings);
        }
        log.trace("setGroupingForwards: Update groupingsSubscribers: BEFORE: {}", groupingsSubscribers);
        groupingsSubscribers.get(newGroupingName).forEach(subscriber -> {
            Set<BrokerConnectionConfig> fwdUrls = topicFwdUrls.get(subscriber.getTopic());
            if (fwdUrls!=null) subscriber.setForwardToGroupings(fwdUrls);
        });
        log.trace("setGroupingForwards: Update groupingsSubscribers: AFTER: {}", groupingsSubscribers);
    }

    protected void updateCertificates(@NonNull GroupingConfiguration grouping) {
        if (brokerCepService.getBrokerTruststore()==null) {
            log.warn("Broker-CEP trust store has not been initialized. Probably SSL is disabled.");
            log.debug("Broker URL: {}", brokerCepService.getBrokerCepProperties().getBrokerUrl());
            return;
        }

        // Update truststore with per-grouping broker certificates
        try {
            log.debug("Truststore certificates before update: {}",
                    KeystoreUtil.getCertificateAliases(brokerCepService.getBrokerTruststore()));
            for (String g : GROUPING.getNames()) {
                BrokerConnectionConfig groupingBrokerCfg = grouping.getBrokerConnections().get(g);
                if (groupingBrokerCfg != null) {
                    String brokerUrl = groupingBrokerCfg.getUrl().trim();
                    String brokerCert = groupingBrokerCfg.getCertificate().trim();
                    String host = null;
                    if (StringUtils.isNotBlank(brokerUrl))
                        host = StringUtils.substringBetween(brokerUrl.trim(), "://", ":");
                    log.debug("Grouping host: {}", host);
                    if (StringUtils.isNotEmpty(brokerCert)) {
                        //log.debug("Updating broker certificate to truststore for Grouping: {}", g);
                        //brokerCepService.addOrReplaceCertificateInTruststore(g, brokerCert);
                        log.debug("Updating broker certificate to truststore for Grouping Host: {}", host);
                        brokerCepService.addOrReplaceCertificateInTruststore(host, brokerCert);
                    } else {
                        log.warn("No broker PEM certificate provided for Grouping: {}", g);
                    }
                } else {
                    log.debug("Removing broker certificate from truststore for Grouping (no new certificate provided): {}", g);
                    brokerCepService.deleteCertificateFromTruststore(g);
                }
            }
            log.debug("Truststore certificates after update: {}",
                    KeystoreUtil.getCertificateAliases(brokerCepService.getBrokerTruststore()));
        } catch (Exception ex) {
            log.error("EXCEPTION while updating Trust store: ", ex);
        }
    }

    public void sendLocalEvent(String destination, double metricValue) {
        String brokerUrl = brokerCepService.getBrokerCepProperties().getBrokerUrlForConsumer();
        log.debug("sendLocalEvent(): local-broker-url={}, metricValue={}", brokerUrl, metricValue);
        sendEvent(brokerUrl, destination, metricValue);
    }

    public void sendLocalEvent(String destination, Map<String,Object> event) {
        String brokerUrl = brokerCepService.getBrokerCepProperties().getBrokerUrlForConsumer();
        log.debug("sendLocalEvent(): local-broker-url={}, event={}", brokerUrl, event);
        sendEvent(brokerUrl, destination, event);
    }

    public void sendEvent(String connectionStr, String destination, double metricValue) {
        Map<String, Object> event = new HashMap<>();
        event.put("metricValue", metricValue);
        event.put("level", 1);
        event.put("timestamp", System.currentTimeMillis());
        sendEvent(connectionStr, destination, event);
    }

    public CollectorContext.PUBLISH_RESULT sendEvent(String connectionStr, String destination, Map<String, Object> event, boolean createDestination) {
        if (log.isTraceEnabled())
            log.trace("sendEvent(): connection-string={}, destination={}, create-destination={}, destination-exists={}, event={}",
                    connectionStr, destination, createDestination, brokerCepService.destinationExists(destination), event);
        CollectorContext.PUBLISH_RESULT result;
        if (createDestination || brokerCepService.destinationExists(destination)) {
            result = sendEvent(connectionStr, destination, event);
            log.trace("sendEvent(): Event sent: destination={}, result={}, event={}", destination, result, event);
            return result;
        }
        result = CollectorContext.PUBLISH_RESULT.SKIPPED;
        log.trace("sendEvent(): Event skipped: destination={}, result={}, event={}", destination, result, event);
        return result;
    }

    public CollectorContext.PUBLISH_RESULT sendEvent(String connectionStr, String destination, Map<String, Object> event) {
        try {
            log.debug("sendEvent(): Sending event: connection={}, destination={}, event={}", connectionStr, destination, event);
            brokerCepService.publishEvent(connectionStr, destination, event);
            log.debug("sendEvent(): Event sent: connection={}, destination={}, event={}", connectionStr, destination, event);
            return CollectorContext.PUBLISH_RESULT.SENT;
        } catch (Exception ex) {
            log.error("sendEvent(): Error while sending event: connection={}, destination={}, event={}, exception: ", connectionStr, destination, event, ex);
            return CollectorContext.PUBLISH_RESULT.ERROR;
        }
    }

    protected synchronized String loadCachedClientId() {
        // Get the 'cached client id' file name
        if (idFile == null)
            idFile = DEFAULT_ID_FILE;

        // Check if the cached client id file exists
        File file = Paths.get(idFile).toFile();
        if (! file.exists()) { log.warn("loadCachedClientId: Cached client id file not exists: {}", idFile); return null; }
        if (! file.isFile()) { log.warn("loadCachedClientId: Cached client id file is not a regular file: {}", idFile); return null; }

        // Load contents of existing 'client id' file
        try (InputStream in = new FileInputStream(idFile)) {

            Properties p = new Properties();
            p.load(in);

            // Get cached client id (if any)
            String id = p.getProperty("client.id", null);
            if (StringUtils.isNotBlank(id)) {
                id = id.trim();
                log.info("loadCachedClientId: Used cached Client Id: {}", clientId);
                return id;
            } else {
                log.warn("loadCachedClientId: No cached Client id found in file: {}", idFile);
            }
        } catch (Exception e) {
            log.warn("loadCachedClientId: EXCEPTION while loading cached Client id from file: {}\n", idFile, e);
        }
        return null;
    }

    protected synchronized void saveClientId(String id) {
        // Check new id value
        if (StringUtils.isBlank(id)) {
            log.error("SET-ID: ERROR: Empty id: {}", id);
            err.println("ERROR Empty id: " + id);
            return;
        }
        clientId = id.trim();

        // Load contents of existing 'id file' (if any)
        if (StringUtils.isBlank(idFile))
            idFile = DEFAULT_ID_FILE;
        Properties p = new Properties();
        // Check if the cached client id file exists
        File file = Paths.get(idFile).toFile();
        if (file.exists() && file.isFile()) {
            try (InputStream in = new FileInputStream(idFile)) {
                p.load(in);
            } catch (Exception e) {
                log.warn("saveClientId: EXCEPTION while reading cached Client id from file: {}\n", idFile, e);
            }
        } else {
            log.warn("saveClientId: Cached client id file not exists or is not a regular file: {}", idFile);
        }

        // Update 'id' in file contents in-memory
        p.setProperty("client.id", id);

        // Store new contents into 'id file'
        try (OutputStream os = new FileOutputStream(idFile)) {
            p.store(os, null);
            log.info("ID SET to: {}", id);
            if (out!=null) out.println("ID SET");
        } catch (Exception ex) {
            log.error("SET-ID: EXCEPTION: ", ex);
            err.println("ERROR While storing id to file: " + ex);
        }
    }

    private BrokerConnectionConfig getBrokerConfiguration() {
        BrokerConnectionConfig config = new BrokerConnectionConfig(
                activeGrouping!=null ? activeGrouping.getName() : null,
                brokerCepService.getBrokerCepProperties().getBrokerUrlForClients(),
                brokerCepService.getBrokerCertificate(),
                brokerCepService.getBrokerUsername(),
                brokerCepService.getBrokerPassword()
        );
        log.debug("getBrokerConfiguration: {}", config);
        return config;
    }

    @SneakyThrows
    private String getBrokerConfigurationAsString() {
        ObjectMapper mapper = new ObjectMapper();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            mapper.writer().writeValue(baos, getBrokerConfiguration());
            String configStr = Base64.getEncoder().encodeToString(baos.toByteArray());
            log.debug("getBrokerConfigurationAsString: {}", configStr);
            return configStr;
        }
    }

    @SneakyThrows
    private BrokerConnectionConfig getBrokerConfigurationFromString(String configStr) {
        log.debug("getBrokerConfigurationFromString: INPUT: {}", configStr);
        ObjectMapper mapper = new ObjectMapper();
        BrokerConnectionConfig config = mapper
                .readValue(Base64.getDecoder().decode(configStr), BrokerConnectionConfig.class);
        log.debug("getBrokerConfigurationFromString: OUTPUT: {}", config);
        return config;
    }

    private void setBrokerConfigurationFromString(String brokerConfigStr) {
        BrokerConnectionConfig brokerConfig = getBrokerConfigurationFromString(brokerConfigStr);
        setBrokerConfiguration(brokerConfig);
    }

    private void setBrokerConfiguration(BrokerConnectionConfig brokerConfig) {
        log.debug("setBrokerConfiguration(): PASSED (NEW) CONFIG:\n{}", brokerConfig);
        log.debug("setBrokerConfiguration(): ACTIVE GROUPING: {}", activeGrouping.getName());
        log.debug("setBrokerConfiguration(): OLD BROKER CONNECTIONS:\n{}", activeGrouping.getBrokerConnections());

        // Update broker connection configuration for aggregator grouping
        BrokerConnectionConfig oldConn = activeGrouping.getBrokerConnections().get(aggregatorGrouping);
        activeGrouping.getBrokerConnections().put(aggregatorGrouping, brokerConfig);
        log.debug("setBrokerConfiguration(): NEW BROKER CONNECTIONS:\n{}", activeGrouping.getBrokerConnections());

        // Update forward settings of active grouping
        // Clear forward-to-groupings settings of active grouping
        clearActiveGroupingForwards();
        // Set forward-to-topic settings of active grouping
        setGroupingForwards(activeGrouping.getName());
        // Update truststore certificates from active grouping settings
        updateCertificates(activeGrouping);
    }

    private void nodeStatusChanged(BrokerUtil.NODE_STATUS oldStatus, BrokerUtil.NODE_STATUS newStatus) {
        log.info("NOTIFY-STATUS-CHANGE: {}", newStatus.toString());
        out.println("-NOTIFY-STATUS-CHANGE: "+newStatus);
    }

    private void sendClientProperty(String propertyName, String propertyValue) {
        log.info("CLIENT-PROPERTY-CHANGE: {} = {}", propertyName, propertyValue);
        out.printf("-CLIENT-PROPERTY-CHANGE: %s %s%n", propertyName, propertyValue);
    }

    @SneakyThrows
    private void getStatistics(String inputUuid) {
        //Map<String,Object> statsMap = brokerCepService.getBrokerCepStatistics();
        Map<String,Object> statsMap = _collectAndSendStatistics(false);
        log.debug("Statistics: {}", statsMap);
        if (out!=null) out.println("-INPUT:"+inputUuid+":"+SerializationUtil.serializeToString(statsMap));
    }

    private void collectStatistics() {
        try {
            // Run system metrics collection script
            log.debug("Running system metrics collection...");
            boolean result = systemResourceMonitor.runImmediatelyBlocking(-1);  // >=0: timeout in millis; <0: wait forever
            log.debug("Running system metrics collection... {}", result ? "done" : "cancel/timeout");

            _collectAndSendStatistics(true);
        } catch (Exception ex) {
            log.error("Exception while getting Statistics to server: ", ex);
        }
    }

    @SneakyThrows
    private void sendStatisticsStart() {
        String destination = baguetteClient.getBaguetteClientProperties().getSendStatisticsDestination();
        if (StringUtils.isNotBlank(destination))
            brokerCepService.getEventCache().excludeDestination(destination);
        statsSendTask = taskScheduler.scheduleWithFixedDelay(() -> {
            try {
                _collectAndSendStatistics(true);
            } catch (Exception ex) {
                log.error("Exception while sending Statistics to server: ", ex);
            }
        }, Duration.ofMillis(baguetteClient.getBaguetteClientProperties().getSendStatisticsDelay()));
        log.info("Start sending STATS to server");
    }

    private Map<String, Object> _collectAndSendStatistics(boolean sendStats) throws IOException {
        // Collect metrics
        Map<String, Object> statsMap = brokerCepService.getBrokerCepStatistics();
        log.debug("BCEP Statistics: {}", statsMap);
        Map<String, Object> sysMap = systemResourceMonitor.getLatestMeasurements();
        log.debug("System Statistics: {}", sysMap);

        // Prepare and send response
        Map<String, Object> clientStats = new HashMap<>();
        if (statsMap!=null) clientStats.putAll(statsMap);
        if (sysMap!=null) clientStats.putAll(sysMap);
        if (sendStats && out!=null) {
            log.debug("-STATS: {}", clientStats);
            out.println("-STATS:" + SerializationUtil.serializeToString(clientStats));
        }

        // Send stats event to local broker
        String destination = baguetteClient.getBaguetteClientProperties().getSendStatisticsDestination();
        if (StringUtils.isNotBlank(destination)) {
            EventMap event = new EventMap(1);
            event.put("client-stats", clientStats);
            event.put("ip-addr-1", NetUtil.getDefaultIpAddress());
            event.put("ip-addr-2", NetUtil.getPublicIpAddress());
            Object tmp = clientStats.remove("latest-events");
            sendLocalEvent(destination, event);
            clientStats.put("latest-events", tmp);
        }

        return clientStats;
    }

    @SneakyThrows
    private void sendStatisticsStop() {
        statsSendTask.cancel(true);
        log.info("Stop sending STATS to server");
    }

    private void clearStatistics() {
        brokerCepService.clearBrokerCepStatistics();
        log.info("Statistics cleared");
        if (out!=null) out.println("STATISTICS CLEARED");
    }

    public boolean isAggregator() {
        return activeGrouping!=null && aggregatorGrouping!=null && aggregatorGrouping.equals(activeGrouping.getName());
    }

    public boolean isNode() {
        return ! isAggregator();
    }

    public void notifyEmsServer(String message) {
        log.info("NOTIFY-X: {}", message);
        out.println("-NOTIFY-X: "+message);
    }

    /*private static class StreamGobbler implements Runnable {
        private InputStream inputStream1;
        private InputStream inputStream2;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream1, InputStream inputStream2, Consumer<String> consumer) {
            this.inputStream1 = inputStream1;
            this.inputStream2 = inputStream2;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream1)).lines().forEach(consumer);
            new BufferedReader(new InputStreamReader(inputStream2)).lines().forEach(consumer);
        }
    }*/

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    protected static class ConfigurationContents {
        private long timestamp;
        private String clientId;
        private String activeGrouping;
        private Map<String, GroupingConfiguration> groupings;
    }

    @Data
    protected static class ClusterNodeCallback implements BrokerUtil.NodeCallback {
        @NonNull private final CommandExecutor commandExecutor;

        private void printInfo(String methodName, String message) {
            if (message!=null ) log.debug("{}(): {}", methodName, message);
            log.trace("{}(): Node properties: {}", methodName, commandExecutor.getClusterManager().getLocalMemberProperties());
            log.trace("{}(): Back-off flag: {}", methodName, commandExecutor.getClusterManager().getBrokerUtil().isBackOffSet());
        }

        @Override
        public void joinedCluster() {
            String nodeId = commandExecutor.getClusterManager().getLocalMember().id().id();
            log.info("joinedCluster(): Node joined cluster: {}", nodeId);
            commandExecutor.sendClientProperty("node-id", nodeId);
        }

        @Override
        public void leftCluster() {
            log.info("joinedCluster(): Node left cluster");
            commandExecutor.sendClientProperty("node-id", "");
        }

        @Override
        public void initialize() {
            printInfo("initialize", "INITIALIZE");

            log.info("initialize(): Node starts initializing as Aggregator...");
            commandExecutor.setActiveGrouping(commandExecutor.getAggregatorGrouping());
            log.info("initialize(): Node initialized as Aggregator");
        }

        @Override
        public void stepDown() {
            printInfo("stepDown", "STEP DOWN");

            log.info("stepDown(): Node is Aggregator. Start stepping down...");
            commandExecutor.setActiveGrouping(commandExecutor.getNodeGrouping());
            log.info("stepDown(): Node stepped down");
        }

        @Override
        public void statusChanged(BrokerUtil.NODE_STATUS oldStatus, BrokerUtil.NODE_STATUS newStatus) {
            log.debug("statusChanged(): Status changed: {} --> {}", oldStatus, newStatus);
            commandExecutor.nodeStatusChanged(oldStatus, newStatus);
        }

        @Override
        public void clusterChanged(ClusterMembershipEvent event) {
            log.debug("clusterChanged(): Cluster changed: {} --> {}", event.type(), event.subject().id().id());
            if (commandExecutor.getClusterManager().getBrokerUtil().getLocalStatus()== BrokerUtil.NODE_STATUS.AGGREGATOR) {
                if (event.type() == ClusterMembershipEvent.Type.MEMBER_ADDED) {
                    log.debug("clusterChanged(): Broadcast MEMBER_ADDED in event bus: {}", event.subject().id().id());
                    commandExecutor.getEventBus().send(EVENT_CLUSTER_NODE_ADDED, event);
                } else
                if (event.type() == ClusterMembershipEvent.Type.MEMBER_REMOVED) {
                    log.debug("clusterChanged(): Broadcast MEMBER_REMOVED in event bus: {}", event.subject().id().id());
                    commandExecutor.getEventBus().send(EVENT_CLUSTER_NODE_REMOVED, event);
                }
            }
        }

        @Override
        public String getConfiguration(Member local) {
            printInfo("getConfiguration", null);

            String brokerConfig = commandExecutor.getBrokerConfigurationAsString();
            log.trace("getConfiguration(): Config. string: {}", brokerConfig);
            return brokerConfig;
        }

        @Override
        public void setConfiguration(String newConfig) {
            printInfo("setConfiguration", "SET CONFIG: "+newConfig);

            // Update broker connection configuration for aggregator grouping
            commandExecutor.setBrokerConfigurationFromString(newConfig);
        }
    }
}
