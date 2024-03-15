/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.installer;

import gr.iccs.imu.ems.baguette.client.install.*;
import gr.iccs.imu.ems.baguette.client.install.instruction.INSTRUCTION_RESULT;
import gr.iccs.imu.ems.baguette.client.install.instruction.Instruction;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsService;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsSet;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelSession;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.mina.MinaServiceFactoryFactory;
import org.apache.sshd.scp.client.DefaultScpClientCreator;
import org.apache.sshd.scp.client.ScpClient;
import org.apache.sshd.scp.client.ScpClientCreator;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SSH client installer
 */
@Slf4j
@Getter
public class SshClientInstaller implements ClientInstallerPlugin {
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS");

    private final ClientInstallationTask task;
    private final long taskCounter;

    private final int maxRetries;
    private final long retryDelay;
    private final double retryBackoffFactor;
    private final long connectTimeout;
    private final long authenticationTimeout;
    private final long heartbeatInterval;
    private final long heartbeatReplyWait;
    private final boolean simulateConnection;
    private final boolean simulateExecution;
    private final long commandExecutionTimeout;
    private final boolean continueOnFail;

    private final ClientInstallationProperties properties;

    private SshClient sshClient;
    //private SimpleClient simpleClient;
    private ClientSession session;
    //private ChannelShell shellChannel;
    private StreamLogger streamLogger;

    @Builder
    public SshClientInstaller(ClientInstallationTask task, long taskCounter, ClientInstallationProperties properties) {
        this.task = task;
        this.taskCounter = taskCounter;

        this.maxRetries = properties.getMaxRetries()>=0 ? properties.getMaxRetries() : 5;
        this.retryDelay = properties.getRetryDelay()>0 ? properties.getRetryDelay() : 1000L;
        this.retryBackoffFactor = properties.getRetryBackoffFactor()>0 ? properties.getRetryBackoffFactor() : 1.0;

        this.connectTimeout = properties.getConnectTimeout()>0 ? properties.getConnectTimeout() : 60000;
        this.authenticationTimeout = properties.getAuthenticateTimeout()>0 ? properties.getAuthenticateTimeout() : 60000;
        this.heartbeatInterval = properties.getHeartbeatInterval()>0 ? properties.getHeartbeatInterval() : 10000;
        this.heartbeatReplyWait = properties.getHeartbeatReplyWait()>0 ? properties.getHeartbeatReplyWait() : 10 * heartbeatInterval;
        this.simulateConnection = properties.isSimulateConnection();
        this.simulateExecution = properties.isSimulateExecution();
        this.commandExecutionTimeout = properties.getCommandExecutionTimeout()>0 ? properties.getCommandExecutionTimeout() : 120000;
        this.continueOnFail = properties.isContinueOnFail();

        this.properties = properties;
    }

    @Override
    public boolean executeTask(/*int retries*/) {
        if (! openSshConnection())
            return false;

        boolean success;
        try {
            INSTRUCTION_RESULT exitResult = executeInstructionSets();
            success = exitResult != INSTRUCTION_RESULT.FAIL;
        } catch (Exception ex) {
            log.error("SshClientInstaller: Failed executing installation instructions for task #{}, Exception: ", taskCounter, ex);
            success = false;
        }

        if (success) log.info("SshClientInstaller: Task completed successfully #{}", taskCounter);
        else log.info("SshClientInstaller: Error occurred while executing task #{}", taskCounter);
        return closeSshConnection(success);
    }

    protected boolean openSshConnection() {
        task.getNodeRegistryEntry().nodeInstalling(task.getNodeRegistryEntry().getPreregistration());
        boolean success = false;
        int retries = 0;
        while (!success && retries<=maxRetries) {
            if (retries>0) log.warn("SshClientInstaller: Retry {}/{} executing task #{}", retries, maxRetries, taskCounter);
            try {
                sshConnect();
                //sshOpenShell();
                success = true;
            } catch (Exception ex) {
                log.error("SshClientInstaller: Failed executing task #{}, Exception: ", taskCounter, ex);
                retries++;
                if (retries<=maxRetries)
                    waitToRetry(retries);
            }
        }
        if (!success) {
            log.error("SshClientInstaller: Giving up executing task #{} after {} retries", taskCounter, maxRetries);
            return false;
        }
        return true;
    }

    protected boolean closeSshConnection(boolean success) {
        try {
            //sshCloseShell();
            sshDisconnect();
            return success;
        } catch (Exception ex) {
            log.error("SshClientInstaller: Exception while disconnecting. Task #{}, Exception: ", taskCounter, ex);
            return false;
        }
    }

    private void waitToRetry(int retries) {
        long delay = Math.max(1, (long)(retryDelay * Math.pow(retryBackoffFactor, retries-1)));
        try {
            log.debug("SshClientInstaller: waitToRetry: Waiting for {}ms to retry", delay);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            log.warn("SshClientInstaller: waitToRetry: Waiting to retry interrupted: ", e);
        }
    }

    private boolean sshConnect() throws Exception {
        SshConfig config = task.getSsh();
        String host = config.getHost();
        int port = config.getPort();

        if (simulateConnection) {
            log.info("SshClientInstaller: Simulate connection to remote host: task #{}: host: {}:{}", taskCounter, host, port);
            return true;
        }

        // Get connection information
        String privateKey = config.getPrivateKey();
        String fingerprint = config.getFingerprint();
        String username = config.getUsername();
        String password = config.getPassword();

        // Create and configure SSH client
        this.sshClient = SshClient.setUpDefaultClient();
        sshClient.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
        sshClient.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);

