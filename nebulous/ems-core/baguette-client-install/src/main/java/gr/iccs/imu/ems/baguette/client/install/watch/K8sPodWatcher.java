/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.install.watch;

import gr.iccs.imu.ems.baguette.server.NodeRegistry;
import gr.iccs.imu.ems.baguette.server.NodeRegistryEntry;
import gr.iccs.imu.ems.util.PasswordUtil;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kubernetes cluster pods watcher service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class K8sPodWatcher implements InitializingBean {
    private static final String K8S_SERVICE_ACCOUNT_SECRETS_PATH_DEFAULT = "/var/run/secrets/kubernetes.io/serviceaccount";

    private final TaskScheduler taskScheduler;
    private final PasswordUtil passwordUtil;
    private final NodeRegistry nodeRegistry;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (Boolean.parseBoolean(getConfig("K8S_WATCHER_ENABLED", "true"))) {
            taskScheduler.scheduleWithFixedDelay(this::doWatch, Instant.now().plusSeconds(20), Duration.ofSeconds(10));
        } else {
            log.warn("K8sPodWatcher: Disabled  (set K8S_WATCHER_ENABLED=true to enable)");
        }
    }

    private String getConfig(@NonNull String key, String defaultValue) {
        String value = System.getenv(key);
        return value==null ? defaultValue : value;
    }

    private void doWatch() {
        Map<String, NodeEntry> addressToNodeMap;
        Map<String, Set<PodEntry>> addressToPodMap;
        try {
            log.debug("K8sPodWatcher: BEGIN: doWatch");

            String serviceAccountPath = getConfig("K8S_SERVICE_ACCOUNT_SECRETS_PATH", K8S_SERVICE_ACCOUNT_SECRETS_PATH_DEFAULT);
            String masterUrl = getConfig("KUBERNETES_SERVICE_HOST", null);
            String caCert = Files.readString(Paths.get(serviceAccountPath, "ca.crt"));
            String token = Files.readString(Paths.get(serviceAccountPath, "token"));
            String namespace = Files.readString(Paths.get(serviceAccountPath, "namespace"));
            log.trace("""
                            K8sPodWatcher:
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
                log.debug("K8sPodWatcher: Retrieving active Kubernetes cluster nodes and pods");

                // Get Kubernetes cluster nodes (Hosts)
                addressToNodeMap = new HashMap<>();
                Map<String, NodeEntry> uidToNodeMap = new HashMap<>();
                client.nodes()
                        .resources()
                        .map(Resource::item)
                        .forEach(node -> {
                            NodeEntry entry = uidToNodeMap.computeIfAbsent(
                                    node.getMetadata().getUid(), s -> new NodeEntry(node));
                            node.getStatus().getAddresses().stream()
                                    .filter(address -> ! "Hostname".equalsIgnoreCase(address.getType()))
                                    .forEach(address -> addressToNodeMap.putIfAbsent(address.getAddress(), entry));
                        });
                log.trace("K8sPodWatcher: Address-to-Nodes: {}", addressToNodeMap);

                // Get Kubernetes cluster pods
                addressToPodMap = new HashMap<>();
                Map<String, PodEntry> uidToPodMap = new HashMap<>();
                client.pods()
                        .inAnyNamespace()
//                        .withLabel("nebulous.application")
                        .resources()
                        .map(Resource::item)
                        .filter(pod-> "Running".equalsIgnoreCase(pod.getStatus().getPhase()))
                        .forEach(pod -> {
                            PodEntry entry = uidToPodMap.computeIfAbsent(
                                    pod.getMetadata().getUid(), s -> new PodEntry(pod));
                            pod.getStatus().getPodIPs()
                                    .forEach(address ->
                                        addressToPodMap.computeIfAbsent(address.getIp(), s -> new HashSet<>()).add(entry)
                                    );
                        });
                log.trace("K8sPodWatcher: Address-to-Pods: {}", addressToPodMap);

            } // End of try-with-resources

            // Update Node Registry
            log.debug("K8sPodWatcher: Updating Node Registry");
            Map<String, NodeRegistryEntry> addressToNodeEntryMap = nodeRegistry.getNodes().stream()
                    .collect(Collectors.toMap(NodeRegistryEntry::getIpAddress, entry -> entry));

            // New Pods
            HashMap<String, Set<PodEntry>> newPods = new HashMap<>(addressToPodMap);
            newPods.keySet().removeAll(addressToNodeEntryMap.keySet());
            if (! newPods.isEmpty()) {
                log.trace("K8sPodWatcher: New Pods found: {}", newPods);
                /*newPods.forEach((address, podSet) -> {
                    if (podSet.size()==1)
                        nodeRegistry.addNode(null, podSet.iterator().next().getPodUid());
                });*/
            } else {
                log.trace("K8sPodWatcher: No new Pods");
            }

            // Node Entries to be removed
            HashMap<String, NodeRegistryEntry> oldEntries = new HashMap<>(addressToNodeEntryMap);
            oldEntries.keySet().removeAll(addressToPodMap.keySet());
            if (! oldEntries.isEmpty()) {
                log.trace("K8sPodWatcher: Node entries to be removed: {}", oldEntries);
            } else {
                log.trace("K8sPodWatcher: No node entries to remove");
            }

        } catch (Exception e) {
            log.warn("K8sPodWatcher: Error while running doWatch: ", e);
        }
    }

    @Data
    private static class NodeEntry {
        private final String nodeUid;
        private final String nodeName;

        public NodeEntry(Node node) {
            nodeUid = node.getMetadata().getUid();
            nodeName = node.getMetadata().getName();
        }
    }

    @Data
    private static class PodEntry {
        private final String podUid;
        private final String podIP;
        private final String podName;
        private final String hostIP;
        private final Map<String, String> labels;

        public PodEntry(Pod pod) {
            podUid = pod.getMetadata().getUid();
            podIP = pod.getStatus().getPodIP();
            podName = pod.getMetadata().getName();
            hostIP = pod.getStatus().getHostIP();
            labels = Collections.unmodifiableMap(pod.getMetadata().getLabels());
        }
    }
}
