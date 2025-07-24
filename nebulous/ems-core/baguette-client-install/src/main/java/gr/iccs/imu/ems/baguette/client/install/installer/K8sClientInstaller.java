/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.installer;

import gr.iccs.imu.ems.baguette.client.install.ClientInstallationProperties;
import gr.iccs.imu.ems.baguette.client.install.ClientInstallationTask;
import gr.iccs.imu.ems.baguette.client.install.ClientInstallerPlugin;
import gr.iccs.imu.ems.common.k8s.K8sClient;
import gr.iccs.imu.ems.util.ConfigWriteService;
import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.EmsRelease;
import gr.iccs.imu.ems.util.PasswordUtil;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static gr.iccs.imu.ems.common.k8s.K8sClient.getConfig;

/**
 * EMS client installer on a Kubernetes cluster
 */
@Slf4j
@Getter
public class K8sClientInstaller implements ClientInstallerPlugin {
    private static final String APP_CONFIG_MAP_NAME_DEFAULT = "monitoring-configmap";
    private static final String EMS_CLIENT_CONFIG_MAP_NAME_DEFAULT = "ems-client-configmap";

    private static final String EMS_CLIENT_DAEMONSET_SPECIFICATION_FILE_DEFAULT = "/ems-client-daemonset.yaml";
    private static final String EMS_CLIENT_DAEMONSET_NAME_DEFAULT = "ems-client-daemonset";
    private static final String EMS_CLIENT_DAEMONSET_IMAGE_REPOSITORY_DEFAULT = "registry.gitlab.com/nebulous-project/ems-main/ems-client";
    private static final String EMS_CLIENT_DAEMONSET_IMAGE_TAG_DEFAULT = EmsRelease.EMS_VERSION;
    private static final String EMS_CLIENT_DAEMONSET_IMAGE_PULL_POLICY_DEFAULT = "Always";
    private static final String EMS_CLIENT_DEPLOY_AT_CONTROL_PLANE_DEFAULT = "true";

    private final ClientInstallationTask task;
    private final long taskCounter;
    private final ClientInstallationProperties properties;
    private final ConfigWriteService configWriteService;
    private final PasswordUtil passwordUtil;

    private String additionalCredentials;   // Those specified in EMS_CLIENT_ADDITIONAL_BROKER_CREDENTIALS, plus one generated
    private String brokerUsername;
    private String brokerPassword;
    private String extraEnvVars;
    private String tolerations;

    @Builder
    public K8sClientInstaller(ClientInstallationTask task, long taskCounter, ClientInstallationProperties properties,
                              ConfigWriteService configWriteService, PasswordUtil passwordUtil)
    {
        this.task = task;
        this.taskCounter = taskCounter;
        this.properties = properties;
        this.configWriteService = configWriteService;
        this.passwordUtil = passwordUtil;

        initializeAdditionalCredentials();
        initializeExtraEnvVars();
        initializeLoggingEnvVars();
        initializeTolerations();
    }

    private void initializeAdditionalCredentials() {
        brokerUsername = getConfig("EMS_CLIENT_BROKER_USERNAME", "user-" + RandomStringUtils.randomAlphanumeric(32));
        brokerPassword = getConfig("EMS_CLIENT_BROKER_PASSWORD", RandomStringUtils.randomAlphanumeric(32));

        StringBuilder sb = new StringBuilder(getConfig("EMS_CLIENT_ADDITIONAL_BROKER_CREDENTIALS", ""));
        if (StringUtils.isNotBlank(brokerUsername)) {
            if (StringUtils.isNotBlank(sb))
                sb.append(", ");
            sb.append(brokerUsername).append("/").append(brokerPassword);
        }
        additionalCredentials = sb.toString();
    }

    private void initializeExtraEnvVars() {
        extraEnvVars = "";
        if (properties.getK8s()==null) return;
        if (properties.getK8s().getExtraEnvVars()==null) return;
        String str = properties.getK8s().getExtraEnvVars().entrySet()
                .stream().filter(e -> StringUtils.isNotBlank(e.getKey()))
                .map(e -> String.format("""
                                                    - name: "%s"
                                                      value: "%s"
                                        """, e.getKey().trim(), e.getValue()))
                .collect(Collectors.joining("\n"));
        log.info("K8sClientInstaller: Extra Env.Vars:\n{}", str);
        extraEnvVars = str;
    }

    private void initializeLoggingEnvVars() {
        final StringBuilder sb = new StringBuilder(extraEnvVars);
        final AtomicBoolean comma = new AtomicBoolean(! extraEnvVars.isBlank());
        System.getenv()
                .forEach((name, value) -> {
                    if (StringUtils.startsWith(name.trim(), "EMS_CLIENT_LOGGING_LEVEL_")) {
                        sb.append( String.format("""
                                                            - name: "%s"
                                                              value: "%s"
                                                """,
                                StringUtils.removeStart(name.trim(), "EMS_CLIENT_"),
                                value)
                        );
                    }
                });
        if (sb.length()>extraEnvVars.length())
            extraEnvVars = sb.toString();
        log.info("K8sClientInstaller: Extra Env.Vars with Log settings:\n{}", extraEnvVars);
    }