        //this.simpleClient = SshClient.wrapAsSimpleClient(sshClient);
        //simpleClient.setConnectTimeout(connectTimeout);
        //simpleClient.setAuthenticationTimeout(authenticationTimeout);

        // Set a huge idle timeout, keep-alive to true and heartbeat to configured value
        PropertyResolverUtils.updateProperty(sshClient, CoreModuleProperties.HEARTBEAT_INTERVAL.getName(), heartbeatInterval);      // Prevents server-side connection closing
        PropertyResolverUtils.updateProperty(sshClient, CoreModuleProperties.HEARTBEAT_REPLY_WAIT.getName(), heartbeatReplyWait);   // Prevents client-side connection closing
        PropertyResolverUtils.updateProperty(sshClient, CoreModuleProperties.IDLE_TIMEOUT.getName(), Integer.MAX_VALUE);
        PropertyResolverUtils.updateProperty(sshClient, CoreModuleProperties.SOCKET_KEEPALIVE.getName(), true);               // Socket keep-alive at OS-level
        log.debug("SshClientInstaller: Set IDLE_TIMEOUT to MAX, SOCKET-KEEP-ALIVE to true, and HEARTBEAT to {}", heartbeatInterval);

        // Explicitly set IO service factory factory to prevent conflict between MINA and Netty options
        sshClient.setIoServiceFactoryFactory(new MinaServiceFactoryFactory());

