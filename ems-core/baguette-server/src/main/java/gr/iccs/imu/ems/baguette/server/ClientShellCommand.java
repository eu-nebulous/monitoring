/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import gr.iccs.imu.ems.baguette.server.coordinator.cluster.IClusterZone;
import gr.iccs.imu.ems.common.recovery.RecoveryConstant;
import gr.iccs.imu.ems.util.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionAware;
import org.cryptacular.util.CertUtil;
import org.slf4j.event.Level;

import javax.validation.constraints.NotBlank;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
public class ClientShellCommand implements Command, Runnable, ServerSessionAware {

    private final static Object LOCK = new Object();
    private final static AtomicLong counter = new AtomicLong(0);
    private final static Set<ClientShellCommand> activeCmdList = new HashSet<>();
    private final static Map<String,ClientShellCommand> activeCmdMap = new HashMap<>();
    private final static long INPUT_CHECK_DELAY = 100;

    public static Set<ClientShellCommand> getActive() {
        return Collections.unmodifiableSet(activeCmdList);
    }

    public static Set<String> getActiveIds() {
        return Collections.unmodifiableSet(activeCmdMap.keySet());
    }

    public static ClientShellCommand getActiveByIpAddress(@NotBlank String address) {
        return activeCmdMap.get(address);
    }

    public static ClientShellCommand getActiveById(@NotBlank String id) {
        return activeCmdList.stream().filter(csc->csc.getId().equals(id)).findFirst().orElse(null);
    }

    private InputStream in;
    private PrintStream out;
    private PrintStream err;
    private ExitCallback callback;
    private final AtomicBoolean callbackCalled = new AtomicBoolean(false);

    @Getter @Setter
    private String id;
    @Getter @Setter
    private boolean echoOn = false;

    private String clientId;
    @Getter private String clientBrokerUrl;
    @Getter private String clientBrokerUsername;
    @Getter private String clientBrokerPassword;
    private String clientIpAddress;
    private String clientHostname;
    private String clientCanonicalHostname;
    private int clientPort = -1;
    @Getter private String clientCertificate;   // Broker certificate of Client

    @Getter @Setter private int clientClusterNodePort;
    @Getter @Setter private String clientClusterNodeAddress;
    @Getter @Setter private String clientClusterNodeHostname;
    @Getter @Setter private IClusterZone clientZone;
    @Getter private String clientNodeStatus;
    @Getter private String clientGrouping;
    private final Properties clientProperties = new Properties();

    private final ServerCoordinator coordinator;
    private final boolean clientAddressOverrideAllowed;
    @Getter
    private ServerSession session;
    @Getter @Setter
    private boolean closeConnection = false;

    private final Map<String,Object> inputsMap = new HashMap<>();
    private final EventBus<String,Object,Object> eventBus;
    @Getter
    private Exception lastException;
    @JsonIgnore
    private final transient NodeRegistry nodeRegistry;
    @Setter
    private NodeRegistryEntry nodeRegistryEntry;

    @Getter
    private Map<String, Object> clientStatistics;

    public ClientShellCommand(ServerCoordinator coordinator, boolean allowClientOverrideItsAddress, EventBus<String,Object,Object> eventBus, NodeRegistry registry) {
        synchronized (LOCK) {
            id = String.format("#%05d", counter.getAndIncrement());
        }
        this.coordinator = coordinator;
        this.clientAddressOverrideAllowed = allowClientOverrideItsAddress;
        this.eventBus = eventBus;
        this.nodeRegistry = registry;
    }

    @JsonIgnore
    public NodeRegistry getNodeRegistry() {
        return nodeRegistry;
    }

    public void setSession(ServerSession session) {
        log.info("{}--> Got session : {}", id, session);
        this.session = session;
        eventBus.send("BAGUETTE_SERVER_CLIENT_SESSION_STARTED", this);

		/*try {
			String clientIpAddr = ((InetSocketAddress)session.getIoSession().getRemoteAddress()).getAddress().getHostAddress();
			int clientPort = ((InetSocketAddress)session.getIoSession().getRemoteAddress()).getPort();
			log.info("{}--> Client connection : {}:{}", id, clientIpAddr, clientPort);
			String username = session.getUsername();
			log.info("{}--> Client session username: {}", username);
		} catch (Exception ex) {}*/

        session.addSessionListener(new SessionListener() {
            @Override
            public void sessionException(Session session, Throwable t) {
                log.warn("{}--> SessionListener: sessionException Throwable: ", id, t);
            }
            @Override
            public void sessionClosed(Session session) {
                log.info("{}--> SessionListener: sessionClosed", id);
            }
        });

        // Initialize NodeRegistryEntry for this CSC
        initNodeRegistryEntry();
    }

