/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.cluster;

import io.atomix.core.Atomix;
import lombok.*;

import java.util.stream.Collectors;

@Data
public class ClusterTest implements Runnable {

	@NonNull
	private final ClusterManager clusterManager;

	@Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
	private Thread runner;
	@Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
	private boolean keepRunning;
	private long delay = 5000;

	public void startTest(long delay) {
		checkRunning();
		if (delay < 1) throw new IllegalArgumentException("ClusterTest delay must be positive: " + delay);
		this.delay = delay;
		startTest();
	}

	public synchronized void startTest() {
		checkRunning();
		runner = new Thread(this);
		runner.setDaemon(true);
		keepRunning = true;
		runner.start();
	}

	public synchronized void stopTest() {
		checkNotRunning();
		keepRunning = false;
		runner.interrupt();
		runner = null;
	}

	private void checkRunning() {
		if (keepRunning)
			throw new IllegalStateException("ClusterTest is already running");
	}

	private void checkNotRunning() {
		if (!keepRunning)
			throw new IllegalStateException("ClusterTest is not running");
	}

	public void run() {
		// Start doing work...
		Atomix atomix = clusterManager.getAtomix();
		int iterations = 0;
		while (keepRunning) {
			iterations++;
			clusterManager.log_info("-- Iter={} ---------------------------------------", iterations);

			// Get cluster members
			clusterManager.log_info("-- CLUSTER-MEMBERS: {}", atomix.getMembershipService().getMembers().stream()
					.map(m -> "\n  "+m.id().id()
							+ "/" + clusterManager.getBrokerUtil().getNodeStatus(m)
							+ "/" + m.properties().getProperty("address", "---")
							+ "/" + (m.isActive()?"active":"inactive")
							+ (!m.isReachable() ? "/unreachable" : ""))
					.collect(Collectors.toList()));

			// Sleep for 5 seconds
			try { Thread.sleep(delay); } catch (Exception e) {}
		}
	}
}
