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
import com.google.gson.GsonBuilder;
import gr.iccs.imu.ems.baguette.client.install.ClientInstallationProperties;
import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.SshConfig;
import gr.iccs.imu.ems.baguette.client.install.instruction.Instruction;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsService;
import gr.iccs.imu.ems.baguette.client.install.instruction.InstructionsSet;
import gr.iccs.imu.ems.baguette.server.BaguetteServer;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.util.CredentialsMap;
import gr.iccs.imu.ems.util.NetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Baguette Client installation helper
 */
@Slf4j
@Service
public class VmInstallationHelper extends AbstractInstallationHelper {
    private final static SimpleDateFormat tsW3C = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private final static SimpleDateFormat tsUTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private final static SimpleDateFormat tsFile = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS");
    static {
        tsW3C.setTimeZone(TimeZone.getDefault());
        tsUTC.setTimeZone(TimeZone.getTimeZone("UTC"));
        tsFile.setTimeZone(TimeZone.getDefault());
    }

    @Autowired
    private ResourceLoader resourceLoader;
    @Autowired
    private ClientInstallationProperties clientInstallationProperties;

    @Override
    public ClientInstallationTask createClientInstallationTask(NodeRegistryEntry entry, TranslationContext translationContext) throws IOException {
        Map<String, String> nodeMap = entry.getPreregistration();

        String baseUrl = nodeMap.get("BASE_URL");
        String clientId = nodeMap.get("CLIENT_ID");
        String ipSetting = nodeMap.get("IP_SETTING");
        String requestId = nodeMap.get("requestId");

        // Extract node identification and type information
        String nodeId = nodeMap.get("id");
        String nodeOs = nodeMap.get("operatingSystem");
        String nodeAddress = nodeMap.get("address");
        String nodeType = nodeMap.get("type");
        String nodeName = nodeMap.get("name");
        String nodeProvider = nodeMap.get("provider");

        if (StringUtils.isBlank(nodeType)) nodeType = "VM";

        if (StringUtils.isBlank(nodeOs)) throw new IllegalArgumentException("Missing OS information for Node");
        if (StringUtils.isBlank(nodeAddress)) throw new IllegalArgumentException("Missing Address for Node");

        // Extract node SSH information
        int port = (int) Double.parseDouble(Objects.toString(nodeMap.get("ssh.port"), "22"));
        if (port<1) port = 22;
        String username = nodeMap.get("ssh.username");
        String password = nodeMap.get("ssh.password");
        String privateKey = nodeMap.get("ssh.key");
        String fingerprint = nodeMap.get("ssh.fingerprint");

        if (port>65535)
            throw new IllegalArgumentException("Invalid SSH port for Node: " + port);
        if (StringUtils.isBlank(username))
            throw new IllegalArgumentException("Missing SSH username for Node");
        if (StringUtils.isEmpty(password) && StringUtils.isBlank(privateKey))
            throw new IllegalArgumentException("Missing SSH password or private key for Node");

        // Get EMS client installation instructions for VM node
        List<InstructionsSet> instructionsSetList =
                prepareInstallationInstructionsForOs(entry);

        // Create Installation Task for VM node
        ClientInstallationTask installationTask = ClientInstallationTask.builder()
                .id(clientId)
                .nodeId(nodeId)
                .requestId(requestId)
                .name(nodeName)
                .os(nodeOs)
                .address(nodeAddress)
                .ssh(SshConfig.builder()
                        .host(nodeAddress)
                        .port(port)
                        .username(username)
                        .password(password)
                        .privateKey(privateKey)
                        .fingerprint(fingerprint)
                        .build())
                .type(nodeType)
                .provider(nodeProvider)
                .instructionSets(instructionsSetList)
                .nodeRegistryEntry(entry)
                .translationContext(translationContext)
                .build();
        log.debug("VmInstallationHelper.createClientInstallationTask(): Created client installation task: {}", installationTask);

        return installationTask;
    }

    @Override
    public List<InstructionsSet> prepareInstallationInstructionsForWin(NodeRegistryEntry entry) {
        log.warn("VmInstallationHelper.prepareInstallationInstructionsForWin(): NOT YET IMPLEMENTED");
        throw new IllegalArgumentException("VmInstallationHelper.prepareInstallationInstructionsForWin(): NOT YET IMPLEMENTED");
    }