    private void initNodeRegistryEntry() {
        String address = getClientIpAddress();
        NodeRegistryEntry entry = coordinator.getServer().getNodeRegistry().getNodeByAddress(address);
        log.debug("{}--> initNodeRegistryEntry: Node registry entry for CSC: address={}, entry={}", id, address, entry);
        log.trace("{}--> initNodeRegistryEntry: Current nodeRegistryEntry: {}", id, entry);
        if (entry!=null) {
            setNodeRegistryEntry(entry);
        } else {
            log.error("{}--> initNodeRegistryEntry: No node registry entry found for client: address={}", id, address);
            log.error("{}--> initNodeRegistryEntry: Marked client session for immediate close: address={}", id, address);
            setCloseConnection(true);
        }
    }

    public void setInputStream(InputStream in) {
        this.in = in;
    }

    public void setOutputStream(OutputStream out) {
        this.out = new PrintStream(out, true);
    }

    public void setErrorStream(OutputStream err) {
        this.err = new PrintStream(err, true);
    }

    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(ChannelSession channelSession, Environment environment) throws IOException {
        new Thread(this).start();
    }

    @Override
    public void destroy(ChannelSession channelSession) throws Exception {
    }

    public void run() {
        // Check if session has been marked for immediate close
        if (closeConnection) {
            log.warn("{}--> Exiting immediately because 'closeConnection' flag is set", id);
            eventBus.send("BAGUETTE_SERVER_CLIENT_SESSION_CLOSING_IMMEDIATELY", this);
            coordinator.unregister(this);
            if (this.session!=null && this.session.isOpen()) {
                try {
                    this.session.close();
                } catch (IOException e) {
                    log.warn("Closing session caused on exception: ", e);
                }
                this.session = null;
            }
            if (!callbackCalled.getAndSet(true)) {
                callback.onExit(2);
            }
            log.info("{}--> Thread stopped immediately", id);
            eventBus.send("BAGUETTE_SERVER_CLIENT_SESSION_CLOSED_IMMEDIATELY", this);
            return;
        }

        // Add this CSC in active list
        synchronized (activeCmdList) {
            if (activeCmdMap.containsKey(getClientIpAddress()) || activeCmdMap.containsValue(this))
                throw new IllegalArgumentException("ClientShellCommand has already been registered");
            activeCmdList.add(this);
            activeCmdMap.put(getClientIpAddress(), this);
        }
        eventBus.send("BAGUETTE_SERVER_CLIENT_STARTING", this);
        getNodeRegistryEntry().nodeRegistering(null);

        // Process client input
        try {
            log.info("{}==> Thread started", id);
            out.printf("CLIENT (%s) : START\n", id);

            this.clientIpAddress = getClientIpAddress();

            // Enter the main processing loop
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            boolean helloReceived = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                log.debug("{}--> {}", id, line);

                // Echo command (if configured)
                //if (echoOn) out.printf("CLIENT (%s) : ECHO : %s\n", id, line);
                if (echoOn) out.printf("ECHO %s\n", line);
                //if (line.equalsIgnoreCase("exit")) break;

                if (!helloReceived && line.startsWith("-HELLO FROM CLIENT:")) {
                    // Process the Greeting line from client -- It must be the first line received
                    helloReceived = true;
                    getClientInfoFromGreeting(line.substring("-HELLO FROM CLIENT:".length()));

                    // Register CSC to Coordinator
                    coordinator.register(this);
                    eventBus.send("BAGUETTE_SERVER_CLIENT_REGISTERED", this);
                    getNodeRegistryEntry().nodeRegistered(null);

                    // Instruct client to start sending statistics
                    sendCommand("SEND-STATS START");
                } else {
                    // Process the subsequent lines from client -- After the Greeting line
                    processClientInput(line);
                }
            }
            // Client connection closed
            try {
                eventBus.send("BAGUETTE_SERVER_CLIENT_EXITING", this);
                NodeRegistryEntry entry = getNodeRegistryEntry();
                if (! entry.isArchived())
                    entry.nodeExiting(null);
                else
                    log.warn("{}==> Node is archived", id);
            } catch (Exception e) {
                log.warn("{}==> EXCEPTION: ", id, e);
            }

            log.info("{}==> Signaling client to exit", id);
            out.println("EXIT");

        } catch (Exception ex) {
            log.warn("{}==> EXCEPTION : ", id, ex);
            out.printf("EXCEPTION %s\n", ex);
            this.lastException = ex;
            eventBus.send("BAGUETTE_SERVER_CLIENT_EXCEPTION", this);
            NodeRegistryEntry entry = getNodeRegistryEntry();
            if (entry.getState()==NodeRegistryEntry.STATE.REGISTERING) entry.nodeRegistrationError(ex);
            else entry.nodeDisconnected(ex);
        } finally {
            // Remove CSC from active list
            synchronized (activeCmdList) {
                activeCmdList.remove(this);
                activeCmdMap.remove(getClientIpAddress());
            }
            log.info("{}--> Thread stops", id);

            // Unregister from Coordinator
            coordinator.unregister(this);
            eventBus.send("BAGUETTE_SERVER_CLIENT_UNREGISTERED", this);

            // Invoke callback if provided
            if (!callbackCalled.getAndSet(true)) {
                callback.onExit(0);
            }
            eventBus.send("BAGUETTE_SERVER_CLIENT_EXITED", this);
            if (getNodeRegistryEntry().getState()==NodeRegistryEntry.STATE.EXITING)
                getNodeRegistryEntry().nodeExited(null);
        }
    }

    private void processClientInput(String line) throws IOException, ClassNotFoundException {
        if (line.startsWith("-INPUT:")) {
            String input = line.substring("-INPUT:".length());
            String[] part = input.split(":",2 );
            inputsMap.put(part[0].trim(), SerializationUtil.deserializeFromString(part[1]));
        } else if (StringUtils.startsWithIgnoreCase(line, "SERVER-")) {
            String[] lineArgs = line.split(" ", 2);
            if ("SERVER-GET-NODE-SSH-CREDENTIALS".equalsIgnoreCase(lineArgs[0].trim()) && lineArgs.length>1) {
                String nodeAddress = lineArgs[1].trim();
                if (!nodeAddress.isEmpty()) {
                    NodeRegistryEntry entry = nodeRegistry.getNodeByAddress(nodeAddress);
                    if (entry!=null) {
                        Map<String, String> preregInfo = entry.getPreregistration();
                        log.debug("{}--> NODE PRE-REGISTRATION INFO: address={}\n{}", getId(), nodeAddress, preregInfo);

                        if (preregInfo!=null) {
                            String preregInfoStr = new Gson().toJson(preregInfo);
                            log.trace("{}--> NODE PRE-REGISTRATION INFO STRING: STR={}\n{}", getId(), nodeAddress, preregInfoStr);
                            sendToClient(preregInfoStr);
                        } else {
                            log.warn("{}--> NO PRE-REGISTRATION INFO FOR NODE: {}", getId(), nodeAddress);
                            sendToClient("{}");
                        }
                    } else {
                        log.warn("{}--> UNKNOWN NODE: {}", getId(), nodeAddress);
                        sendToClient("{}");
                    }
                }
            }
        } else if (line.startsWith("-NOTIFY-GROUPING-CHANGE:")) {
            String newGrouping = line.substring("-NOTIFY-GROUPING-CHANGE:".length()).trim();
            log.info("{}--> Client grouping changed: {} --> {}", getId(), clientGrouping, newGrouping);
            if (StringUtils.isNotBlank(newGrouping) && ! StringUtils.equals(clientGrouping, newGrouping))
                this.clientGrouping = newGrouping;
        } else if (line.startsWith("-NOTIFY-STATUS-CHANGE:")) {
            String newNodeStatus = line.substring("-NOTIFY-STATUS-CHANGE:".length()).trim();
            log.info("{}--> Client status changed: {} --> {}", getId(), clientNodeStatus, newNodeStatus);
            if (StringUtils.isNotBlank(newNodeStatus) && ! StringUtils.equals(clientNodeStatus, newNodeStatus))
                this.clientNodeStatus = newNodeStatus;
        } else if (line.startsWith("-NOTIFY-X:")) {
            String message = line.substring("-NOTIFY-X:".length()).trim();
            String[] part = message.split(" ", 2);
            String command = part[0].trim();
            String args = part.length>1 ? part[1] : null;
            log.info("{}--> Client notification: CMD={}, ARGS={}", getId(), command, args);

            if ("DEBUG".equalsIgnoreCase(command)) {
                log.debug("{}--> {}", getId(), args);
            } else
            if ("INFO".equalsIgnoreCase(command)) {
                log.info("{}--> {}", getId(), args);
            } else
            if ("WARN".equalsIgnoreCase(command)) {
                log.warn("{}--> {}", getId(), args);
            } else
            if ("ERROR".equalsIgnoreCase(command)) {
                log.error("{}--> {}", getId(), args);
            } else
            if ("RECOVERY".equalsIgnoreCase(command)) {
                args = args==null ? "" : args;
                part = args.split(" ", 2);
                String notificationType = part[0].trim();
                String clientData = part.length>1 ? part[1] : null;
                if (StringUtils.isNotBlank(notificationType) && StringUtils.isNotBlank(clientData)) {
                    log.info("{}--> Client Recovery Notification: {}: {}", getId(), notificationType, clientData);
                    if ("GIVE_UP".equalsIgnoreCase(notificationType)) {
                        String[] tmp = clientData.split("@", 2);
                        String nodeId = tmp[0].trim();
                        String nodeAddress = tmp.length>1 ? tmp[1].trim() : null;
                        if (StringUtils.isNotBlank(nodeAddress))
                            eventBus.send(RecoveryConstant.SELF_HEALING_RECOVERY_GIVE_UP, nodeAddress, "Client_" + getId());
                        else
                            log.warn("{}--> Missing Node Address in Client Recovery Notification: {}", getId(), args);
                    } else
                        log.warn("{}--> UNKNOWN Client Recovery Notification: {}", getId(), args);
                } else {
                    log.warn("{}--> INVALID Client Recovery Notification: {}", getId(), args);
                }
            } else
            {
                log.warn("{}--> UNKNOWN Client Notification type: {}", getId(), message);
            }

        } else if (line.startsWith("-CLIENT-PROPERTY-CHANGE:")) {
            String[] part = line.substring("-CLIENT-PROPERTY-CHANGE:".length()).trim().split(" ", 2);
            String propertyName = part[0];
            String propertyValue = part.length > 1 ? part[1] : null;
            String oldValue = clientProperties.getProperty(propertyName);
            if (StringUtils.isNotBlank(propertyName)) {
                log.info("{}--> Client property changed: {} = {} --> {}", getId(), propertyName, oldValue, propertyValue);
                clientProperties.put(propertyName.trim(), propertyValue);
            } else {
                log.warn("{}--> Invalid Client property: input line: ", line);
            }
        } else if (line.startsWith("-STATS:")) {
            String statsStr = line.substring("-STATS:".length());
            Object statsObj = SerializationUtil.deserializeFromString(statsStr);
            if (statsObj instanceof Map) {
                Map<String, Object> statsMap = StrUtil.castToMapStringObject(statsObj);
                statsMap.put("_received_at_server_timestamp", System.currentTimeMillis());
                log.debug("{}--> Client STATS received: {}", getId(), statsMap);
                this.clientStatistics = statsMap;
            } else if (statsObj==null) {
                log.debug("{}--> Client STATS object is NULL", getId());
            } else {
                log.error("{}--> Unsupported Client STATS object: class={}, object={}", getId(), statsObj.getClass().getName(), statsObj);
            }
        } else if (line.equalsIgnoreCase("READY")) {
            coordinator.clientReady(this);
        } else {
            coordinator.processClientInput(this, line);
        }
    }

    protected void getClientInfoFromGreeting(String greetingInfo) {
        if (StringUtils.isBlank(greetingInfo)) return;
        String[] clientInfo = greetingInfo.trim().split(" ");

        for (String s : clientInfo) {
            if (StringUtils.isBlank(s)) continue;
            if (s.startsWith("id=")) {
                this.clientId = s.substring("id=".length()).replace("~~", " ");
                log.info("{}--> Client Id: {}", id, clientId);
            } else
            if (s.startsWith("broker=")) {
                this.clientBrokerUrl = s.substring("broker=".length());
                log.info("{}--> Broker URL: {}", id, clientBrokerUrl);
            } else
            if (s.startsWith("address=")) {
                if (clientAddressOverrideAllowed) {
                    String addr = s.substring("address=".length());
                    if (StringUtils.isNotBlank(addr)) {
                        this.clientIpAddress = addr.trim();
                        log.info("{}--> Effective IP: {}", id, clientIpAddress);
                    }
                }
            } else
            if (s.startsWith("port=")) {
                if (clientAddressOverrideAllowed) {
                    try {
                        int port = Integer.parseInt(s.substring("port=".length()));
                        if (port>0 && port<65536) {
                            this.clientPort = port;
                            log.info("{}--> Effective Port: {}", id, clientPort);
                        }
                    } catch (Exception ex) {
                        log.warn("{}--> Invalid Port value: {}: {}", id, s.substring("port=".length()), ex.getMessage());
                    }
                }
            } else
            if (s.startsWith("username=")) {
                this.clientBrokerUsername = s.substring("username=".length());
                log.info("{}--> Broker Username: {}", id, clientBrokerUsername);
            } else
            if (s.startsWith("password=")) {
                this.clientBrokerPassword = s.substring("password=".length());
                log.info("{}--> Broker Password: {}", id, PasswordUtil.getInstance().encodePassword(clientBrokerPassword));
            } else
            if (s.startsWith("cert=")) {
                this.clientCertificate = s.substring("cert=".length())
                        .replace("~~", " ")
                        .replace("##", "\r\n")
                        .replace("$$", "\n");
                log.info("{}--> Broker Cert.: {}", id, clientCertificate);

                // Get certificate alias from client Id or IP address
                String alias = /*StringUtils.isNotBlank(clientId)
                        ? clientId.trim()
                        :*/ getClientIpAddress();
                log.info("{}--> Adding/Replacing client certificate in Truststore: alias={}", id, alias);

                if (StringUtils.isNotEmpty(clientCertificate)) {
                    // Add certificate to truststore
                    try {
                        X509Certificate cert = (X509Certificate) coordinator
                                .getServer()
                                .getBrokerCepService()
                                .addOrReplaceCertificateInTruststore(alias, clientCertificate);
                        log.info("{}--> Added/Replaced client certificate in Truststore: alias={}, CN={}, certificate-names={}",
                                id, alias, cert.getSubjectX500Principal().getName(), CertUtil.subjectNames(cert));
                    } catch (Exception e) {
                        log.warn("{}--> EXCEPTION while adding/replacing certificate in Trust store: alias={}, exception: ",
                                clientId, alias, e);
                    }
                } else {
                    log.info("{}--> Client PEM certificate is empty. Leaving truststore unchanged", id);
                }
            } else {
                log.warn("{}--> Unknown HELLO argument will be ignored: {}", id, s);
            }
        }

        if (StringUtils.isBlank(this.clientId) || "null".equalsIgnoreCase(this.clientId))
            this.clientId = getClientId();
        if (StringUtils.isBlank(this.clientIpAddress) || "null".equalsIgnoreCase(this.clientIpAddress))
            this.clientIpAddress = getClientIpAddress();
        if (this.clientPort<=0 || this.clientPort>65535)
            this.clientPort = getClientPort();
    }

    public String getClientId() {
        if (StringUtils.isNotBlank(clientId)) return clientId;
        clientId = getId();
        return clientId;
    }

    public String getClientIpAddress() {
        if (StringUtils.isNotBlank(clientIpAddress)) return clientIpAddress;
        clientIpAddress = ((InetSocketAddress) getSession().getIoSession().getRemoteAddress()).getAddress().getHostAddress();
        return clientIpAddress;
    }

    public String getClientHostname() {
        if (StringUtils.isNotBlank(clientHostname)) return clientHostname;
        clientHostname = ((InetSocketAddress) getSession().getIoSession().getRemoteAddress()).getAddress().getHostName();
        return clientHostname;
    }

    public String getClientCanonicalHostname() {
        if (StringUtils.isNotBlank(clientCanonicalHostname)) return clientCanonicalHostname;
        clientCanonicalHostname = ((InetSocketAddress) getSession().getIoSession().getRemoteAddress()).getAddress().getCanonicalHostName();
        return clientCanonicalHostname;
    }

    public int getClientPort() {
        if (clientPort > 0) return clientPort;
        clientPort = ((InetSocketAddress) getSession().getIoSession().getRemoteAddress()).getPort();
        return clientPort;
    }

    public String getClientProperty(@NonNull String propertyName) { return clientProperties.getProperty(propertyName); }
    public String getClientProperty(@NonNull String propertyName, String defaultValue) { return clientProperties.getProperty(propertyName, defaultValue); }

    public NodeRegistryEntry getNodeRegistryEntry() {
        if (nodeRegistryEntry!=null)
            return nodeRegistryEntry;

        //XXX:BUG: Following code seems not working...
        String clientId = getClientId();
        if (StringUtils.isNotBlank(clientId)) {
            return nodeRegistry.getNodeByClientId(clientId);
        }
        return null;
    }

    public void sendToClient(String msg) {
        sendToClient(msg, Level.INFO);
    }

    public void sendToClient(String msg, Level logLevel) {
        if (msg == null || (msg = msg.trim()).isEmpty()) return;
        switch (logLevel) {
            case TRACE -> log.trace("{}==> PUSH : {}", id, msg);
            case DEBUG -> log.debug("{}==> PUSH : {}", id, msg);
            case WARN -> log.warn("{}==> PUSH : {}", id, msg);
            case ERROR -> log.error("{}==> PUSH : {}", id, msg);
            default -> log.info("{}==> PUSH : {}", id, msg);
        }
        out.println(msg);
    }

    public void sendCommand(String cmd) {
        sendToClient(cmd);
    }

    public void sendCommand(String cmd, Level logLevel) {
        sendToClient(cmd, logLevel);
    }

    public void sendCommand(String[] cmd) {
        sendToClient(String.join(" ", cmd));
    }

    public void sendCommand(String[] cmd, Level logLevel) {
        sendToClient(String.join(" ", cmd), logLevel);
    }

    public Object readFromClient(String cmd, Level logLevel) {
        String uuid = UUID.randomUUID().toString();
        log.trace("ClientShellCommand.readFromClient: uuid={}, cmd={}", uuid, cmd);
        Object oldValue = inputsMap.remove(uuid);
        log.trace("ClientShellCommand.readFromClient: uuid={}, old-inputMap-value={}", uuid, oldValue);
        log.trace("ClientShellCommand.readFromClient: uuid={}, inputMap-BEFORE={}", uuid, inputsMap);
        sendCommand(cmd+" "+uuid, logLevel);
        log.trace("ClientShellCommand.readFromClient: uuid={}, Command sent to client", uuid);
        while (!inputsMap.containsKey(uuid)) {
            log.trace("ClientShellCommand.readFromClient: uuid={}, No input, waiting 500ms", uuid);
            try { Thread.sleep(INPUT_CHECK_DELAY); } catch (InterruptedException e) { }
        }
        log.trace("ClientShellCommand.readFromClient: uuid={}, inputMap-BEFORE={}", uuid, inputsMap);
        Object input = inputsMap.remove(uuid);
        log.trace("ClientShellCommand.readFromClient: uuid={}, Input found: {}", uuid, input);
        return input;
    }

    protected String _propertiesToBase64(Properties params) {
        if (params != null && params.size() > 0) {
            StringWriter writer = new StringWriter();
            try {
                params.store(writer, null);
            } catch (IOException e) {
                log.error("Could not serialize parameters: ", e);
            }
            String paramsStr = writer.getBuffer().toString();
            return Base64.getEncoder().encodeToString(paramsStr.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    public void sendParams(Properties params) {
        log.debug("sendParams: id={}, parameters={}", id, params);
        String paramsStr = _propertiesToBase64(params);
        if (paramsStr != null) {
            sendToClient("SET-PARAMS " + paramsStr);
        }
    }

    /**
     * Write an object to a Base64 string.
     */
    public static String serializeToString(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * Read the object from Base64 string.
     */
    public static Object unserializeFromString(String s) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public static void sendClientConfigurationToClients(@NonNull ClientConfiguration cc, @NonNull List<ClientShellCommand> clients) {
        List<String> clientIds = clients.stream().map(ClientShellCommand::getClientId).collect(Collectors.toList());
        log.debug("sendClientConfigurationToClients: clients={}, client-config={}", clientIds, cc);
        try {
            String ccStr = serializeToString(cc);
            log.debug("sendClientConfigurationToClients: Serialization of Client configuration: {}", ccStr);
            ccStr = "SET-CLIENT-CONFIG " + ccStr;
            for (ClientShellCommand csc : clients) {
                log.info("sendClientConfigurationToClients: Sending Client configuration to client: {}", csc.getClientId());
                csc.sendToClient(ccStr);
            }
            log.info("sendClientConfigurationToClients: Client configuration sent to clients: {}", clientIds);
        } catch (IOException ex) {
            log.error("sendClientConfigurationToClients: Exception while serializing Client configuration: ", ex);
            log.error("sendClientConfigurationToClients: SET-CLIENT-CONFIG command *NOT* sent to clients");
        }
    }

    public void sendClientConfiguration(ClientConfiguration cc) {
        log.debug("sendClientConfiguration: id={}, client-config={}", id, cc);
        try {
            String ccStr = serializeToString(cc);
            log.info("sendClientConfiguration: Serialization of Client configuration: {}", ccStr);
            sendToClient("SET-CLIENT-CONFIG " + ccStr);
        } catch (IOException ex) {
            log.error("sendClientConfiguration: Exception while serializing Client configuration: ", ex);
            log.error("sendClientConfiguration: SET-CLIENT-CONFIG command *NOT* sent to client");
        }
    }

    public void sendGroupingConfiguration(String grouping, Map<String, GroupingConfiguration.BrokerConnectionConfig> connectionConfigs, BaguetteServer server) {
        GroupingConfiguration gc = GroupingConfigurationHelper.newGroupingConfiguration(grouping, connectionConfigs, server);
        sendGroupingConfiguration(gc);
    }

    public void sendGroupingConfiguration(GroupingConfiguration gc) {
        String grouping = gc.getName();
        log.debug("sendGroupingConfiguration: id={}, grouping={}, grouping-config={}", id, grouping, gc);
        try {
            String allStr = serializeToString(gc);
            log.info("sendGroupingConfiguration: Serialization of Grouping configuration for {}: {}", grouping, allStr);
            sendToClient("SET-GROUPING-CONFIG " + allStr);
        } catch (IOException ex) {
            log.error("sendGroupingConfiguration: Exception while serializing Grouping configuration: ", ex);
            log.error("sendGroupingConfiguration: SET-GROUPING-CONFIG command *NOT* sent to client");
        }
    }

    public void sendConstants(Map<String, Double> constants) {
        log.debug("sendConstants: constants={}", constants);
        HashMap<String, Object> all = new HashMap<>();
        all.put("constants", constants);

        try {
            String allStr = serializeToString(all);
            log.info("sendConstants: Serialization of Constants: {}", allStr);
            sendToClient("SET-CONSTANTS " + allStr);
        } catch (IOException ex) {
            log.error("sendConstants: Exception while serializing Constants: ", ex);
            log.error("sendConstants: SET-CONSTANTS command *NOT* sent to client");
        }
    }

    public void setClientId(String id) {
        if (id != null && !id.trim().isEmpty())
            sendToClient("SET-ID " + id.trim());
    }

    public void setRole(String role) {
        if (role != null && !role.trim().isEmpty()) sendToClient("SET-ROLE " + role.trim().toUpperCase());
    }

    public void setActiveGrouping(String grouping) {
        if (grouping != null && !grouping.trim().isEmpty())
            sendToClient("SET-ACTIVE-GROUPING " + grouping.trim().toUpperCase());
    }

    public void stop(String msg) {
        log.info("{}==> STOP : {}", id, msg);
        out.println("EXIT " + msg);
        if (!callbackCalled.getAndSet(true)) {
            callback.onExit(1);
        }
    }

    public String toString() {
        return "ClientShellCommand_" + id;
    }

    public String toStringCluster() {
        return getClientClusterNodeAddress()+":"+getClientClusterNodePort();
    }
}
