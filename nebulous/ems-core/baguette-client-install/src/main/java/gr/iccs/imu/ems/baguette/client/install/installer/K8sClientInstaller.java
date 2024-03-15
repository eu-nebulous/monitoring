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
import gr.iccs.imu.ems.util.ConfigWriteService;
import gr.iccs.imu.ems.util.EmsConstant;
import gr.iccs.imu.ems.util.EmsRelease;
import gr.iccs.imu.ems.util.PasswordUtil;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EMS client installer on a Kubernetes cluster
 */
@Slf4j
@Getter
public class K8sClientInstaller implements ClientInstallerPlugin {
    private static final String K8S_SERVICE_ACCOUNT_SECRETS_PATH_DEFAULT = "/var/run/secrets/kubernetes.io/serviceaccount";
    private static final String APP_CONFIG_MAP_NAME_DEFAULT = "monitoring-configmap";
    private static final String EMS_CLIENT_CONFIG_MAP_NAME_DEFAULT = "ems-client-configmap";

    private static final String EMS_CLIENT_DAEMONSET_SPECIFICATION_FILE_DEFAULT = "/ems-client-daemonset.yaml";
    private static final String EMS_CLIENT_DAEMONSET_NAME_DEFAULT = "ems-client-daemonset";
    private static final String EMS_CLIENT_DAEMONSET_IMAGE_REPOSITORY_DEFAULT = "registry.gitlab.com/nebulous-project/ems-main/ems-client";
    private static final String EMS_CLIENT_DAEMONSET_IMAGE_TAG_DEFAULT = EmsRelease.EMS_VERSION;
    private static final String EMS_CLIENT_DAEMONSET_IMAGE_PULL_POLICY_DEFAULT = "Always";

    private final ClientInstallationTask task;
    private final long taskCounter;
    private final ClientInstallationProperties properties;
    private final ConfigWriteService configWriteService;
    private final PasswordUtil passwordUtil;