    @Override
    public List<InstructionsSet> prepareInstallationInstructionsForLinux(NodeRegistryEntry entry) throws IOException {
        Map<String, String> nodeMap = entry.getPreregistration();
        BaguetteServer baguette = entry.getBaguetteServer();

        String baseUrl = StringUtils.removeEnd(nodeMap.get("BASE_URL"), "/");
        String clientId = nodeMap.get("CLIENT_ID");
        String ipSetting = nodeMap.get("IP_SETTING");
        log.debug("VmInstallationHelper.prepareInstallationInstructionsForLinux(): Invoked: base-url={}", baseUrl);

        // Get parameters
        log.trace("VmInstallationHelper.prepareInstallationInstructionsForLinux(): properties: {}", properties);
        String rootCmd = properties.getRootCmd();
        String baseDir = properties.getBaseDir();
        String checkInstallationFile = properties.getCheckInstalledFile();

        String baseDownloadUrl = _prepareUrl(properties.getDownloadUrl(), baseUrl);
        String apiKey = properties.getApiKey();
        String installScriptUrl = _prepareUrl(properties.getInstallScriptUrl(), baseUrl);
        String installScriptPath = properties.getInstallScriptFile();

        String serverCertFile = properties.getServerCertFileAtClient();
        String clientConfArchive = properties.getClientConfArchiveFile();

        String copyFromServerDir = properties.getCopyFilesFromServerDir();
        String copyToClientDir = properties.getCopyFilesToClientDir();

        String clientTmpDir = StringUtils.firstNonBlank(properties.getClientTmpDir(), "/tmp");

        // Create additional keys (with NODE_ prefix) for node map values (as aliases to the already existing keys)
        /*
        Map<String,String> additionalKeysMap = nodeMap.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().startsWith("ssh.")
                                ? "NODE_SSH_" + e.getKey().substring(4).toUpperCase()
                                : "NODE_" + e.getKey().toUpperCase(),
                        Map.Entry::getValue,
                        (v1, v2) -> {
                            log.warn("VmInstallationHelper.prepareInstallationInstructionsForLinux(): DUPLICATE KEY FOUND: key={}, old-value={}, new-value={}",
                                    k, v1, v2);
                            return v2;
                        }
                ));*/
        final Map<String,String> additionalKeysMap = new HashMap<>();
        nodeMap.forEach((k, v) -> {
            try {
                k = k.startsWith("ssh.")
                        ? "NODE_SSH_" + k.substring(4).toUpperCase()
                        : "NODE_" + k.toUpperCase();
                if (additionalKeysMap.containsKey(k)) {
                    log.warn("VmInstallationHelper.prepareInstallationInstructionsForLinux(): DUPLICATE KEY FOUND: key={}, old-value={}, new-value={}",
                            k, additionalKeysMap.get(k), v);
                }
                additionalKeysMap.put(k, v);
            } catch (Exception ex) {
                log.error("VmInstallationHelper.prepareInstallationInstructionsForLinux(): EXCEPTION in additional keys copy loop: key={}, value={}, additionalKeysMap={}, Exception:\n",
                        k, v, additionalKeysMap, ex);
            }
        });
        nodeMap.putAll(additionalKeysMap);

        // Load client config. template and prepare configuration
        nodeMap.put("ROOT_CMD", rootCmd!=null ? rootCmd : "");
        nodeMap.put("BAGUETTE_CLIENT_ID", clientId);
        nodeMap.put("BAGUETTE_CLIENT_BASE_DIR", baseDir);
        nodeMap.put("BAGUETTE_SERVER_ADDRESS", baguette.getConfiguration().getServerAddress());
        nodeMap.put("BAGUETTE_SERVER_HOSTNAME", NetUtil.getHostname());
        nodeMap.put("BAGUETTE_SERVER_PORT", ""+baguette.getConfiguration().getServerPort());
        nodeMap.put("BAGUETTE_SERVER_PUBKEY", baguette.getServerPubkey());
        nodeMap.put("BAGUETTE_SERVER_PUBKEY_FINGERPRINT", baguette.getServerPubkeyFingerprint());
        nodeMap.put("BAGUETTE_SERVER_PUBKEY_ALGORITHM", baguette.getServerPubkeyAlgorithm());
        nodeMap.put("BAGUETTE_SERVER_PUBKEY_FORMAT", baguette.getServerPubkeyFormat());
        CredentialsMap.Entry<String,String> pair =
                baguette.getConfiguration().getCredentials().hasPreferredPair()
                        ? baguette.getConfiguration().getCredentials().getPreferredPair()
                        : baguette.getConfiguration().getCredentials().entrySet().iterator().next();
        nodeMap.put("BAGUETTE_SERVER_USERNAME", pair.getKey());
        nodeMap.put("BAGUETTE_SERVER_PASSWORD", pair.getValue());

        if (StringUtils.isEmpty(ipSetting)) throw new IllegalArgumentException("IP_SETTING must have a value");
        nodeMap.put("IP_SETTING", ipSetting);

        // Misc. installation property values
        nodeMap.put("BASE_URL", baseUrl);
        nodeMap.put("DOWNLOAD_URL", baseDownloadUrl);
        nodeMap.put("API_KEY", apiKey);
        nodeMap.put("SERVER_CERT_FILE", serverCertFile);
        nodeMap.put("REMOTE_TMP_DIR", clientTmpDir);

        Date ts = new Date();
        nodeMap.put("TIMESTAMP", Long.toString(ts.getTime()));
        nodeMap.put("TIMESTAMP-W3C", tsW3C.format(ts));
        nodeMap.put("TIMESTAMP-UTC", tsUTC.format(ts));
        nodeMap.put("TIMESTAMP-FILE", tsFile.format(ts));

        nodeMap.putAll(clientInstallationProperties.getParameters());
        nodeMap.put("EMS_PUBLIC_DIR", System.getProperty("PUBLIC_DIR", System.getenv("PUBLIC_DIR")));
        log.trace("VmInstallationHelper.prepareInstallationInstructionsForLinux: value-map: {}", nodeMap);

/*        // Clear EMS server certificate (PEM) file, if not secure
        if (!isServerSecure) {
            serverCertFile = "";
        }

        // Copy files from server to Baguette Client
        if (StringUtils.isNotEmpty(copyFromServerDir) && StringUtils.isNotEmpty(copyToClientDir)) {
            Path startDir = Paths.get(copyFromServerDir).toAbsolutePath();
            try (Stream<Path> stream = Files.walk(startDir, Integer.MAX_VALUE)) {
                List<Path> paths = stream
                        .filter(Files::isRegularFile)
                        .map(Path::toAbsolutePath)
                        .sorted()
                        .collect(Collectors.toList());
                for (Path p : paths) {
                    _appendCopyInstructions(instructionSets, p, startDir, copyToClientDir, clientTmpDir, valueMap);
                }
            }
        }*/

        List<InstructionsSet> instructionsSetList = new ArrayList<>();

        try {
            // Read installation instructions from JSON file
            List<String> instructionSetFileList = null;
            if (nodeMap.containsKey("instruction-files")) {
                instructionSetFileList = Arrays.stream(nodeMap.getOrDefault("instruction-files", "").split(","))
                        .filter(StringUtils::isNotBlank)
                        .map(String::trim)
                        .collect(Collectors.toList());
                if (instructionSetFileList.isEmpty())
                    log.warn("VmInstallationHelper.prepareInstallationInstructionsForLinux: Context map contains 'instruction-files' entry with no contents");
            } else {
                instructionSetFileList = properties.getInstructions().get("LINUX");
            }
            for (String instructionSetFile : instructionSetFileList) {
                // Load instructions set from file
                log.debug("VmInstallationHelper.prepareInstallationInstructionsForLinux: Installation instructions file for LINUX: {}", instructionSetFile);
                InstructionsSet instructionsSet = InstructionsService.getInstance().loadInstructionsFile(instructionSetFile);
                log.debug("VmInstallationHelper.prepareInstallationInstructionsForLinux: Instructions set loaded from file: {}\n{}", instructionSetFile, instructionsSet);

                // Pretty print instructionsSet JSON
                if (log.isTraceEnabled()) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    StringWriter stringWriter = new StringWriter();
                    try (PrintWriter writer = new PrintWriter(stringWriter)) {
                        gson.toJson(instructionsSet, writer);
                    }
                    log.trace("VmInstallationHelper.prepareInstallationInstructionsForLinux: Installation instructions for LINUX: json:\n{}", stringWriter);
                }

                instructionsSetList.add(instructionsSet);
            }

            return instructionsSetList;
        } catch (Exception ex) {
            log.error("VmInstallationHelper.prepareInstallationInstructionsForLinux: Exception while reading Installation instructions for LINUX: ", ex);
            throw ex;
        }
    }

    private InstructionsSet _appendCopyInstructions(
            InstructionsSet instructionsSet,
            Path path,
            Path localBaseDir,
            String remoteTargetDir,
            Map<String,String> valueMap
    ) throws IOException
    {
        String targetFile = StringUtils.substringAfter(path.toUri().toString(), localBaseDir.toUri().toString());
        if (!targetFile.startsWith("/")) targetFile = "/"+targetFile;
        targetFile = remoteTargetDir + targetFile;
        String contents = new String(Files.readAllBytes(path));
        contents = StringSubstitutor.replace(contents, valueMap);
        String description = String.format("Copy file from server to temp to client: %s -> %s", path.toString(), targetFile);
        return _appendCopyInstructions(instructionsSet, targetFile, description, contents);
    }

    private InstructionsSet _appendCopyInstructions(
            InstructionsSet instructionsSet,
            String targetFile,
            String description,
            String contents)
    {
        instructionsSet
                .appendInstruction(Instruction.createWriteFile(targetFile, contents, false).description(description))
                .appendExec("sudo chmod u+rw,og-rwx " + targetFile);
        return instructionsSet;
    }
}
