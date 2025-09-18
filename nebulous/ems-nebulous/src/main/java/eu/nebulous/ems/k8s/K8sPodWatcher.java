/*
 * Copyright (C) 2017-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.k8s;

import eu.nebulous.ems.EmsNebulousProperties;
import gr.iccs.imu.ems.baguette.server.ClientShellCommand;
import gr.iccs.imu.ems.common.k8s.K8sClient;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.io.Serializable;
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
    private final K8sServiceProperties properties;
    private final TaskScheduler taskScheduler;
    private final EmsNebulousProperties emsNebulousProperties;

    private String EMS_SERVER_POD_UID;
    private String APP_POD_LABEL;

    @Override
    public void afterPropertiesSet() throws Exception {
        boolean envVarEnable = Boolean.parseBoolean(K8sClient.getConfig("K8S_WATCHER_ENABLED", "false"));
        if (properties.isEnabled() || envVarEnable) {
            Instant initDelay = Instant.now().plus(properties.getInitDelay());
            Duration period = properties.getPeriod();
            taskScheduler.scheduleAtFixedRate(this::doWatch, initDelay, period);
            log.info("K8sPodWatcher: Enabled  (running every {}, init-delay={})", period, properties.getInitDelay());
        } else {
            log.info("K8sPodWatcher: Disabled  (to enable set 'k8s-watcher.enable' property or K8S_WATCHER_ENABLED env. var. to true)");
        }
        EMS_SERVER_POD_UID = StringUtils.defaultIfBlank(
                emsNebulousProperties.getEmsServerPodUid(), "");
        APP_POD_LABEL = emsNebulousProperties.getAppPodLabel();
    }

    private void doWatch() {
        try {
            // Get running pods and apply exclusions
            log.debug("K8sPodWatcher: BEGIN: Retrieving active Kubernetes cluster pods");

            HashMap<String, K8sClient.PodEntry> uuidToPodsMap = new HashMap<>();
            HashMap<String, Set<K8sClient.PodEntry>> podsPerHost = new HashMap<>();
            try (K8sClient client = K8sClient.create()) {
                List<Pod> podsList = client.getRunningPodsInfo();
                log.trace("K8sPodWatcher: Got pods list: {}", podsList);
                if (podsList==null) {
                    log.warn("K8sPodWatcher: PROBLEM with Kubernetes? No pods retrieved from K8S API server");
                    log.debug("K8sPodWatcher: END");
                    return;
                }

                podsList.forEach(pod -> {
                    String ns = pod.getMetadata().getNamespace();
                    String appLabelValue = pod.getMetadata().getLabels().get(APP_POD_LABEL);
                    log.trace("K8sPodWatcher: Got pod: uid={}, name={}, address={}, namespace={}, app-label={}",
                            pod.getMetadata().getUid(), pod.getMetadata().getName(), pod.getStatus().getPodIP(),
                            ns, appLabelValue);
                    if (properties.getIgnorePodsInNamespaces().contains(ns))
                        return;
                    if (StringUtils.isNotBlank(appLabelValue) && properties.getIgnorePodsWithAppLabel().contains(appLabelValue))
                        return;
                    K8sClient.PodEntry entry = new K8sClient.PodEntry(pod);
                    uuidToPodsMap.put(pod.getMetadata().getUid(), entry);
                    String podHostIp = pod.getStatus().getHostIP();
                    podsPerHost.computeIfAbsent(podHostIp, s -> new HashSet<>()).add(entry);
                    log.trace("K8sPodWatcher: Keeping pod: uid={}, name={}, address={}",
                            pod.getMetadata().getUid(), pod.getMetadata().getName(), pod.getStatus().getPodIP());
                });

            } // End of try-with-resources
            log.debug("K8sPodWatcher: Active Kubernetes cluster pods: uuidToPodsMap: {}", uuidToPodsMap);
            log.debug("K8sPodWatcher: Active Kubernetes cluster pods:   podsPerHost: {}", podsPerHost);

            // Group running pods per host IP
            log.debug("K8sPodWatcher: Processing active pods per active EMS client: ems-clients: {}", ClientShellCommand.getActive());
            Map<ClientShellCommand,List<K8sClient.PodEntry>> emsClientPodLists = new HashMap<>();
            ClientShellCommand.getActive().forEach(csc -> {
                //String id = csc.getId();
                String emsClientPodUuid = csc.getClientId();
                String address = csc.getClientIpAddress();
                log.trace("K8sPodWatcher: EMS client: pod-uid={}, address={}", emsClientPodUuid, address);

                K8sClient.PodEntry emsClientPod = uuidToPodsMap.get(emsClientPodUuid);
                log.trace("K8sPodWatcher: EMS client: pod-entry: {}", emsClientPod);
                String emsClientPodHostIp = emsClientPod.hostIP();
                Set<K8sClient.PodEntry> podsInHost = podsPerHost.get(emsClientPodHostIp);
                log.trace("K8sPodWatcher: EMS client: pod-host-address={}, pods-in-host: {}", emsClientPodHostIp, podsInHost);
                List<K8sClient.PodEntry> podsInHostWithoutEmsClient = podsInHost.stream()
                        .filter(pod -> ! pod.podUid().equalsIgnoreCase(EMS_SERVER_POD_UID))
                        .filter(pod -> ! pod.podUid().equalsIgnoreCase(emsClientPodUuid))
                        .toList();
                log.trace("K8sPodWatcher: EMS client: pod-host-address={}, Filtered-pods-in-host: {}", emsClientPodHostIp, podsInHostWithoutEmsClient);
                LinkedList<K8sClient.PodEntry> list = new LinkedList<>();
                list.add(emsClientPod);
                list.addAll(podsInHostWithoutEmsClient);
                emsClientPodLists.put(csc, list);
            });
            log.debug("K8sPodWatcher: Active Kubernetes cluster pods per EMS client: {}", emsClientPodLists);

            // Update EMS client configurations
            log.debug("K8sPodWatcher: Updating EMS client configurations with active pods: {}", emsClientPodLists);
            emsClientPodLists.forEach((csc, podList) -> {
                K8sClient.PodEntry emsClientPod = podList.get(0);
                List<K8sClient.PodEntry> otherPods = podList.subList(1, podList.size());
                String clientId = csc.getClientId();
                String hostIp = emsClientPod.hostIP();

                Set<Serializable> oldPodSet = csc.getClientConfiguration().getNodesWithoutClient();
                Set<Serializable> newPodSet = otherPods.stream().map(K8sClient.PodEntry::podIP).collect(Collectors.toSet());
                log.info("K8sPodWatcher: EMS client: {} @{} -- Old pod set: {} -- New pod set: {}", clientId, hostIp, oldPodSet, newPodSet);
                csc.getClientConfiguration().setNodesWithoutClient(newPodSet);
                csc.getClientConfiguration().setPodInfo(new HashSet<>(otherPods));

                log.trace("K8sPodWatcher: EMS client: {} @{} -- Sending configuration to EMS client", clientId, hostIp);
                csc.sendClientConfiguration();
            });
            log.debug("K8sPodWatcher: Updated EMS client configurations with active pods");

            log.debug("K8sPodWatcher: END");

        } catch (Exception e) {
            log.warn("K8sPodWatcher: ERROR while running doWatch: ", e);
        }
    }
}
