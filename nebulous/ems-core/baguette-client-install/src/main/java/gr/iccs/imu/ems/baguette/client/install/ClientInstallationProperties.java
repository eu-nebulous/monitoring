/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gr.iccs.imu.ems.util.EmsConstant;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = EmsConstant.EMS_PROPERTIES_PREFIX + "baguette.client.install")
public class ClientInstallationProperties implements InitializingBean {

    private final static ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public void afterPropertiesSet() throws Exception {
        normalizeParameterKeys();
        loadYamlFields();
        log.debug("ClientInstallationProperties: {}", this);
    }

    enum INSTALLER_TYPE { DEFAULT_INSTALLER, JS_INSTALLER }

    private final Map<String, List<String>> osFamilies = new LinkedHashMap<>();

    private int workers = 1;
    private INSTALLER_TYPE installerType = INSTALLER_TYPE.DEFAULT_INSTALLER;

    private String clientInstallationRequestsTopic = "ems.client.installation.requests";
    private String clientInstallationReportsTopic = "ems.client.installation.reports";
    private List<Pattern> clientInstallationReportNodeInfoPatterns = new ArrayList<>();
    private String clientInfoRequestsTopic = "ems.client.info.requests";
    private String clientInfoReportsTopic = "ems.client.info.reports";

    private String baseDir;                     // EMS client home directory
    private String rootCmd;                     // Root command (e.g. 'sudo', or 'echo ${NODE_SSH_PASSWORD} | sudo -S ')
    private List<String> mkdirs;
    private List<String> touchFiles;
    private String checkInstalledFile;

    private String downloadUrl;                 // Base URL of EMS server downloads
    @ToString.Exclude
    private String apiKey;                      // API Key for accessing EMS server downloads
    private String installScriptUrl;
    private String installScriptFile;

    private String archiveSourceDir;            // the directory in server that will be archived (it must contain client configuration)
    private String archiveDir;                  // the directory in server where client config. archive will be placed into
    private String archiveFile;                 // name of the client configuration archive (in server)
    private String clientConfArchiveFile;       // location in VM, where client config. archive will be stored (in BASE64 encoding)
    //private String clientConfArchiveDest;       // location in VM, where client config. archive will be extracted

    private String serverCertFileAtServer;      // location of EMS server certificate in server (in config-files)
    private String serverCertFileAtClient;      // location in VM, where EMS server certificate will be stored
    private String copyFilesFromServerDir;      // location in EMS server whose contents will be copied to VM
    private String copyFilesToClientDir;        // location in VM where server files will be copied into

    private String clientTmpDir;                // location of temp. directory in VM (typically /tmp)
    private String serverTmpDir;                // location of temp. directory in EMS server
    private boolean keepTempFiles;              // keep temporary files in EMS server (during debug)

    // ----------------------------------------------------

    private boolean simulateConnection;
    private boolean simulateExecution;

    private int maxRetries = 5;
    private long retryDelay = 10000L;
    private double retryBackoffFactor = 1.0;

    private long connectTimeout = 60000;
    private long authenticateTimeout = 60000;
    private long heartbeatInterval = 60000;
    private long heartbeatReplyWait = heartbeatInterval;
    private long commandExecutionTimeout = 60000;

    private final Map<String, List<String>> instructions = new LinkedHashMap<>();
    private final Map<String, String> parameters = new LinkedHashMap<>();
    private String filePostProcessingRulesFile;
    private Map<String,Map<String,String>> filePostProcessingRules;

    private boolean continueOnFail = false;
    private String sessionRecordingDir = "logs";

    @PostConstruct
    public void normalizeParameterKeys() {
        Map<String, String> normalizedParameters = new LinkedHashMap<>();
        parameters.forEach((key, value) -> {
            String normalizedKey = key.toUpperCase();  // Convert to upper case keys
            normalizedKey = normalizedKey.replace(".", "_");  // Replace dots (.) with underscores (_)
            normalizedParameters.put(normalizedKey, value);
        });
        parameters.clear();
        parameters.putAll(normalizedParameters);
    }

    @PostConstruct
    public void loadYamlFields() {
        filePostProcessingRules = loadYamlFromFile(filePostProcessingRulesFile);
    }

    private Map loadYamlFromFile(String jsonFile) {
        if (StringUtils.isNotBlank(jsonFile)) {
            try {
                String jsonValue = Files.readString(Paths.get(jsonFile));
                if (StringUtils.isNotBlank(jsonValue)) {
                    return objectMapper.readValue(jsonValue, Map.class);
                }
            } catch (IOException e) {
                throw new RuntimeException("Json property parsing failed", e);
            }
        }
        return null;
    }

    // ----------------------------------------------------

    private String clientInstallVarName = "__EMS_CLIENT_INSTALL__";
    private Pattern clientInstallSuccessPattern = Pattern.compile("^INSTALLED($|[\\s:=])", Pattern.CASE_INSENSITIVE);
    private Pattern clientInstallErrorPattern = Pattern.compile("^ERROR($|[\\s:=])", Pattern.CASE_INSENSITIVE);
    private boolean clientInstallSuccessIfVarIsMissing = false;
    private boolean clientInstallErrorIfVarIsMissing = true;

    private String skipInstallVarName = "__EMS_CLIENT_INSTALL__";
    private Pattern skipInstallPattern = Pattern.compile("^SKIPPED($|[\\s:=])", Pattern.CASE_INSENSITIVE);
    private boolean skipInstallIfVarIsMissing = false;

    private String ignoreNodeVarName = "__EMS_IGNORE_NODE__";
    private Pattern ignoreNodePattern = Pattern.compile("^IGNORED($|[\\s:=])", Pattern.CASE_INSENSITIVE);
    private boolean ignoreNodeIfVarIsMissing = false;

    // ----------------------------------------------------

    private List<Class<InstallationContextProcessorPlugin>> installationContextProcessorPlugins = Collections.emptyList();

    // ----------------------------------------------------

    private K8sClientInstallationProperties k8s = new K8sClientInstallationProperties();

    @Data
    public static class K8sClientInstallationProperties {
        private Map<String,String> extraEnvVars = new LinkedHashMap<>();
    }
}
