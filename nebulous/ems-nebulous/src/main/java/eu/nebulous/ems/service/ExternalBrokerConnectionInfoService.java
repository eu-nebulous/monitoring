/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.service;

import gr.iccs.imu.ems.common.k8s.K8sClient;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class ExternalBrokerConnectionInfoService implements InitializingBean {
	private final ExternalBrokerServiceProperties externalBrokerServiceProperties;
	private String brokerAddress;
	private int brokerPort = -1;

	@Getter
	private static ExternalBrokerConnectionInfoService instance;

	@Override
	public void afterPropertiesSet() throws Exception {
		instance = this;
		if (! externalBrokerServiceProperties.isEnabled()) {
			log.debug("ExternalBrokerConnectionInfoService: External Broker service is disabled");
			return;
		}
		collectExternalBrokerConnectionInfo();
	}

	private void collectExternalBrokerConnectionInfo() {
		log.debug("ExternalBrokerConnectionInfoService: collectExternalBrokerConnectionInfo: BEGIN: Mode: {}",
				externalBrokerServiceProperties.getConnectionInfoCollectionMode());
		if (externalBrokerServiceProperties.getConnectionInfoCollectionMode()==ExternalBrokerServiceProperties.COLLECTION_MODE.K8S) {
			try (K8sClient client = K8sClient.create()) {
				// Get external broker service parameters
				@NonNull String serviceName = externalBrokerServiceProperties.getBrokerServiceName();
				String serviceNamespace = externalBrokerServiceProperties.getBrokerServiceNamespace();
				log.debug("ExternalBrokerConnectionInfoService: collectExternalBrokerConnectionInfo: service={}, namespace={}", serviceName, serviceNamespace);

				// Query Kubernetes API server about external broker service connection info
				Map<String, Object> connectionInfo = client.getServiceConnectionInfo(serviceName, serviceNamespace);
				log.debug("ExternalBrokerConnectionInfoService: collectExternalBrokerConnectionInfo: connectionInfo: {}", connectionInfo);

				// Update 'externalBrokerServiceProperties'
				if (connectionInfo!=null) {
					if (connectionInfo.get("external-addresses")!=null
							&& ! ((List)connectionInfo.get("external-addresses")).isEmpty())
						brokerAddress = ((List<String>) connectionInfo.get("external-addresses")).get(0);
					brokerPort = connectionInfo.containsKey("node-port")
							? (int) connectionInfo.get("node-port") : -1;
				}

			} catch (IOException e) {
				log.warn("NebulousInstallationContextProcessorPlugin: EXCEPTION while querying Kubernetes API server: ", e);
			}
		}
		log.debug("ExternalBrokerConnectionInfoService: collectExternalBrokerConnectionInfo: END: mode={}, address={}, port={}",
				externalBrokerServiceProperties.getConnectionInfoCollectionMode(), brokerAddress, brokerPort);
	}

	public boolean isEnabled() {
		return externalBrokerServiceProperties.isEnabled();
	}

	public String getBrokerAddress() {
		if (externalBrokerServiceProperties.isEnabled())
			return StringUtils.defaultIfBlank(brokerAddress, externalBrokerServiceProperties.getBrokerAddress());
		return null;
	}

	public int getBrokerPort() {
		if (externalBrokerServiceProperties.isEnabled())
			return brokerPort > 0 ? brokerPort : externalBrokerServiceProperties.getBrokerPort();
		return -1;
	}

	public String getBrokerUsername() {
		if (externalBrokerServiceProperties.isEnabled())
			return externalBrokerServiceProperties.getBrokerUsername();
		return null;
	}

	public String getBrokerPassword() {
		if (externalBrokerServiceProperties.isEnabled())
			return externalBrokerServiceProperties.getBrokerPassword();
		return null;
	}
}