    private void initializeTolerations() {
        StringBuilder stringBuilder = new StringBuilder("\n");
        if ("true".equalsIgnoreCase(getConfig("EMS_CLIENT_DEPLOY_AT_CONTROL_PLANE", EMS_CLIENT_DEPLOY_AT_CONTROL_PLANE_DEFAULT))) {
            stringBuilder
                    .append("        - key: \"node-role.kubernetes.io/control-plane\"\n")
                    .append("          operator: \"Exists\"\n")
                    .append("          effect: \"NoSchedule\"\n");
        }
        tolerations = stringBuilder.isEmpty() ? "[]" : stringBuilder.toString();
    }

    @Override
    public boolean executeTask() {
        boolean success = false;
        int retries = 0;
        int maxRetries = properties.getMaxRetries();
        while (!success && retries<=maxRetries) {
            if (retries > 0)
                log.warn("K8sClientInstaller: Retry {}/{}: Executing task #{}", retries, maxRetries, taskCounter);

            try {
                // Clear any previous deployment
                switch (getTask().getTaskType()) {
                    case INSTALL, REINSTALL, UNINSTALL -> undeployFromCluster();
                }

                // New deployment
                switch (getTask().getTaskType()) {
                    case INSTALL, REINSTALL -> deployOnCluster();
                }

                success = true;
            } catch (Exception ex) {
                log.error("K8sClientInstaller: Failed executing installation instructions for task #{}, Exception: ", taskCounter, ex);
                retries++;
                if (retries<=maxRetries)
                    waitToRetry(retries);
            }
        }

        if (success) log.info("K8sClientInstaller: Task completed successfully #{}", taskCounter);
        else log.warn("K8sClientInstaller: Task #{} failed after {} retries", taskCounter, retries);
        return true;
    }

    private void waitToRetry(int retries) {
        long delay = Math.max(1, (long)(properties.getRetryDelay() * Math.pow(properties.getRetryBackoffFactor(), retries-1)));
        try {
            log.debug("K8sClientInstaller: waitToRetry: Waiting for {}ms to retry", delay);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            log.warn("K8sClientInstaller: waitToRetry: Waiting to retry interrupted: ", e);
        }
    }

    private void deployOnCluster() throws IOException {
        boolean dryRun = Boolean.parseBoolean( getConfig("EMS_CLIENT_DEPLOYMENT_DRY_RUN", "false") );
        if (dryRun)
            log.warn("K8sClientInstaller: NOTE: Dry Run set!!  Will not make any changes to cluster");
        deployOnCluster(dryRun);
    }

    private void deployOnCluster(boolean dryRun) throws IOException {
        try (K8sClient client = K8sClient.create().dryRun(dryRun)) {
            // Prepare ems client config map
            createEmsClientConfigMap(client);

            // Prepare application config map
            createAppConfigMap(client);

            // Deploy ems client DaemonSet
            createEmsClientDaemonSet(client);

            task.getNodeRegistryEntry().nodeInstallationComplete(null);
        }
    }

    private void undeployFromCluster() throws IOException {
        boolean dryRun = Boolean.parseBoolean( getConfig("EMS_CLIENT_DEPLOYMENT_DRY_RUN", "false") );
        if (dryRun)
            log.warn("K8sClientInstaller: NOTE: Dry Run set!!  Will not make any changes to cluster");
        undeployFromCluster(dryRun);
    }

    private void undeployFromCluster(boolean dryRun) throws IOException {
        try (K8sClient client = K8sClient.create().dryRun(dryRun)) {
            task.getNodeRegistryEntry().nodeRemoving(null);

            // Undeploy ems client DaemonSet
            deleteEmsClientDaemonSet(client);

            // Delete ems client and app config maps
            deleteConfigMaps(client);

            task.getNodeRegistryEntry().nodeRemoved(null);
        }
    }

    private void createEmsClientConfigMap(K8sClient client) {
        // Get ems client configmap name
        String configMapName = getConfig("EMS_CLIENT_CONFIG_MAP_NAME", EMS_CLIENT_CONFIG_MAP_NAME_DEFAULT);
        log.debug("K8sClientInstaller.createEmsClientConfigMap: name: {}", configMapName);

        // Get ems client configuration
        Map<String, String> configMapData = configWriteService
                .getConfigFile(EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FILE).getContentMap();
        log.debug("K8sClientInstaller.createEmsClientConfigMap: data:\n{}", configMapData);

        // Create ems client configmap
        client.createConfigMap(configMapName, configMapData);
    }

