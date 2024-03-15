/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.helper;

import com.google.gson.Gson;
import gr.iccs.imu.ems.baguette.client.install.ClientInstallationProperties;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsSet;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.util.KeystoreUtil;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
//import org.rauschig.jarchivelib.Archiver;
//import org.rauschig.jarchivelib.ArchiverFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.FileSystemResource;

import java.io.*;
import java.nio.file.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Baguette Client installation helper
 */
@Slf4j
public abstract class AbstractInstallationHelper implements InitializingBean, ApplicationListener<WebServerInitializedEvent>, InstallationHelper {
    protected final static String LINUX_OS_FAMILY = "LINUX";
    protected final static String WINDOWS_OS_FAMILY = "WINDOWS";

    @Autowired
    @Getter @Setter
    protected ClientInstallationProperties properties;
    @Autowired
    protected PasswordUtil passwordUtil;

//XXX: Commented a not used feature with dependency with vulnerability
//    protected String archiveBase64;
    protected boolean isServerSecure;
    protected String serverCert;

    @Override
    public void afterPropertiesSet() {
        log.debug("AbstractInstallationHelper.afterPropertiesSet(): class={}: configuration: {}", getClass().getName(), properties);
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        log.debug("AbstractInstallationHelper.onApplicationEvent(): event={}", event);
        TomcatWebServer tomcat = (TomcatWebServer) event.getSource();

        try {
            initServerCertificateFile(tomcat);
            initBaguetteClientConfigArchive();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initServerCertificateFile(TomcatWebServer tomcat) throws Exception {
        this.isServerSecure = tomcat.getTomcat().getConnector().getSecure();
        log.debug("AbstractInstallationHelper.initServerCertificate(): Embedded Tomcat is secure: {}", isServerSecure);

        if (isServerSecure) {
            // If HTTPS is enabled
            SSLHostConfig[] sslHostConfigArr = tomcat.getTomcat().getConnector().findSslHostConfigs();
            if (log.isDebugEnabled())
                log.debug("AbstractInstallationHelper.initServerCertificate(): Tomcat SSL host config array: {}", Arrays.asList(sslHostConfigArr));
            if (sslHostConfigArr.length!=1)
                throw new RuntimeException("Embedded Tomcat has zero or more than one SSL host configurations: "+sslHostConfigArr.length);

            // Get certificate entries (in key manager/store) for this SSL Hosting configuration
            Set<SSLHostConfigCertificate> sslCertificatesSet = sslHostConfigArr[0].getCertificates();
            log.debug("AbstractInstallationHelper.initServerCertificate(): SSL certificates set: {}", sslCertificatesSet);
            int n = 0;
            String serverCert = null;
            for (SSLHostConfigCertificate sslCertificate : sslCertificatesSet) {
                // Get entry alias
                log.debug("AbstractInstallationHelper.initServerCertificate(): SSL certificate[{}]: {}", n, sslCertificate);
                String keyAlias = sslCertificate.getCertificateKeyAlias();
                log.debug("AbstractInstallationHelper.initServerCertificate(): SSL certificate[{}]: alias={}", n, keyAlias);

                // Get certificate chain for entry with 'alias'
                X509Certificate[] chain = sslCertificate.getSslContext().getCertificateChain(keyAlias);
                StringBuilder sb = new StringBuilder();
                int m = 0;
                for (X509Certificate c : chain) {
                    // Export certificate in PEM format (for each chain item)
                    String certPem = KeystoreUtil.exportCertificateAsPEM(c);
                    log.debug("AbstractInstallationHelper.initServerCertificate(): SSL certificate[{}]: {}: \n{}", n, m, certPem);
                    // Append PEM certificate to 'sb'
                    sb.append(certPem).append(System.lineSeparator());
                    m++;
                }
                // The first entry is used as the server certificate
                if (serverCert==null)
                    serverCert = sb.toString();

                n++;
            }
            this.serverCert = serverCert;
            log.debug("AbstractInstallationHelper.initServerCertificate(): Server certificate:\n{}", serverCert);

            // Write server certificate to PEM file (server.pem)
            String certFileName = properties.getServerCertFileAtServer();
            if (this.serverCert!=null && StringUtils.isNotEmpty(certFileName)) {
                File certFile = new File(certFileName);
                Files.writeString(certFile.toPath(), this.serverCert, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                if (! certFile.exists())
                    throw new RuntimeException("Server PEM certificate file not found: "+certFile);
                log.debug("AbstractInstallationHelper.initServerCertificate(): Server PEM certificate stored in file: {}", certFile);
                log.info("Server PEM certificate stored in file: {}", certFile);
            }

        } else {
            // If HTTPS is disabled
            if (StringUtils.isNotEmpty(properties.getServerCertFileAtServer())) {
                File certFile = new File(properties.getServerCertFileAtServer());
                if (certFile.exists()) {
                    log.debug("AbstractInstallationHelper.initServerCertificate(): Removing previous server certificate file");
                    if (!certFile.delete())
                        throw new RuntimeException("Could not remove previous server certificate file: " + certFile);
                }
                this.serverCert = null;
            }
        }
    }

    private void initBaguetteClientConfigArchive() throws IOException {
        if (StringUtils.isEmpty(properties.getArchiveSourceDir()) || StringUtils.isEmpty(properties.getArchiveFile())) {
            log.debug("AbstractInstallationHelper: No baguette client configuration archiving has been configured");
            return;
        }
        log.info("AbstractInstallationHelper: Building baguette client configuration archive...");

        // Get archiving settings
        String configDirName = properties.getArchiveSourceDir();
        File configDir = new File(configDirName);
        log.debug("AbstractInstallationHelper: Baguette client configuration directory: {}", configDir);
        if (!configDir.exists())
            throw new FileNotFoundException("Baguette client configuration directory not found: " + configDirName);

        String archiveName = properties.getArchiveFile();
        String archiveDirName = properties.getArchiveDir();
        File archiveDir = new File(archiveDirName);
        log.debug("AbstractInstallationHelper: Baguette client configuration archive: {}/{}", archiveDirName, archiveName);
        if (!archiveDir.exists())
            throw new FileNotFoundException("Baguette client configuration archive directory not found: " + archiveDirName);

        // Remove previous baguette client configuration archive
        File archiveFile = new File(archiveDirName, archiveName);
        if (archiveFile.exists()) {
            log.debug("AbstractInstallationHelper: Removing previous archive...");
            if (!archiveFile.delete())
                throw new RuntimeException("AbstractInstallationHelper: Failed removing previous archive: " + archiveName);
        }

        // Create baguette client configuration archive
        /*XXX: Commented a not used feature with dependency with vulnerability
        Archiver archiver = ArchiverFactory.createArchiver(archiveFile);
        String tempFileName = "archive_" + System.currentTimeMillis();
        log.debug("AbstractInstallationHelper: Temp. archive name: {}", tempFileName);
        archiveFile = archiver.create(tempFileName, archiveDir, configDir);
        log.debug("AbstractInstallationHelper: Archive generated: {}", archiveFile);
        if (!archiveFile.getName().equals(archiveName)) {
            log.debug("AbstractInstallationHelper: Renaming archive to: {}", archiveName);
            if (!archiveFile.renameTo(archiveFile = new File(archiveDir, archiveName)))
                throw new RuntimeException("AbstractInstallationHelper: Failed renaming generated archive to: " + archiveName);
        }
        log.info("AbstractInstallationHelper: Baguette client configuration archive: {}", archiveFile);

        // Base64 encode archive and cache in memory
        byte[] archiveBytes = Files.readAllBytes(archiveFile.toPath());
        this.archiveBase64 = Base64.getEncoder().encodeToString(archiveBytes);
        log.debug("AbstractInstallationHelper: Archive Base64 encoded: {}", archiveBase64);
        */
    }

    private String getResourceAsString(String resourcePath) throws IOException {
        InputStream resource = new FileSystemResource(resourcePath).getInputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public Optional<List<String>> getInstallationInstructionsForOs(NodeRegistryEntry entry) throws IOException {
        if (! entry.getBaguetteServer().isServerRunning()) throw new RuntimeException("Baguette Server is not running");

        List<InstructionsSet> instructionsSets = prepareInstallationInstructionsForOs(entry);
        if (instructionsSets==null) {
            String nodeOs = entry.getPreregistration().get("operatingSystem");
            log.warn("AbstractInstallationHelper.getInstallationInstructionsForOs(): ERROR: Unknown node OS: {}: node-map={}", nodeOs, entry.getPreregistration());
            return Optional.empty();
        }

        List<String> jsonSets = null;
        if (!instructionsSets.isEmpty()) {
            // Convert 'instructionsSet' into json string
            Gson gson = new Gson();
            jsonSets = instructionsSets.stream().map(instructionsSet -> gson.toJson(instructionsSet, InstructionsSet.class)).collect(Collectors.toList());
        }
        log.trace("AbstractInstallationHelper.getInstallationInstructionsForOs(): JSON instruction sets for node: node-map={}\n{}", entry.getPreregistration(), jsonSets);
        return Optional.ofNullable(jsonSets);
    }

    public List<InstructionsSet> prepareInstallationInstructionsForOs(NodeRegistryEntry entry) throws IOException {
        if (! entry.getBaguetteServer().isServerRunning()) throw new RuntimeException("Baguette Server is not running");
        log.trace("AbstractInstallationHelper.prepareInstallationInstructionsForOs(): node-map={}", entry.getPreregistration());

        String osFamily = entry.getPreregistration().get("operatingSystem");
        List<InstructionsSet> instructionsSetList = null;
        if (matchesOsFamily(osFamily, LINUX_OS_FAMILY))
            instructionsSetList = prepareInstallationInstructionsForLinux(entry);
        else if (matchesOsFamily(osFamily, WINDOWS_OS_FAMILY))
            instructionsSetList = prepareInstallationInstructionsForWin(entry);
        else
            log.warn("AbstractInstallationHelper.prepareInstallationInstructionsForOs(): Unsupported OS family: {}", osFamily);
        return instructionsSetList;
    }

    public List<InstructionsSet> prepareUninstallInstructionsForOs(NodeRegistryEntry entry) throws IOException {
        if (! entry.getBaguetteServer().isServerRunning()) throw new RuntimeException("Baguette Server is not running");
        log.trace("AbstractInstallationHelper.prepareUninstallInstructionsForOs(): node-map={}", entry.getPreregistration());

        String osFamily = entry.getPreregistration().get("operatingSystem");
        List<InstructionsSet> instructionsSetList = null;
        if (matchesOsFamily(osFamily, LINUX_OS_FAMILY))
            instructionsSetList = prepareUninstallInstructionsForLinux(entry);
        else if (matchesOsFamily(osFamily, WINDOWS_OS_FAMILY))
            instructionsSetList = prepareUninstallInstructionsForWin(entry);
        else
            log.warn("AbstractInstallationHelper.prepareUninstallInstructionsForOs(): Unsupported OS family: {}", osFamily);
        return instructionsSetList;
    }

    public boolean matchesOsFamily(@NonNull String lookup, @NonNull String osFamily) {
        lookup = lookup.trim().toUpperCase();
        List<String> familyList = properties.getOsFamilies().get(osFamily);
        if (familyList!=null && !familyList.isEmpty() && osFamily.equals(lookup))
            return true;
        return familyList != null && familyList.contains(lookup);
    }

    protected InstructionsSet _appendCopyInstructions(
            InstructionsSet instructionsSet,
            Path p,
            Path startDir,
            String copyToClientDir,
            String clientTmpDir,
            Map<String,String> valueMap
    ) throws IOException
    {
        String targetFile = StringUtils.substringAfter(p.toUri().toString(), startDir.toUri().toString());
        if (!targetFile.startsWith("/")) targetFile = "/"+targetFile;
        targetFile = copyToClientDir + targetFile;
        String contents = new String(Files.readAllBytes(p));
        contents = StringSubstitutor.replace(contents, valueMap);
        String tmpFile = clientTmpDir+"/installEMS_"+System.currentTimeMillis();
        instructionsSet
                .appendLog("Copy file from server to temp to client: %s -> %s -> %s"
                        .formatted(p.toString(), tmpFile, targetFile));
        return _appendCopyInstructions(instructionsSet, targetFile, tmpFile, contents, clientTmpDir);
    }

    protected InstructionsSet _appendCopyInstructions(
            InstructionsSet instructionsSet,
            String targetFile,
            String tmpFile,
            String contents,
            String clientTmpDir
    ) throws IOException
    {
        if (StringUtils.isEmpty(tmpFile))
            tmpFile = clientTmpDir+"/installEMS_"+System.currentTimeMillis();
        instructionsSet
                .appendWriteFile(tmpFile, contents, false)
                .appendExec("sudo mv " + tmpFile + " " + targetFile)
                .appendExec("sudo chmod u+rw,og-rwx " + targetFile);
        return instructionsSet;
    }

    protected String _prepareUrl(String urlTemplate, String baseUrl) {
        return urlTemplate
                .replace("%{BASE_URL}%", Optional.ofNullable(baseUrl).orElse(""));
    }
}