        // Start client and connect to SSH server
        try {
            sshClient.start();
            this.session = sshClient.connect(username, host, port)
                    .verify(connectTimeout)
                    .getSession();
            if (StringUtils.isNotBlank(privateKey)) {
                /*PrivateKey privKey = getPrivateKey(privateKey);
                //PublicKey pubKey = getPublicKey(publicKeyStr);
                PublicKey pubKey = getPublicKey(privKey);
                KeyPair keyPair = new KeyPair(pubKey, privKey);
                */
                KeyPair keyPair = getKeyPairFromPEM(privateKey);
                session.addPublicKeyIdentity(keyPair);
            }
            if (StringUtils.isNotBlank(password)) {
                session.addPasswordIdentity(password);
            }
            session.auth()
                    .verify(authenticationTimeout);

            // Initialize standard streams' logger
            initStreamLogger();

            log.info("SshClientInstaller: Connected to remote host: task #{}: host: {}:{}", taskCounter, host, port);
            return true;

        } catch (Exception ex) {
            log.error("SshClientInstaller: Error while connecting to remote host: task #{}: ", taskCounter, ex);
            throw ex;
        }
    }

    private PrivateKey getPrivateKey(String pemStr) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        try (StringReader keyReader = new StringReader(pemStr); PemReader pemReader = new PemReader(keyReader)) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(content);
            PrivateKey privKey = factory.generatePrivate(keySpecPKCS8);
            return privKey;
        }
        //PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.decode(privateKeyContent.replaceAll("\\n", "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")));
        //PrivateKey privKey = kf.generatePrivate(keySpecPKCS8);
    }

    private KeyPair getKeyPairFromPEM(String pemStr) throws IOException {
        // Load the PEM file containing the private key
        log.trace("SshClientInstaller: getKeyPairFromPEM: pemStr: {}", pemStr);
        pemStr = pemStr.replace("\\n", "\n");
        try (StringReader keyReader = new StringReader(pemStr); PEMParser pemParser = new PEMParser(keyReader)) {
            // Parse the PEM encoded private key
            Object pemObject = pemParser.readObject();
            log.trace("SshClientInstaller: getKeyPairFromPEM: pemObject: {}", pemObject);

            // Convert the PEM object to a KeyPair
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (pemObject instanceof PEMKeyPair pemKeyPair) {
                KeyPair keyPair = converter.getKeyPair(pemKeyPair);
                log.trace("SshClientInstaller: getKeyPairFromPEM: keyPair: {}", keyPair);
                return keyPair;
            } else {
                log.warn("SshClientInstaller: getKeyPairFromPEM: Failed to parse PEM private key");
                throw new RuntimeException("Failed to parse PEM private key");
            }
        }
    }

    private PublicKey getPublicKey(PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm());
        PKCS8EncodedKeySpec pubKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
        PublicKey publicKey = factory.generatePublic(pubKeySpec);
        return publicKey;
    }

    /*private PublicKey getPublicKey(String pemStr) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        try (StringReader keyReader = new StringReader(pemStr); PemReader pemReader = new PemReader(keyReader)) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(content);
            PublicKey publicKey = factory.generatePublic(pubKeySpec);
            return publicKey;
        }
        //X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(
        //        Base64.decode(
        //                pemStr.replaceAll("\\n", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
        //                        .getBytes()));
        //RSAPublicKey pubKey = (RSAPublicKey) factory.generatePublic(keySpecX509);
    }

    private PublicKey getPublicKey(RSAPublicKeySpec rsaPrivateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent()));
        return publicKey;
    }

    private PublicKey getPublicKey(BCRSAPrivateCrtKey rsaPrivateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent()));
        return publicKey;
    }*/

    private boolean sshDisconnect() throws Exception {
        SshConfig config = task.getSsh();
        String host = config.getHost();
        int port = config.getPort();
        if (simulateConnection) {
            log.info("SshClientInstaller: Simulate disconnect from remote host: task #{}: host: {}:{}", taskCounter, host, port);
            return true;
        }

        try {
            if (streamLogger!=null)
                streamLogger.close();

            //channel.close(false).await();
            session.close(false);
            //simpleClient.close();
            sshClient.stop();

            log.info("SshClientInstaller: Disconnected from remote host: task #{}: host: {}:{}", taskCounter, host, port);
            return true;
        } catch (Exception ex) {
            log.error("SshClientInstaller: Error while disconnecting from remote host: task #{}: ", taskCounter, ex);
            throw ex;
        } finally {
            session = null;
            //simpleClient = null;
            sshClient = null;
        }
    }

    private void initStreamLogger() throws IOException {
        if (streamLogger!=null) return;

        String address = session.getConnectAddress().toString().replace("/","").replace(":", "-");
        //log.trace("SshClientInstaller: address: {}", address);
        String logFile = StringUtils.isNotBlank(properties.getSessionRecordingDir())
                ? properties.getSessionRecordingDir()+"/"+address+"-"+ simpleDateFormat.format(new Date())+"-"+taskCounter+".txt"
                : null;
        log.info("SshClientInstaller: Task #{}: Session will be recorded in file: {}", taskCounter, logFile);
        this.streamLogger = new StreamLogger(logFile, "  Task #"+taskCounter);
    }

    private void setChannelStreams(ChannelSession channel) throws IOException {
        initStreamLogger();
        channel.setIn( streamLogger.getIn() );
        channel.setOut( streamLogger.getOut() );
        channel.setErr( streamLogger.getErr() );
    }

    /*public boolean sshOpenShell() throws IOException {
        if (simulateConnection) {
            log.info("SshClientInstaller: Simulate open shell channel: task #{}", taskCounter);
            return true;
        }

        shellChannel = session.createShellChannel();
        setChannelStreams(shellChannel);
        shellChannel.open().verify(connectTimeout);
        //shellPipedIn = shellChannel.getInvertedIn();
        log.info("SshClientInstaller: Opened shell channel: task #{}", taskCounter);

        shellChannel.waitFor(
                EnumSet.of(ClientChannelEvent.CLOSED),
                authenticationTimeout);
                //TimeUnit.SECONDS.toMillis(5));
        log.info("SshClientInstaller: Shell channel ready: task #{}", taskCounter);

        return true;
    }

    public boolean sshCloseShell() throws IOException {
        if (simulateConnection) {
            log.info("SshClientInstaller: Simulate close shell channel: task #{}", taskCounter);
            return true;
        }

        shellChannel.close();
        shellChannel = null;
        //shellPipedIn = null;
        streamLogger.close();
        streamLogger = null;
        log.info("SshClientInstaller: Closed shell channel: task #{}", taskCounter);
        return true;
    }

    public boolean sshShellExec(@NotNull String command, long executionTimeout) throws IOException {
        if (simulateConnection || simulateExecution) {
            log.info("SshClientInstaller: Simulate command execution: task #{}: command: {}", taskCounter, command);
            return true;
        }

        // Send command to remote side
        if (!command.endsWith("\n"))
            command += "\n";
        log.info("SshClientInstaller: Sending command: {}", command);
        streamLogger.getInvertedIn().write(command.getBytes());
        streamLogger.getInvertedIn().flush();

        // Search remote side output for expected patterns
        // Not implemented

        shellChannel.waitFor(
                EnumSet.of(ClientChannelEvent.CLOSED),
                executionTimeout>0 ? executionTimeout : commandExecutionTimeout);
                //TimeUnit.SECONDS.toMillis(5));
        return true;
    }*/

    public Integer sshExecCmd(String command) throws IOException {
        return sshExecCmd(command, commandExecutionTimeout);
    }

    public Integer sshExecCmd(String command, long executionTimeout) throws IOException {
        if (simulateConnection || simulateExecution) {
            log.info("SshClientInstaller: Simulate shell command execution: task #{}: command: {}", taskCounter, command);
            return null;
        }

        // Using EXEC channel
        Integer exitStatus = null;
        ChannelExec channel = session.createExecChannel(command);
        setChannelStreams(channel);
        log.debug("SshClientInstaller: task #{}: EXEC: New channel id: {}", taskCounter, channel.getChannelId());
        //streamLogger.getInvertedIn().write(command.getBytes());
        streamLogger.logMessage("EXEC: %s\n".formatted(command));
        try {
            // Sending command to remote side
            log.debug("SshClientInstaller: task #{}: EXEC: Sending command for execution: {}   (connect timeout: {}ms)", taskCounter, command, connectTimeout);
            session.resetIdleTimeout();
            channel.open().verify(connectTimeout);
            log.trace("SshClientInstaller: task #{}: EXEC: Sending command verified: {}", taskCounter, command);
            log.debug("SshClientInstaller: task #{}: EXEC: Opened channel id: {}", taskCounter, channel.getChannelId());

            //XXX: TODO: Search remote side output for expected patterns

            // Wait until channel closes from server side (i.e. command completed) or timeout occurs
            log.trace("SshClientInstaller: task #{}: EXEC: instruction execution-timeout: {}", taskCounter, executionTimeout);
            log.trace("SshClientInstaller: task #{}: EXEC: default command-execution-timeout: {}", taskCounter, commandExecutionTimeout);
            long execTimeout = executionTimeout != 0 ? executionTimeout : commandExecutionTimeout;
            log.debug("SshClientInstaller: task #{}: EXEC: effective instruction execution-timeout: {}", taskCounter, execTimeout);
            Set<ClientChannelEvent> eventSet = channel.waitFor(
                    EnumSet.of(ClientChannelEvent.CLOSED),
                    execTimeout);
                    //TimeUnit.SECONDS.toMillis(50));
            log.debug("SshClientInstaller: task #{}: EXEC: Exit event set: {}", taskCounter, eventSet);
            exitStatus = channel.getExitStatus();
            log.debug("SshClientInstaller: task #{}: EXEC: Exit status: {}", taskCounter, exitStatus);
        } finally {
            channel.close();
        }

        return exitStatus;
    }

    public boolean sshFileDownload(String remoteFilePath, String localFilePath) throws IOException {
        if (simulateConnection || simulateExecution) {
            log.info("SshClientInstaller: Simulate file download: task #{}: remote: {} -> local: {}", taskCounter, remoteFilePath, localFilePath);
            return true;
        }

        streamLogger.logMessage("DOWNLOAD: SCP: %s -> %s\n".formatted(remoteFilePath, localFilePath));
        try {
            log.info("SshClientInstaller: Downloading file: task #{}: remote: {} -> local: {}", taskCounter, remoteFilePath, localFilePath);
            ScpClientCreator creator = new DefaultScpClientCreator();
            ScpClient scpClient = creator.createScpClient(session);
            scpClient.download(remoteFilePath, localFilePath, ScpClient.Option.PreserveAttributes);
            log.info("SshClientInstaller: File download completed: task #{}: remote: {} -> local: {}", taskCounter, remoteFilePath, localFilePath);
        } catch (Exception ex) {
            log.error("SshClientInstaller: File download failed: task #{}: remote: {} -> local: {} Exception: ", taskCounter, remoteFilePath, localFilePath, ex);
            throw ex;
        }

        return true;
    }

    public boolean sshFileUpload(String localFilePath, String remoteFilePath) throws IOException {
        if (simulateConnection || simulateExecution) {
            log.info("SshClientInstaller: Simulate file upload: task #{}: local: {} -> remote: {}", taskCounter, localFilePath, remoteFilePath);
            return true;
        }

        streamLogger.logMessage("UPLOAD: SCP: %s -> %s\n".formatted(localFilePath, remoteFilePath));
        try {
            long startTm = System.currentTimeMillis();
            log.info("SshClientInstaller: Uploading file: task #{}: local: {} -> remote: {}", taskCounter, localFilePath, remoteFilePath);
            ScpClientCreator creator = new DefaultScpClientCreator();
            ScpClient scpClient = creator.createScpClient(session);
            scpClient.upload(localFilePath, remoteFilePath, ScpClient.Option.PreserveAttributes);
            long endTm = System.currentTimeMillis();
            log.info("SshClientInstaller: File upload completed in {}ms: task #{}: local: {} -> remote: {}", endTm-startTm, taskCounter, localFilePath, remoteFilePath);
        } catch (Exception ex) {
            log.error("SshClientInstaller: File upload failed: task #{}: local: {} -> remote: {} Exception: ", taskCounter, localFilePath, remoteFilePath, ex);
            throw ex;
        }

        return true;
    }

    public boolean sshFileWrite(String content, String remoteFilePath, boolean isExecutable) throws IOException {
        if (simulateConnection || simulateExecution) {
            log.info("SshClientInstaller: Simulate file upload: task #{}: remote: {}, content-length={}", taskCounter, remoteFilePath, content.length());
            return true;
        }

        streamLogger.logMessage("WRITE FILE: SCP: %s, content-length=%d \n".formatted(remoteFilePath, content.length()));
        try {
            long timestamp = System.currentTimeMillis();
            /*Collection<PosixFilePermission> permissions = isExecutable
                    ? Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            log.info("SshClientInstaller: Uploading file: task #{}: remote: {}, perm={}, content-length={}", taskCounter, remoteFilePath, permissions, content.length());
            log.trace("SshClientInstaller: Uploading file: task #{}: remote: {}, perm={}, content:\n{}", taskCounter, remoteFilePath, permissions, content);
            ScpClient scpClient = session.createScpClient();
            scpClient.upload(content.getBytes(), remoteFilePath, permissions, new ScpTimestamp(timestamp, timestamp));
            */

             /*
             The alternative approach next is much faster than the original approach above (commented out)
             Old approach: write bytes directly to remote file
             New approach: write contents to a local temp. file and then upload it to remote side
             */

            // Write contents to a temporary local file
            File tmpDir = Paths.get(properties.getServerTmpDir()).toFile();
            tmpDir.mkdirs();
            File tmp = File.createTempFile("bci_upload_", ".tmp", tmpDir);
            log.debug("SshClientInstaller: Write to temp. file: task #{}: temp-file: {}, remote: {}, content-length: {}", taskCounter, tmp, remoteFilePath, content.length());
            log.trace("SshClientInstaller: Write to temp. file: task #{}: temp-file: {}, remote: {}, content:\n{}", taskCounter, tmp, remoteFilePath, content);
            try (FileWriter fw = new FileWriter(tmp.getAbsoluteFile())) { fw.write(content); }

            // Upload temporary local file to remote side
            log.trace("SshClientInstaller: Call 'sshFileUpload': task #{}: temp-file={}, remote={}", taskCounter, tmp, remoteFilePath);
            sshFileUpload(tmp.getAbsolutePath(), remoteFilePath);

            // Delete temporary file
            if (!properties.isKeepTempFiles()) {
                log.trace("SshClientInstaller: Remove temp. file: task #{}: temp-file={}", taskCounter, tmp);
                tmp.delete();
            }

            long endTm = System.currentTimeMillis();
            log.info("SshClientInstaller: File upload completed in {}ms: task #{}: remote: {}, content-length={}", endTm-timestamp, taskCounter, remoteFilePath, content.length());
            log.trace("SshClientInstaller: File upload completed in {}ms: task #{}: remote: {}, content:\n{}", endTm-timestamp, taskCounter, remoteFilePath, content);
        } catch (Exception ex) {
            log.error("SshClientInstaller: File upload failed: task #{}: remote: {}, Exception: ", taskCounter, remoteFilePath, ex);
            throw ex;
        }

        return true;
    }

    private INSTRUCTION_RESULT executeInstructionSets() throws IOException {
        List<InstructionsSet> instructionsSetList = task.getInstructionSets();
        INSTRUCTION_RESULT exitResult = INSTRUCTION_RESULT.SUCCESS;
        int cntSuccess = 0;
        int cntFail = 0;
        for (InstructionsSet instructionsSet : instructionsSetList) {
            log.info("\n  ----------------------------------------------------------------------\n  Task #{} :  Instruction Set: {}", taskCounter, instructionsSet.getDescription());

            // Check installation instructions condition
            try {
                if (! InstructionsService.getInstance().checkCondition(instructionsSet, task.getNodeRegistryEntry().getPreregistration())) {
                    log.info("SshClientInstaller: Task #{}: Installation Instructions set is skipped due to failed condition: {}", taskCounter, instructionsSet.getDescription());
                    if (instructionsSet.isStopOnConditionFail()) {
                        log.info("SshClientInstaller: Task #{}: No further installation instructions sets will be executed due to stopOnConditionFail: {}", taskCounter, instructionsSet.getDescription());
                        exitResult = INSTRUCTION_RESULT.FAIL;
                        break;
                    }
                    continue;
                }
                log.debug("SshClientInstaller: Task #{}: Condition evaluation for Installation Instructions Set succeeded: {}", taskCounter, instructionsSet.getDescription());
            } catch (Exception e) {
                log.error("sshClientInstaller: Task #{}: Installation Instructions Set Condition evaluation error. Will not process remaining installation instructions sets: {}\n", taskCounter, instructionsSet.getDescription(), e);
                exitResult = INSTRUCTION_RESULT.FAIL;
                break;
            }

            // Execute installation instructions
            log.info("SshClientInstaller: Task #{}: Executing installation instructions set: {}", taskCounter, instructionsSet.getDescription());
            streamLogger.logMessage(
                    String.format("\n  ----------------------------------------------------------------------\n  Task #%d :  Executing instruction set: %s\n",
                    taskCounter, instructionsSet.getDescription()));
            INSTRUCTION_RESULT result = executeInstructions(instructionsSet);
            if (result==INSTRUCTION_RESULT.FAIL) {
                log.error("SshClientInstaller: Task #{}: Installation Instructions set failed: {}", taskCounter, instructionsSet.getDescription());
                cntFail++;
                if (!continueOnFail) {
                    exitResult = INSTRUCTION_RESULT.FAIL;
                    break;
                }
            } else
            if (result==INSTRUCTION_RESULT.EXIT) {
                log.info("SshClientInstaller: Task #{}: Instruction set processing exits", taskCounter);
                cntSuccess++;
                exitResult = INSTRUCTION_RESULT.EXIT;
                break;
            } else {
                log.info("SshClientInstaller: Task #{}: Installation Instructions set succeeded: {}", taskCounter, instructionsSet.getDescription());
                cntSuccess++;
            }
        }
        log.info("\n  -------------------------------------------------------------------------\n  Task #{} :  Instruction sets processed: successful={}, failed={}, exit-result={}", taskCounter, cntSuccess, cntFail, exitResult);
        return exitResult;
    }

    private INSTRUCTION_RESULT executeInstructions(InstructionsSet instructionsSet) throws IOException {
        Map<String, String> valueMap = task.getNodeRegistryEntry().getPreregistration();
        int numOfInstructions = instructionsSet.getInstructions().size();
        int cnt = 0;
        int insCount = instructionsSet.getInstructions().size();
        for (Instruction ins : instructionsSet.getInstructions()) {
            if (ins==null) continue;
            cnt++;

            // Check instruction condition
            try {
                if (! InstructionsService.getInstance().checkCondition(ins, valueMap)) {
                    log.info("SshClientInstaller: Task #{}: Instruction is skipped due to failed condition {}/{}: {}", taskCounter, cnt, numOfInstructions, ins.description());
                    if (ins.isStopOnConditionFail()) {
                        log.info("SshClientInstaller: Task #{}: No further instructions will be executed due to stopOnConditionFail: {}/{}: {}", taskCounter, cnt, numOfInstructions, ins.description());
                        return INSTRUCTION_RESULT.FAIL;
                    }
                    continue;
                }
                log.debug("SshClientInstaller: Task #{}: Condition evaluation for instruction succeeded: {}/{}: {}", taskCounter, cnt, numOfInstructions, ins.description());
            } catch (Exception e) {
                log.error("sshClientInstaller: Task #{}: Instruction Condition evaluation error. Will not process remaining instructions: {}/{}: {}\n", taskCounter, cnt, numOfInstructions, ins.description(), e);
                return INSTRUCTION_RESULT.FAIL;
            }

            // Execute instruction
            ins = InstructionsService
                    .getInstance()
                    .resolvePlaceholders(ins, valueMap);
            log.trace("SshClientInstaller: Task #{}: Executing instruction {}/{}: {}", taskCounter, cnt, numOfInstructions, ins);
            log.info("SshClientInstaller: Task #{}: Executing instruction {}/{}: {}", taskCounter, cnt, numOfInstructions, ins.description());
            Integer exitStatus;
            boolean result = true;
            switch (ins.taskType()) {
                case LOG:
                    log.info("SshClientInstaller: Task #{}: LOG: {}", taskCounter, ins.message());
                    break;
                case CMD:
                    log.info("SshClientInstaller: Task #{}: EXEC: {}", taskCounter, ins.command());
                    int retries = 0;
                    int maxRetries = ins.retries();
                    while (true) {
                        try {
                            exitStatus = sshExecCmd(ins.command(), ins.executionTimeout());
                            result = (exitStatus!=null);
                            //result = (exitStatus==0);
                            log.info("SshClientInstaller: Task #{}: EXEC: exit-status={}", taskCounter, exitStatus);
                            if (result) break;
                        } catch (Exception ex) {
                            if (retries+1>=maxRetries)
                                throw ex;
                            else
                                log.error("SshClientInstaller: Task #{}: EXEC: Last command raised exception: ", taskCounter, ex);
                        }

                        retries++;
                        if (retries<=maxRetries) {
                            log.info("SshClientInstaller: Task #{}: Retry {}/{} for instruction {}/{}: {}",
                                    taskCounter, retries, maxRetries, cnt, numOfInstructions, ins.description());
                        } else {
                            if (maxRetries>0)
                                log.error("sshClientInstaller: Task #{}: Last instruction failed {} times. Giving up", taskCounter, maxRetries);
                            result = false;
                            break;
                        }
                    }
                    break;
                /*case SHELL:
                    log.info("SshClientInstaller: Task #{}: SHELL: {}", taskCounter, ins.getCommand());
                    retries = 0;
                    maxRetries = ins.getRetries();
                    while (true) {
                        try {
                            result = sshShellExec(ins.getCommand(), ins.getExecutionTimeout());
                            log.info("SshClientInstaller: Task #{}: SHELL: exit-status={}", taskCounter, result);
                            if (result) break;
                        } catch (Exception ex) {
                            if (retries+1>=maxRetries)
                                throw ex;
                            else
                                log.error("SshClientInstaller: Task #{}: SHELL: Last command raised exception: ", taskCounter, ex);
                        }

                        retries++;
                        if (retries<=maxRetries) {
                            log.info("SshClientInstaller: Task #{}: Retry {}/{} for instruction {}/{}: {}",
                                    taskCounter, retries, maxRetries, cnt, numOfInstructions, ins.getDescription());
                        } else {
                            if (maxRetries>0)
                                log.error("sshClientInstaller: Task #{}: Last instruction failed {} times. Giving up", taskCounter, maxRetries);
                            result = false;
                            break;
                        }
                    }
                    break;*/
                case FILE:
                    //log.info("SshClientInstaller: Task #{}: FILE: {}, content-length={}", taskCounter, ins.getFileName(), ins.getContents().length());
                    if (Paths.get(ins.localFileName()).toFile().isDirectory()) {
                        log.info("SshClientInstaller: Task #{}: FILE: COPY-PROCESS DIR: {} -> {}", taskCounter, ins.localFileName(), ins.fileName());
                        result = copyDir(ins.localFileName(), ins.fileName(), valueMap);
                    } else
                    if (Paths.get(ins.localFileName()).toFile().isFile()) {
                        log.info("SshClientInstaller: Task #{}: FILE: COPY-PROCESS FILE: {} -> {}", taskCounter, ins.localFileName(), ins.fileName());
                        Path sourceFile = Paths.get(ins.localFileName());
                        Path sourceBaseDir = Paths.get(ins.localFileName()).getParent();
                        result = copyFile(sourceFile, sourceBaseDir, ins.fileName(), valueMap, ins.executable());
                    } else {
                        log.error("SshClientInstaller: Task #{}: FILE: ERROR: Local file is not directory or normal file: {}", taskCounter, ins.localFileName());
                        result = false;
                    }
                    break;
                case COPY:
                case UPLOAD:
                    log.info("SshClientInstaller: Task #{}: UPLOAD: {} -> {}", taskCounter, ins.localFileName(), ins.fileName());
                    result = sshFileUpload(ins.localFileName(), ins.fileName());
                    break;
                case DOWNLOAD:
                    log.info("SshClientInstaller: Task #{}: DOWNLOAD: {} -> {}", taskCounter, ins.fileName(), ins.localFileName());
                    result = sshFileDownload(ins.fileName(), ins.localFileName());
                    if (result)
                        result = processPatterns(ins, valueMap);
                    break;
                case CHECK:
                    log.info("SshClientInstaller: Task #{}: CHECK: {}", taskCounter, ins.command());
                    exitStatus = sshExecCmd(ins.command());
                    log.info("SshClientInstaller: Task #{}: CHECK: exit-status={}", taskCounter, exitStatus);
                    log.debug("SshClientInstaller: Task #{}: CHECK: Result: match={}, match-status={}, exec-status={}",
                            taskCounter, ins.match(), ins.exitCode(), exitStatus);
                    if (ins.match() && exitStatus==ins.exitCode()
                        || !ins.match() && exitStatus!=ins.exitCode())
                    {
                        log.info("SshClientInstaller: Task #{}: CHECK: MATCH: {}", taskCounter, ins.message());
                        log.info("SshClientInstaller: Task #{}: CHECK: MATCH: Will not process more instructions", taskCounter);
                        return INSTRUCTION_RESULT.SUCCESS;
                    }
                    break;

                case SET_VARS:
                    log.info("SshClientInstaller: Task #{}: SET_VARS:", taskCounter);
                    if (ins.variables()!=null && ins.variables().size()>0) {
                        ins.variables().forEach((varName, varExpression) -> {
                            try {
                                String varValue = InstructionsService.getInstance().processPlaceholders(varExpression, valueMap);
                                log.info("SshClientInstaller: Task #{}:   Setting VAR: {} = {}", taskCounter, varName, varValue);
                                valueMap.put(varName, varValue);
                            } catch (Exception e) {
                                log.error("SshClientInstaller: Task #{}:   ERROR while Setting VAR: {}: {}\n", taskCounter, varName, varExpression, e);
                            }
                        });
                    } else
                        log.warn("SshClientInstaller: Task #{}: SET_VARS:  No variables specified", taskCounter);
                    break;
                case UNSET_VARS:
                    log.info("SshClientInstaller: Task #{}: UNSET_VARS:", taskCounter);
                    if (ins.variables()!=null && ins.variables().size()>0) {
                        Set<String> vars = ins.variables().keySet();
                        log.info("SshClientInstaller: Task #{}:   Unsetting VAR: {}", taskCounter, vars);
                        valueMap.keySet().removeAll(vars);
                    } else
                        log.warn("SshClientInstaller: Task #{}: UNSET_VARS:  No variables specified", taskCounter);
                    break;
                case PRINT_VARS:
                    //log.info("SshClientInstaller: Task #{}: PRINT_VARS:", taskCounter);
                    String output = valueMap.entrySet().stream()
                            .map(e -> "    VAR: "+e.getKey()+" = "+e.getValue())
                            .collect(Collectors.joining("\n"));
                    log.info("SshClientInstaller: Task #{}: PRINT_VARS:\n{}", taskCounter, output);
                    break;
                case EXIT_SET:
                    log.info("SshClientInstaller: Task #{}: EXIT_SET: Stop this instruction set processing", taskCounter);
                    try {
                        if (StringUtils.isNotBlank(ins.command())) {
                            String exitResult = ins.command().trim().toUpperCase();
                            log.info("SshClientInstaller: Task #{}: EXIT_SET: Result={}", taskCounter, exitResult);
                            return INSTRUCTION_RESULT.valueOf(exitResult);
                        }
                    } catch (Exception e) {
                        log.error("SshClientInstaller: Task #{}: EXIT_SET: Invalid EXIT_SET result: {}. Will return FAIL", taskCounter, ins.command());
                        return INSTRUCTION_RESULT.FAIL;
                    }
                    log.info("SshClientInstaller: Task #{}: EXIT_SET: Result={}", taskCounter, INSTRUCTION_RESULT.SUCCESS);
                    return INSTRUCTION_RESULT.SUCCESS;
                case EXIT:
                    log.info("SshClientInstaller: Task #{}: EXIT: Stop any further instruction processing", taskCounter);
                    return INSTRUCTION_RESULT.EXIT;
                default:
                    log.error("sshClientInstaller: Unknown instruction type. Ignoring it: {}", ins);
            }
            if (!result) {
                log.error("sshClientInstaller: Last instruction failed. Will not process remaining instructions");
                return INSTRUCTION_RESULT.FAIL;
            }

            if (cnt<insCount)
                log.trace("sshClientInstaller: Continuing with next command...");
            else
                log.trace("sshClientInstaller: No more instructions");
        }
        return INSTRUCTION_RESULT.SUCCESS;
    }

    public boolean copyDir(String sourceDir, String targetDir, Map<String,String> valueMap) throws IOException {
        // Copy files from EMS server to Baguette Client
        if (StringUtils.isNotEmpty(sourceDir) && StringUtils.isNotEmpty(targetDir)) {
            Path baseDir = Paths.get(sourceDir).toAbsolutePath();
            try (Stream<Path> stream = Files.walk(baseDir, Integer.MAX_VALUE)) {
                List<Path> paths = stream
                        .filter(Files::isRegularFile)
                        .map(Path::toAbsolutePath)
                        .sorted()
                        .collect(Collectors.toList());
                for (Path p : paths) {
                    if (!copyFile(p, baseDir, targetDir, valueMap, false))
                        return false;
                }
            }
        }
        return true;
    }

    public boolean copyFile(Path sourcePath, Path sourceBaseDir, String targetDir, Map<String,String> valueMap, boolean isExecutable) throws IOException {
        String targetFile = StringUtils.substringAfter(sourcePath.toUri().toString(), sourceBaseDir.toUri().toString());
        if (!targetFile.startsWith("/")) targetFile = "/"+targetFile;
        targetFile = targetDir + targetFile;

        String contents = new String(Files.readAllBytes(sourcePath));
        log.info("SshClientInstaller: Task #{}: FILE: {}, content-length={}", taskCounter, targetFile, contents.length());
        contents = StringSubstitutor.replace(contents, valueMap);
        log.trace("SshClientInstaller: Task #{}: FILE: {}, final-content:\n{}", taskCounter, targetFile, contents);

        String description = "Copy file from server to temp to client: %s -> %s".formatted(sourcePath.toString(), targetFile);

        return sshFileWrite(contents, targetFile, isExecutable);
    }

    private boolean processPatterns(Instruction ins, Map<String,String> valueMap) {
        Map<String, Pattern> patterns = ins.patterns();
        if (patterns==null || patterns.size()==0) {
            log.info("SshClientInstaller: processPatterns: No patterns to process");
            return true;
        }

        // Read local file
        String[] linesArr;
        try (Stream<String> lines = Files.lines(Paths.get(ins.localFileName()))) {
            linesArr = lines.toArray(String[]::new);
        } catch (IOException e) {
            log.error("SshClientInstaller: processPatterns: Error while reading local file: {} -- Exception: ", ins.localFileName(), e);
            return false;
        }

        // Process file lines against instruction patterns
        patterns.forEach((varName,pattern) -> {
            log.trace("SshClientInstaller: processPatterns: For-Each: var-name={}, pattern={}, pattern-flags={}", varName, pattern, pattern.flags());
            Matcher matcher = null;
            for (String line : linesArr) {
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    matcher = m;
                    //break;    // Uncomment to return the first match. Comment to return the last match.
                }
            }
            if (matcher!=null && matcher.matches()) {
                String varValue = matcher.group( matcher.groupCount()>0 ? 1 : 0 );
                log.info("SshClientInstaller: processPatterns: Setting variable '{}' to: {}", varName, varValue);
                valueMap.put(varName, varValue);
            } else {
                log.info("SshClientInstaller: processPatterns: No match for variable '{}' with pattern: {}", varName, pattern);
            }
        });

        return true;
    }

    @Override
    public void preProcessTask() {
        // Throw exception to prevent task exception, if task data have problem
    }

    @Override
    public boolean postProcessTask() {
        log.trace("SshClientInstaller: postProcessTask: BEGIN:\n{}", task.getNodeRegistryEntry().getPreregistration());

        // Check if Baguette client has been installed (or failed to install)
        log.trace("SshClientInstaller: postProcessTask: CLIENT INSTALLATION....");
        boolean result = postProcessVariable(
                properties.getClientInstallVarName(),
                properties.getClientInstallSuccessPattern(),
                value -> { task.getNodeRegistryEntry().nodeInstallationComplete(value); return true; },
                null, null);
        log.trace("SshClientInstaller: postProcessTask: CLIENT INSTALLATION.... result: {}", result);
        if (result) return true;

        // Check if Baguette client installation has failed
        log.trace("SshClientInstaller: postProcessTask: CLIENT INSTALLATION FAILED....");
        result = postProcessVariable(
                properties.getClientInstallVarName(),
                properties.getClientInstallErrorPattern(),
                value -> { task.getNodeRegistryEntry().nodeInstallationError(value); return true; },
                null, null);
        log.trace("SshClientInstaller: postProcessTask: CLIENT INSTALLATION.... result: {}", result);
        if (result) return true;

        // Check if Baguette client installation has been skipped (not attempted at all)
        log.trace("SshClientInstaller: postProcessTask: CLIENT INSTALLATION SKIP....");
        result = postProcessVariable(
                properties.getSkipInstallVarName(),
                properties.getSkipInstallPattern(),
                value -> { task.getNodeRegistryEntry().nodeNotInstalled(value); return true; },
                null, null);
        log.trace("SshClientInstaller: postProcessTask: CLIENT INSTALLATION SKIP.... result: {}", result);
        if (result) return true;

        // Check if the Node must be ignored by EMS
        log.trace("SshClientInstaller: postProcessTask: NODE IGNORE....");
        result = postProcessVariable(
                properties.getIgnoreNodeVarName(),
                properties.getIgnoreNodePattern(),
                value -> { task.getNodeRegistryEntry().nodeIgnore(value); return true; },
                null, null);
        log.trace("SshClientInstaller: postProcessTask: NODE IGNORE.... result: {}", result);
        if (result) return true;

        // Process defaults, if variables are missing or inconclusive
        log.trace("SshClientInstaller: postProcessTask: DEFAULTS....");
        if (properties.isIgnoreNodeIfVarIsMissing()) {
            log.trace("SshClientInstaller: postProcessTask: DEFAULTS.... NODE IGNORED");
            task.getNodeRegistryEntry().nodeIgnore(null);
        } else
        if (properties.isSkipInstallIfVarIsMissing()) {
            log.trace("SshClientInstaller: postProcessTask: DEFAULTS.... CLIENT INSTALLATION SKIPPED");
            task.getNodeRegistryEntry().nodeNotInstalled(null);
        } else
        if (properties.isClientInstallSuccessIfVarIsMissing()) {
            log.trace("SshClientInstaller: postProcessTask: DEFAULTS.... CLIENT INSTALLED");
            task.getNodeRegistryEntry().nodeInstallationComplete(null);
        } else
        if (properties.isClientInstallErrorIfVarIsMissing()) {
            log.trace("SshClientInstaller: postProcessTask: DEFAULTS.... CLIENT INSTALLATION ERROR");
            task.getNodeRegistryEntry().nodeInstallationError(null);
        } else
            log.trace("SshClientInstaller: postProcessTask: DEFAULTS.... NO DEFAULT");
        log.trace("SshClientInstaller: postProcessTask: END");
        return true;
    }

    private boolean postProcessVariable(String varName, Pattern pattern, @NonNull Function<String,Boolean> match, Function<String,Boolean> notMatch, Supplier<Boolean> missing) {
        log.trace("SshClientInstaller: postProcessVariable: var={}, pattern={}", varName, pattern);
        if (StringUtils.isNotBlank(varName) && pattern!=null) {
            String value = task.getNodeRegistryEntry().getPreregistration().get(varName);
            log.trace("SshClientInstaller: postProcessVariable: var={}, value={}", varName, value);
            if (value!=null) {
                if (pattern.matcher(value).matches()) {
                    log.trace("SshClientInstaller: postProcessVariable: MATCH-END: var={}, value={}, pattern={}", varName, value, pattern);
                    return match.apply(value);
                } else {
                    log.trace("SshClientInstaller: postProcessVariable: NO MATCH: var={}, value={}, pattern={}", varName, value, pattern);
                    if (notMatch!=null) {
                        log.trace("SshClientInstaller: postProcessVariable: NO MATCH-END: var={}, value={}, pattern={}", varName, value, pattern);
                        return notMatch.apply(value);
                    }
                }
            }
        }
        if (missing!=null) {
            log.trace("SshClientInstaller: postProcessVariable: DEFAULT-END: var={}", varName);
            return missing.get();
        }
        log.trace("SshClientInstaller: postProcessVariable: False-END: var={}", varName);
        return false;
    }
}
