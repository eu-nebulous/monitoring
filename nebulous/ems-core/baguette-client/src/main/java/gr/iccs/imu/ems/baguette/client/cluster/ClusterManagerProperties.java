/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.cluster;

import io.atomix.utils.net.Address;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Properties;

@Data
@Configuration
@ConfigurationProperties(prefix = "cluster")
public class ClusterManagerProperties {
	private String clusterId = "local-cluster";
	private NodeProperties localNode = new NodeProperties();
	private List<String> memberAddresses;

	private boolean useSwim = true;			// ...else the Heartbeat membership protocol will be used
	private long failureTimeout = 10000;	// The Atomix default failure timeout for both membership protocols
	private long testInterval = -1;			// Print cluster node status every X millis (negative numbers should turn off feature)

	private boolean logEnabled;
	private boolean outEnabled = true;

	private boolean joinOnInit = true;
	private boolean electionOnJoin;

	private boolean clusterCheckerEnabled = true;
	private long clusterCheckerDelay = 30000L;

	private boolean usePBInMg = true;
	private boolean usePBInPg = true;
	private String mgName = "system";
	private String pgName = "data";

	private TlsProperties tls = new TlsProperties();

	private ScoreFunctionProperties score;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class NodeProperties {
		private String id;
		private Address address;
		private Properties properties = new Properties();

		public NodeProperties(String address) {
			this.address = Address.from(address);
		}

		public void setAddress(String address) {
			this.address = ClusterManager.getAddressFromString(address);
		}
	}

	@Data
	@ToString(exclude = {"keystorePassword", "truststorePassword"})
	public static class TlsProperties {
		private boolean enabled;
		private String keystore;
		private String keystorePassword;
		private String truststore;
		private String truststorePassword;
		private String keystoreDir;
	}

	@Data
	public static class ScoreFunctionProperties {
		private String formula;
		private double defaultScore;
		private Properties defaultArgs;
		private boolean throwException;
	}
}
