/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client;

import gr.iccs.imu.ems.brokercep.BrokerCepService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.simple.SimpleClient;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.mina.MinaServiceFactoryFactory;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.security.PublicKey;
import java.util.Optional;


/**
 * Custom SSH client
 */
@Slf4j
@Service
public class Sshc implements gr.iccs.imu.ems.common.client.SshClient<BaguetteClientProperties> {
    private BaguetteClientProperties config;
    private SshClient client;
    private SimpleClient simple;
    private ClientSession session;
    private ClientChannel channel;
    private boolean started = false;
    @Autowired
    private CommandExecutor commandExecutor;
    @Autowired
    private BrokerCepService brokerCepService;

    @Getter
    private InputStream in;
    @Getter
    private PrintStream out;
    @Getter
    private PrintStream err;
    @Getter
    private String clientId;

    @Getter @Setter
    private boolean useServerKeyVerifier = true;

    @Override
    public void setConfiguration(BaguetteClientProperties config) {
        log.trace("Sshc: New config: {}", config);
        this.config = config;
        this.clientId = config.getClientId();
        log.trace("Sshc: cmd-exec: {}", commandExecutor);
        if (this.commandExecutor!=null) this.commandExecutor.setConfiguration(config);
    }

    public synchronized void start(boolean retry) throws IOException {
        if (retry) {
            log.trace("Starting client in retry mode");
            long retryPeriod = config.getRetryPeriod();
            while (!started) {
                log.debug("(Re-)trying to start client....");
                try {
                    start();
                } catch (Exception ex) {
                    log.warn("{}", ex.getMessage());
                }
                if (started) break;
                log.trace("Failed to start. Sleeping for {}ms...", retryPeriod);
                try {
                    Thread.sleep(retryPeriod);
                } catch (InterruptedException ex) {
                    log.debug("Sleep: ", ex);
                }
            }
        } else {
            start();
        }
        if (started) log.trace("Client started");
    }

    @Override
    public synchronized void start() throws IOException {
        if (started) return;
        log.info("Connecting to server...");

        String host = config.getServerAddress();
        int port = config.getServerPort();
        String serverPubKey = StringEscapeUtils.unescapeJson(config.getServerPubkey());
        String serverPubkeyFingerprint = config.getServerPubkeyFingerprint();
        String serverPubKeyAlgorithm = config.getServerPubkeyAlgorithm();
        String serverPubKeyFormat = config.getServerPubkeyFormat();
        String username = config.getServerUsername();
        String password = config.getServerPassword();
        long connectTimeout = config.getConnectTimeout();
        long authTimeout = config.getAuthTimeout();
        long heartbeatInterval = config.getHeartbeatInterval();
        long heartbeatReplyWait = config.getHeartbeatReplyWait();

        // Starting client and connecting to server
        this.client = SshClient.setUpDefaultClient();
        client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);

        if (useServerKeyVerifier) {
            // Get configured server public key
            PublicKey pubKey = getPublicKeyFromString(serverPubKeyAlgorithm, serverPubKeyFormat, serverPubKey);

            // Provided server key verifiers
            //client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            //client.setServerKeyVerifier(new RequiredServerKeyVerifier(pubKey));

            // Custom server key verifier
            client.setServerKeyVerifier( getCustomServerKeyVerifier(serverPubkeyFingerprint, pubKey) );
        }

        this.simple = SshClient.wrapAsSimpleClient(client);
        //simple.setConnectTimeout(connectTimeout);
        //simple.setAuthenticationTimeout(authTimeout);

        // Set a huge idle timeout, keep-alive to true and heartbeat to configured value
        PropertyResolverUtils.updateProperty(client, CoreModuleProperties.HEARTBEAT_INTERVAL.getName(), heartbeatInterval);      // Prevents server-side connection closing
        PropertyResolverUtils.updateProperty(client, CoreModuleProperties.HEARTBEAT_REPLY_WAIT.getName(), heartbeatReplyWait);   // Prevents client-side connection closing
        PropertyResolverUtils.updateProperty(client, CoreModuleProperties.IDLE_TIMEOUT.getName(), Integer.MAX_VALUE);
        PropertyResolverUtils.updateProperty(client, CoreModuleProperties.SOCKET_KEEPALIVE.getName(), true);               // Socket keep-alive at OS-level
        log.debug("Set IDLE_TIMEOUT to MAX, SOCKET-KEEP-ALIVE to true, and HEARTBEAT to {}", heartbeatInterval);

        // Explicitly set IO service factory factory to prevent conflict between MINA and Netty options
        client.setIoServiceFactoryFactory(new MinaServiceFactoryFactory());

        // Start SSH client
        client.start();

        // Authenticate and start session
        this.session = client.connect(username, host, port)
                .verify(connectTimeout)
                .getSession();
        session.addPasswordIdentity(password);
        session.auth()
                .verify(authTimeout);