    private void createAppConfigMap(K8sClient client) {
        // Get ems client configmap name
        String configMapName = getConfig("APP_CONFIG_MAP_NAME", APP_CONFIG_MAP_NAME_DEFAULT);
        log.debug("K8sClientInstaller.createAppConfigMap: name: {}", configMapName);

        // Get App ems-client-related configuration
        Map<String, String> configMapData = new LinkedHashMap<>();
        configMapData.put("BROKER_USERNAME", brokerUsername);
        configMapData.put("BROKER_PASSWORD", brokerPassword);
        log.debug("K8sClientInstaller:     App configMap: data:\n{}", configMapData);

        // Create ems client configmap
        client.createConfigMap(configMapName, configMapData);
    }

    private void createEmsClientDaemonSet(K8sClient client) throws IOException {
        log.debug("K8sClientInstaller.createEmsClientDaemonSet: BEGIN");

        // Get ems client daemonset replacement values
        String daemonSetName = getConfig("EMS_CLIENT_DAEMONSET_NAME", EMS_CLIENT_DAEMONSET_NAME_DEFAULT);
        String resourceName = getConfig("EMS_CLIENT_DAEMONSET_SPECIFICATION_FILE", EMS_CLIENT_DAEMONSET_SPECIFICATION_FILE_DEFAULT);
        Map<String,String> values = new LinkedHashMap<>();
        values.put("EMS_CLIENT_DAEMONSET_NAME", daemonSetName);
        values.put("EMS_CLIENT_DAEMONSET_IMAGE_REPOSITORY", getConfig("EMS_CLIENT_DAEMONSET_IMAGE_REPOSITORY", EMS_CLIENT_DAEMONSET_IMAGE_REPOSITORY_DEFAULT));
        values.put("EMS_CLIENT_DAEMONSET_IMAGE_TAG", getConfig("EMS_CLIENT_DAEMONSET_IMAGE_TAG", EMS_CLIENT_DAEMONSET_IMAGE_TAG_DEFAULT));
        values.put("EMS_CLIENT_DAEMONSET_IMAGE_PULL_POLICY", getConfig("EMS_CLIENT_DAEMONSET_IMAGE_PULL_POLICY", EMS_CLIENT_DAEMONSET_IMAGE_PULL_POLICY_DEFAULT));
        values.put("EMS_CLIENT_CONFIG_MAP_NAME", getConfig("EMS_CLIENT_CONFIG_MAP_NAME", EMS_CLIENT_CONFIG_MAP_NAME_DEFAULT));
        values.put("EMS_CLIENT_ADDITIONAL_BROKER_CREDENTIALS", additionalCredentials);
        values.put("EMS_CLIENT_KEYSTORE_SECRET", getConfig("EMS_CLIENT_KEYSTORE_SECRET", ""));
        values.put("EMS_CLIENT_TRUSTSTORE_SECRET", getConfig("EMS_CLIENT_TRUSTSTORE_SECRET", ""));
        values.put("EMS_CLIENT_TOLERATIONS", tolerations);
        values.put("EMS_CLIENT_LOG_LEVEL", getConfig("EMS_CLIENT_LOG_LEVEL", "INFO"));
        values.put("EMS_CLIENT_EXTRA_ENV_VARS", extraEnvVars);
        log.debug("K8sClientInstaller:       values: {}", values);

        client.createDaemonSet(null, resourceName, values);
    }

    private void deleteConfigMaps(K8sClient client) {
        log.debug("K8sClientInstaller.deleteConfigMaps: BEGIN");
        String emsClientConfigMapName = getConfig("EMS_CLIENT_CONFIG_MAP_NAME", EMS_CLIENT_CONFIG_MAP_NAME_DEFAULT);
        String appConfigMapName = getConfig("APP_CONFIG_MAP_NAME", APP_CONFIG_MAP_NAME_DEFAULT);
        log.debug("K8sClientInstaller.deleteConfigMaps: emsClientConfigMapName: {}", emsClientConfigMapName);
        log.debug("K8sClientInstaller.deleteConfigMaps:       appConfigMapName: {}", appConfigMapName);
        client.deleteConfigMap(emsClientConfigMapName);
        client.deleteConfigMap(appConfigMapName);
    }

    private void deleteEmsClientDaemonSet(K8sClient client) {
        log.debug("K8sClientInstaller.deleteEmsClientDaemonSet: BEGIN");
        String daemonSetName = getConfig("EMS_CLIENT_DAEMONSET_NAME", EMS_CLIENT_DAEMONSET_NAME_DEFAULT);
        log.debug("K8sClientInstaller.deleteEmsClientDaemonSet: name={}", daemonSetName);
        client.deleteDaemonSet(daemonSetName);
    }

    @Override
    public void preProcessTask() {
        // Throw exception to prevent task exception, if task data have problem
    }

    @Override
    public boolean postProcessTask() {
        return true;
    }
}