    private String additionalCredentials;   // Those specified in EMS_CLIENT_ADDITIONAL_BROKER_CREDENTIALS, plus one generated
    private String brokerUsername;
    private String brokerPassword;

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
    }

    private void initializeAdditionalCredentials() {
        brokerUsername = getConfig("EMS_CLIENT_BROKER_USERNAME", "user-" + RandomStringUtils.randomAlphanumeric(32));
        brokerPassword = getConfig("EMS_CLIENT_BROKER_PASSWORD", RandomStringUtils.randomAlphanumeric(32));

        StringBuilder sb = new StringBuilder(getConfig("EMS_CLIENT_ADDITIONAL_BROKER_CREDENTIALS", ""));
        if (StringUtils.isNotBlank(sb))
            sb.append(", ");
        sb.append(brokerUsername).append("/").append(brokerPassword);
        additionalCredentials = sb.toString();
    }

    private String getConfig(@NonNull String key, String defaultValue) {
        String value = System.getenv(key);
        return value==null ? defaultValue : value;
    }

    @Override
    public boolean executeTask() {
        boolean success = true;
        try {
            deployOnCluster();
        } catch (Exception ex) {
            log.error("K8sClientInstaller: Failed executing installation instructions for task #{}, Exception: ", taskCounter, ex);
            success = false;
        }

        if (success) log.info("K8sClientInstaller: Task completed successfully #{}", taskCounter);
        else log.info("K8sClientInstaller: Error occurred while executing task #{}", taskCounter);
        return true;
    }

    private void deployOnCluster() throws IOException {
        boolean dryRun = Boolean.parseBoolean( getConfig("EMS_CLIENT_DEPLOYMENT_DRY_RUN", "false") );
        if (dryRun)
            log.warn("K8sClientInstaller: NOTE: Dry Run set!!  Will not make any changes to cluster");
        deployOnCluster(dryRun);
    }

    private void deployOnCluster(boolean dryRun) throws IOException {
        String serviceAccountPath = getConfig("K8S_SERVICE_ACCOUNT_SECRETS_PATH", K8S_SERVICE_ACCOUNT_SECRETS_PATH_DEFAULT);
        String masterUrl = getConfig("KUBERNETES_SERVICE_HOST", null);
        String caCert = Files.readString(Paths.get(serviceAccountPath, "ca.crt"));
        String token = Files.readString(Paths.get(serviceAccountPath, "token"));
        String namespace = Files.readString(Paths.get(serviceAccountPath, "namespace"));
        log.debug("""
            K8sClientInstaller:
              Master URL: {}
                CA cert.:
            {}
                   Token: {}
               Namespace: {}""",
                masterUrl, caCert.trim(), passwordUtil.encodePassword(token), namespace);

        // Configure and start Kubernetes API client
        Config config = new ConfigBuilder()
                .withMasterUrl(masterUrl)
                .withCaCertData(caCert)
                .withOauthToken(token)
                .build();
        try (KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build()) {
            // Prepare ems client config map
            createEmsClientConfigMap(dryRun, client, namespace);

            // Prepare application config map
            createAppConfigMap(dryRun, client, namespace);

            // Deploy ems client daemonset
            createEmsClientDaemonSet(dryRun, client, namespace);

            task.getNodeRegistryEntry().nodeInstallationComplete(null);
        }
    }

    private void createEmsClientConfigMap(boolean dryRun, KubernetesClient client, String namespace) {
        log.debug("K8sClientInstaller.createEmsClientConfigMap: BEGIN: dry-run={}, namespace={}", dryRun, namespace);

        // Get ems client configmap name
        String configMapName = getConfig("EMS_CLIENT_CONFIG_MAP_NAME", EMS_CLIENT_CONFIG_MAP_NAME_DEFAULT);
        log.debug("K8sClientInstaller:         configMap: name: {}", configMapName);

        // Get ems client configuration
        Map<String, String> configMapMap = configWriteService
                .getConfigFile(EmsConstant.EMS_CLIENT_K8S_CONFIG_MAP_FILE).getContentMap();
        log.debug("K8sClientInstaller:         configMap: data:\n{}", configMapMap);

        // Create ems client configmap
        Resource<ConfigMap> configMapResource = client.configMaps()
                .inNamespace(namespace)
                .resource(new ConfigMapBuilder()
                        .withNewMetadata().withName(configMapName).endMetadata()
                        .addToData("creationTimestamp", Long.toString(Instant.now().getEpochSecond()))
                        .addToData(configMapMap)
                        .build());
        log.trace("K8sClientInstaller:  ConfigMap to create: {}", configMapResource);
        if (!dryRun) {
            ConfigMap configMap = configMapResource.serverSideApply();
            log.debug("K8sClientInstaller:    ConfigMap created: {}", configMap);
        } else {
            log.warn("K8sClientInstaller: DRY-RUN: Didn't create ems client configmap");
        }
    }

    private void createAppConfigMap(boolean dryRun, KubernetesClient client, String namespace) {
        log.debug("K8sClientInstaller.createAppConfigMap: BEGIN: dry-run={}, namespace={}", dryRun, namespace);

        // Get ems client configmap name
        String configMapName = getConfig("APP_CONFIG_MAP_NAME", APP_CONFIG_MAP_NAME_DEFAULT);
        log.debug("K8sClientInstaller:     App configMap: name: {}", configMapName);

        // Get App ems-client-related configuration
        Map<String, String> configMapMap = new LinkedHashMap<>();
        configMapMap.put("BROKER_USERNAME", brokerUsername);
        configMapMap.put("BROKER_PASSWORD", brokerPassword);
        log.debug("K8sClientInstaller:     App configMap: data:\n{}", configMapMap);

        // Create ems client configmap
        Resource<ConfigMap> configMapResource = client.configMaps()
                .inNamespace(namespace)
                .resource(new ConfigMapBuilder()
                        .withNewMetadata().withName(configMapName).endMetadata()
                        .addToData("creationTimestamp", Long.toString(Instant.now().getEpochSecond()))
                        .addToData(configMapMap)
                        .build());
        log.trace("K8sClientInstaller:  App ConfigMap to create: {}", configMapResource);
        if (!dryRun) {
            ConfigMap configMap = configMapResource.serverSideApply();
            log.debug("K8sClientInstaller:    App ConfigMap created: {}", configMap);
        } else {
            log.warn("K8sClientInstaller: DRY-RUN: Didn't create App ems client configmap");
        }
    }

    private void createEmsClientDaemonSet(boolean dryRun, KubernetesClient client, String namespace) throws IOException {
        log.debug("K8sClientInstaller.createEmsClientDaemonSet: BEGIN: dry-run={}, namespace={}", dryRun, namespace);

        String resourceName = getConfig("EMS_CLIENT_DAEMONSET_SPECIFICATION_FILE", EMS_CLIENT_DAEMONSET_SPECIFICATION_FILE_DEFAULT);
        Map<String,String> values = Map.of(
                "EMS_CLIENT_DAEMONSET_NAME", getConfig("EMS_CLIENT_DAEMONSET_NAME", EMS_CLIENT_DAEMONSET_NAME_DEFAULT),
                "EMS_CLIENT_DAEMONSET_IMAGE_REPOSITORY", getConfig("EMS_CLIENT_DAEMONSET_IMAGE_REPOSITORY", EMS_CLIENT_DAEMONSET_IMAGE_REPOSITORY_DEFAULT),
                "EMS_CLIENT_DAEMONSET_IMAGE_TAG", getConfig("EMS_CLIENT_DAEMONSET_IMAGE_TAG", EMS_CLIENT_DAEMONSET_IMAGE_TAG_DEFAULT),
                "EMS_CLIENT_DAEMONSET_IMAGE_PULL_POLICY", getConfig("EMS_CLIENT_DAEMONSET_IMAGE_PULL_POLICY", EMS_CLIENT_DAEMONSET_IMAGE_PULL_POLICY_DEFAULT),
                "EMS_CLIENT_CONFIG_MAP_NAME", getConfig("EMS_CLIENT_CONFIG_MAP_NAME", EMS_CLIENT_CONFIG_MAP_NAME_DEFAULT),
                "EMS_CLIENT_ADDITIONAL_BROKER_CREDENTIALS", additionalCredentials,
                "EMS_CLIENT_KEYSTORE_SECRET", getConfig("EMS_CLIENT_KEYSTORE_SECRET", ""),
                "EMS_CLIENT_TRUSTSTORE_SECRET", getConfig("EMS_CLIENT_TRUSTSTORE_SECRET", "")
        );
        log.debug("K8sClientInstaller: resourceName: {}", namespace);
        log.debug("K8sClientInstaller:       values: {}", values);

        String spec;
        try (InputStream inputStream = K8sClientInstaller.class.getResourceAsStream(resourceName)) {
            spec = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
            log.trace("K8sClientInstaller: Ems client daemonset spec BEFORE:\n{}", spec);
            spec = StringSubstitutor.replace(spec, values);
            log.trace("K8sClientInstaller: Ems client daemonset spec AFTER :\n{}", spec);
        }

        if (StringUtils.isNotBlank(spec)) {
            try (InputStream stream = new ByteArrayInputStream(spec.getBytes(StandardCharsets.UTF_8))) {
                // Load a DaemonSet object
                DaemonSet daemonSet = client.apps().daemonSets().load(stream).item();
                log.debug("K8sClientInstaller: DaemonSet to create: {} :: {}", daemonSet.hashCode(), daemonSet.getMetadata().getName());

                // Deploy the DaemonSet
                if (!dryRun) {
                    DaemonSet ds = client.apps().daemonSets().inNamespace(namespace).resource(daemonSet).create();
                    log.debug("K8sClientInstaller:   DaemonSet created: {} :: {}", ds.hashCode(), ds.getMetadata().getName());
                } else {
                    log.warn("K8sClientInstaller: DRY-RUN: Didn't create ems client daemonset");
                }
            }
        } else {
            log.warn("K8sClientInstaller: ERROR: Ems client daemonset spec is empty");
        }
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