        // Open command shell channel
        this.channel = session.createChannel(ClientChannel.CHANNEL_SHELL);
        PipedInputStream pIn = new PipedInputStream();
        PipedOutputStream pOut = new PipedOutputStream();
        //PipedOutputStream pErr = new PipedOutputStream();
        this.in = new BufferedInputStream(pIn);
        this.out = new PrintStream(pOut, true);
        //this.err = new PrintStream(pErr, true);

        channel.setIn(new PipedInputStream(pOut));
        channel.setOut(new PipedOutputStream(pIn));
        //channel.setErr(new PipedOutputStream(pErr));

        channel.open();

        log.info("SSH client is ready");
        this.started = true;
    }

    private static ServerKeyVerifier getCustomServerKeyVerifier(String serverPubkeyFingerprint, PublicKey pubKey) {
        return (clientSession, remoteAddress, publicKey) -> {
            // boolean verifyServerKey(ClientSession clientSession, SocketAddress socketAddress, PublicKey publicKey)
            log.info("verifyServerKey(): remoteAddress: {}", remoteAddress.toString());

            // Check server public key fingerprint matches with the one in configuration
            if (StringUtils.isNoneBlank(serverPubkeyFingerprint)) {
                String fingerprint = KeyUtils.getFingerPrint(publicKey);
                log.debug("verifyServerKey(): publicKey: fingerprint: {}", fingerprint);
                if (fingerprint != null && KeyUtils.checkFingerPrint(serverPubkeyFingerprint, publicKey).getKey() != null)
                    log.debug("verifyServerKey(): publicKey: fingerprint: MATCH");
                else
                    log.warn("verifyServerKey(): publicKey: fingerprint: NO MATCH");
            }

            // Check that server public key matches with the one in configuration
            try {
                // Compare session provided and configured public keys
                log.debug("verifyServerKey(): configured server public key: {}", pubKey);
                log.debug("verifyServerKey():   received server public key: {}", publicKey);
                boolean match = KeyUtils.compareKeys(pubKey, publicKey);
                log.debug("verifyServerKey(): Server keys match? {}", match);
                return match;
            } catch (Exception e) {
                log.error("verifyServerKey(): publicKey: EXCEPTION: ", e);
                return false;
            }
        };
    }

    private static PublicKey getPublicKeyFromString(String serverPubKeyAlgorithm, String serverPubKeyFormat, String serverPubKey) throws IOException {
        log.debug("getPublicKeyFromString(): serverPubKeyAlgorithm: {}", serverPubKeyAlgorithm);
        log.debug("getPublicKeyFromString():    serverPubKeyFormat: {}", serverPubKeyFormat);
        log.debug("getPublicKeyFromString():          serverPubKey:\n{}", serverPubKey);

        // Retrieve configured public key - First implementation
        PEMParser pemParser = new PEMParser(new StringReader(serverPubKey));
        PemObject pemObject = pemParser.readPemObject();
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(pemObject.getContent());
        PublicKey pubKey = converter.getPublicKey(publicKeyInfo);

        // Retrieve configured public key - Alternative implementation
        /*KeyFactory factory = KeyFactory.getInstance(serverPubKeyAlgorithm);
        PublicKey pubKey;
        try (StringReader keyReader = new StringReader(serverPubKey);
             PemReader pemReader = new PemReader(keyReader))
        {
            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(content);
            //or PKCS8EncodedKeySpec pubKeySpec = new PKCS8EncodedKeySpec(content);
            pubKey = factory.generatePublic(pubKeySpec);
        }*/

        log.debug("getPublicKeyFromString: Public key: {}", pubKey);
        return pubKey;
    }

    @Override
    public synchronized void stop() throws IOException {
        if (!started) return;
        this.started = false;
        log.info("Stopping SSH client...");

        channel.close(false).await();
        session.close(false);
        simple.close();
        client.stop();

        log.info("SSH client stopped");
    }

    public synchronized void greeting() {
        if (!started) return;
        String certOneLine = Optional
                .ofNullable(brokerCepService.getBrokerCertificate())
                .orElse("")
                .replace(" ","~~")
                .replace("\r\n","##")
                .replace("\n","$$");
        String clientAddress = config.getDebugFakeIpAddress();
        int clientPort = -1;
        out.printf("-HELLO FROM CLIENT: id=%s broker=%s address=%s port=%d username=%s password=%s cert=%s%n",
                clientId.replace(" ", "~~"),
                brokerCepService.getBrokerCepProperties().getBrokerUrlForClients(),
                StringUtils.isNotBlank(clientAddress) ? clientAddress : "",
                clientPort,
                brokerCepService.getBrokerUsername(),
                brokerCepService.getBrokerPassword(),
                certOneLine);
        out.flush();
    }

    public void run() throws IOException {
        if (!started) return;

        // Start communication protocol with Server
        // Execution waits here until connection is closed
        log.trace("run(): Calling communicateWithServer()...");
        commandExecutor.communicateWithServer(in, out, out);
        out.printf("-BYE FROM CLIENT: %s%n", clientId);
    }
}
