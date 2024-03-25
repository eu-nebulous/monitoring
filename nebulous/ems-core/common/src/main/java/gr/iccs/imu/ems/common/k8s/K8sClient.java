/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.common.k8s;

import gr.iccs.imu.ems.util.PasswordUtil;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * EMS Kubernetes (K8S) client
 */
@Slf4j
@Getter
@Setter
@Builder
@Accessors(fluent = true)
@AllArgsConstructor
public class K8sClient implements Closeable {
    private static final String K8S_SERVICE_ACCOUNT_SECRETS_PATH_DEFAULT = "/var/run/secrets/kubernetes.io/serviceaccount";

    private KubernetesClient client;
    private String namespace;
    private boolean dryRun;

    public static K8sClient create() throws IOException {
        // Get K8S client configuration
        String serviceAccountPath = getConfig("K8S_SERVICE_ACCOUNT_SECRETS_PATH", K8S_SERVICE_ACCOUNT_SECRETS_PATH_DEFAULT);
        String masterUrl = getConfig("KUBERNETES_SERVICE_HOST", null);
        String caCert = Files.readString(Paths.get(serviceAccountPath, "ca.crt"));
        String token = Files.readString(Paths.get(serviceAccountPath, "token"));
        String namespace = Files.readString(Paths.get(serviceAccountPath, "namespace"));
        boolean dryRun = Boolean.parseBoolean( getConfig("EMS_CLIENT_DEPLOYMENT_DRY_RUN", "false") );
        log.debug("""
            K8sClient configuration:
              Master URL: {}
                CA cert.:
            {}
                   Token: {}
               Namespace: {}
                 Dry-run: {}""",
                masterUrl, caCert.trim(), PasswordUtil.getInstance().encodePassword(token), namespace, dryRun);
        if (dryRun)
            log.warn("K8sClient: NOTE: Dry Run set!!  Will not make any changes to cluster");

        // Configure and start Kubernetes API client
        Config config = new ConfigBuilder()
                .withMasterUrl(masterUrl)
                .withCaCertData(caCert)
                .withOauthToken(token)
                .build();
        return new K8sClient(new KubernetesClientBuilder().withConfig(config).build(), namespace, dryRun);
    }

    public static String getConfig(@NonNull String key, String defaultValue) {
        String value = System.getenv(key);
        return value==null ? defaultValue : value;
    }

    public K8sClient createConfigMap(String configMapName, Map<String,String> configMapData) {
        log.debug("K8sClient.createConfigMap: BEGIN: configMap-name: {}, configMap-data{}, namespace={}, dry-run={}",
                configMapName, configMapData, namespace, dryRun);

        // Create configmap
        Resource<ConfigMap> configMapResource = client.configMaps()
                .inNamespace(namespace)
                .resource(new ConfigMapBuilder()
                        .withNewMetadata().withName(configMapName).endMetadata()
                        .addToData("creationTimestamp", Long.toString(Instant.now().getEpochSecond()))
                        .addToData(configMapData)
                        .build());
        log.trace("K8sClient.createConfigMap:  ConfigMap to create: {}", configMapResource);
        if (!dryRun) {
            ConfigMap configMap = configMapResource.serverSideApply();
            log.debug("K8sClient.createConfigMap:    ConfigMap created: {}", configMap);
        } else {
            log.warn("K8sClient.createConfigMap: DRY-RUN: Didn't create configmap: {}", configMapName);
        }
        return this;
    }

    public K8sClient createDaemonSet(String daemonSetName, String resourceName, Map<String,String> replacementValuesMap) throws IOException {
        log.debug("K8sClient.createDaemonSet: BEGIN: namespace={}, dry-run={}", namespace, dryRun);

        String spec;
        try (InputStream inputStream = K8sClient.class.getResourceAsStream(resourceName)) {
            spec = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
            log.trace("K8sClient.createDaemonSet: DaemonSet spec BEFORE:\n{}", spec);
            spec = StringSubstitutor.replace(spec, replacementValuesMap);
            log.trace("K8sClient.createDaemonSet: DaemonSet spec AFTER :\n{}", spec);
        }

        return createDaemonSet(daemonSetName, spec);
    }

    public K8sClient createDaemonSet(String daemonSetName, String spec) throws IOException {
        log.debug("K8sClient.createDaemonSet: BEGIN: daemonSet-name: {}, namespace={}, dry-run={}, spec={}",
                daemonSetName, namespace, dryRun, spec);

        if (StringUtils.isNotBlank(spec)) {
            try (InputStream stream = new ByteArrayInputStream(spec.getBytes(StandardCharsets.UTF_8))) {
                // Load a DaemonSet object
                DaemonSet daemonSet = client.apps().daemonSets().load(stream).item();
                log.debug("K8sClient.createDaemonSet: DaemonSet to create: {} :: {}", daemonSet.hashCode(), daemonSet.getMetadata().getName());

                // Deploy the DaemonSet
                if (!dryRun) {
                    DaemonSet ds = client.apps().daemonSets().inNamespace(namespace).resource(daemonSet).create();
                    log.debug("K8sClient.createDaemonSet:   DaemonSet created: {} :: {}", ds.hashCode(), ds.getMetadata().getName());
                } else {
                    log.warn("K8sClient.createDaemonSet: DRY-RUN: Didn't create DaemonSet: {}", daemonSetName);
                }
            }
        } else {
            log.warn("K8sClient.createDaemonSet: ERROR: DaemonSet spec is empty");
        }
        return this;
    }

    public Map<String, Object> getServiceConnectionInfo(@NonNull String serviceName, String namespace) {
        // Get service details
        namespace = StringUtils.isBlank(namespace) ? this.namespace() : namespace;
        Service service = client.services().inNamespace(namespace).withName(serviceName).get();

        // Find the NodePort port number
        List<String> externalAddresses = null;
        List<String> clusterAddresses = null;
        List<Integer> ports = null;
        Integer nodePort = -1;
        if (service != null && service.getSpec() != null && service.getSpec().getPorts() != null) {
            externalAddresses = service.getSpec().getExternalIPs();
            clusterAddresses = service.getSpec().getClusterIPs();
            ports = service.getSpec().getPorts().stream().map(ServicePort::getPort).toList();
            for (ServicePort port : service.getSpec().getPorts()) {
                if (port.getNodePort() != null) {
                    nodePort = port.getNodePort();
                    break;
                }
            }
        }
        return Map.of(
                "external-addresses", emptyIfNull(externalAddresses),
                "clusterAddresses", emptyIfNull(clusterAddresses),
                "ports", emptyIfNull(ports),
                "node-port", nodePort
        );
    }

    private <T>List<T> emptyIfNull(List<T> list) {
        return list!=null ? list : List.of();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
