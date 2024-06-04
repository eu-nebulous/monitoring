/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.k8s;

import gr.iccs.imu.ems.control.plugin.PostTranslationPlugin;
import gr.iccs.imu.ems.control.util.TopicBeacon;
import gr.iccs.imu.ems.translate.TranslationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class K8sEmsClientDeploymentPostTranslationPlugin implements PostTranslationPlugin, InitializingBean {
//	private final K8sServiceProperties properties;
//	private final ApplicationContext applicationContext;
//	private final EmsNebulousProperties emsNebulousProperties;
//
//	private String applicationId;

	@Override
	public void afterPropertiesSet() throws Exception {
		/*applicationId = emsNebulousProperties.getApplicationId();
		log.info("K8sEmsClientDeploymentPostTranslationPlugin: Application Id (from Env.): {}", applicationId);*/
	}

	@Override
	public void processTranslationResults(TranslationContext translationContext, TopicBeacon topicBeacon) {
		/*String oldAppId = this.applicationId;
		this.applicationId = translationContext.getAppId();
		log.info("K8sEmsClientDeploymentPostTranslationPlugin: Set applicationId to: {}  -- was: {}", applicationId, oldAppId);

		// Call control-service to deploy EMS clients
		if (properties.isDeployEmsClientsOnKubernetesEnabled()) {
			try {
				log.info("K8sEmsClientDeploymentPostTranslationPlugin: Start deploying EMS clients...");
				String id = "dummy-" + System.currentTimeMillis();
				Map<String, Object> nodeInfo = new HashMap<>(Map.of(
						"id", id,
						"name", id,
						"type", "K8S",
						"provider", "Kubernetes",
						"zone-id", ""
				));
				applicationContext.getBean(NodeRegistrationCoordinator.class)
						.registerNode("", nodeInfo, translationContext);
				log.debug("K8sEmsClientDeploymentPostTranslationPlugin: EMS clients deployment started");
			} catch (Exception e) {
				log.warn("K8sEmsClientDeploymentPostTranslationPlugin: EXCEPTION while starting EMS client deployment: ", e);
			}
		} else
			log.info("K8sEmsClientDeploymentPostTranslationPlugin: EMS clients deployment is disabled");*/
    }
}