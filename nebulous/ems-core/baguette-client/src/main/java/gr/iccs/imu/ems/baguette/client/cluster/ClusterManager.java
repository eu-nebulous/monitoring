/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.cluster;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.cluster.protocol.HeartbeatMembershipProtocol;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.atomix.core.Atomix;
import io.atomix.core.AtomixBuilder;
import io.atomix.protocols.backup.partition.PrimaryBackupPartitionGroup;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.utils.net.Address;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Data
@Component
@EqualsAndHashCode(callSuper = true)
public class ClusterManager extends AbstractLogBase {

	private static final String NODE_NAME_PREFIX = "node_";

	private ClusterManagerProperties properties;
	private BrokerUtil.NodeCallback callback;
	private ClusterCLI cli;

	private MemberScoreFunction scoreFunction = new MemberScoreFunction("-1");

	@Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
	private Address localAddress = null;
	@Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
	private NodeDiscoveryProvider bootstrapDiscoveryProvider = null;
	@Setter(AccessLevel.NONE)
	private Atomix atomix = null;
	@Setter(AccessLevel.NONE)
	private BrokerUtil brokerUtil = null;

	@Autowired
	private TaskScheduler taskScheduler;
	@Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
	private ScheduledFuture<?> checkerTask;

	// ------------------------------------------------------------------------

	public synchronized ClusterCLI getCli() {
		if (cli==null) {
			cli = new ClusterCLI(this);
			cli.setLogEnabled(isLogEnabled());
			cli.setOutEnabled(isOutEnabled());
		}
		return cli;
	}

	public Atomix getAtomix() {
		if (atomix==null) throw new IllegalStateException("Not initialized");
		return atomix;
	}

	public BrokerUtil getBrokerUtil() {
		if (brokerUtil==null) throw new IllegalStateException("Not initialized");
		return brokerUtil;
	}

	public Set<Member> getMembers() {
		return getAtomix().getMembershipService().getMembers();
	}

	public Member getLocalMember() {
		return getAtomix().getMembershipService().getLocalMember();
	}

	public Address getLocalAddress() {
		return getLocalMember().address();
	}

	public Properties getLocalMemberProperties() {
		return getAtomix().getMembershipService().getLocalMember().properties();
	}

	public void setCallback(BrokerUtil.NodeCallback callback) {
		this.callback = callback;
		if (brokerUtil!=null) brokerUtil.setCallback(callback);
	}

	// ------------------------------------------------------------------------

	public boolean isInitialized() {
		return atomix!=null;
	}

	public boolean isRunning() {
		return (atomix!=null && atomix.isRunning());
	}

	public void initialize() {
		initialize(properties, callback);
	}

	public void initialize(ClusterManagerProperties p) {
		initialize(p, this.callback);
	}

	public void initialize(ClusterManagerProperties p, BrokerUtil.NodeCallback callback) {
		// Store properties and callback
		if (p!=null) this.properties = p;
		if (callback!=null) this.callback = callback;

		// Set logging and output flags
		setLogEnabled(properties.isLogEnabled());
		setOutEnabled(properties.isOutEnabled());

		// Initialize member scoring function
		this.scoreFunction = properties.getScore()!=null
				? MemberScoreFunction.builder()
						.formula(properties.getScore().getFormula())
						.defaultScore(properties.getScore().getDefaultScore())
						.argumentDefaults(properties.getScore().getDefaultArgs())
						.throwExceptions(properties.getScore().isThrowException())
						.build()
				: this.scoreFunction;

		// Get local address and port
		localAddress = properties.getLocalNode().getAddress();
		log_debug("CLM: Provided local-address: {}", localAddress);
		if (localAddress==null) {
			//localAddress = Address.from(getLocalHostName() + ":1234");
			localAddress = Address.from(getLocalHostAddress() + ":1234");
			log_debug("CLM: Resolving local-address: {}", localAddress);
		}
		log_info("CLM: Local address used for building Atomix: {}", localAddress);

		// Initialize Membership provider
		bootstrapDiscoveryProvider = buildNodeDiscoveryProvider(properties.getMemberAddresses());

		// Create Atomix and Join/start cluster
		atomix = buildAtomix(properties, localAddress, bootstrapDiscoveryProvider);
		brokerUtil = new BrokerUtil(this, callback);
		brokerUtil.setLogEnabled(isLogEnabled());
		brokerUtil.setOutEnabled(isOutEnabled());
	}

	public void joinCluster() {
		joinCluster(getProperties().isElectionOnJoin());
	}

	public void joinCluster(boolean startElection) {
		// Initialize cluster if needed
		if (atomix==null)
			initialize();

		// Start/Join cluster
		log_info("CLM: Joining cluster...");
		long startTm = System.currentTimeMillis();
		atomix.start().join();
		long endTm = System.currentTimeMillis();
		log_debug("CLM: Joined cluster in {}ms", endTm-startTm);

		// Populate default local member properties
		Member localMember = atomix.getMembershipService().getLocalMember();
		String addrStr = localMember.address().host() + ":" + localMember.address().port();
		atomix.getMembershipService().getLocalMember().properties().setProperty("address", addrStr);
		atomix.getMembershipService().getLocalMember().properties().setProperty("uuid", UUID.randomUUID().toString());
		brokerUtil.setLocalStatus(BrokerUtil.NODE_STATUS.CANDIDATE);

		// Add membership listener
		atomix.getMembershipService().addListener(event -> {
			log_debug("CLM: {}: node={}", event.type(), event.subject());
			if (event.type()!=ClusterMembershipEvent.Type.REACHABILITY_CHANGED) {
				if (event.type()!=ClusterMembershipEvent.Type.METADATA_CHANGED) {
					log_info("CLM: {}: node={}", event.type(), event.subject().id().id());
					brokerUtil.checkBroker();
				}
				if (callback!=null)
					callback.clusterChanged(event);
			}
		});

		// Add broker message listener
		atomix.getCommunicationService().subscribe(BrokerUtil.NODE_MESSAGE_TOPIC, m -> {
			brokerUtil.processBrokerMessage(m);
			return CompletableFuture.completedFuture("ok");
		});

		// Start election if no broker exists
		if (startElection) {
			brokerUtil.checkBroker();
		}

		// Start cluster checker
		if (properties.isClusterCheckerEnabled()) {
			long delay = Math.max(properties.getClusterCheckerDelay(), 10000L);
			log_info("CLM: Starting cluster checker (delay: {})...", delay);
			checkerTask = taskScheduler.scheduleWithFixedDelay(() -> {
				if (brokerUtil != null)
					brokerUtil.checkBrokerNumber();
				else
					log_warn("CLM: Cluster checker: BrokerUtil is NULL  (is it a BUG?)");
			}, Duration.ofMillis(delay));
		} else {
			log_warn("CLM: Cluster checker is DISABLED");
		}
	}

	public void waitToJoin() {
		while (true) {
			if (isInitialized() && isRunning()) break;
			try { Thread.sleep(500); } catch (InterruptedException e) { break; }
		}
		if (callback!=null)
			callback.joinedCluster();
	}

	public void waitToJoin(long waitForMillis) {
		long startTm = System.currentTimeMillis();
		long endTm = startTm + waitForMillis;
		while (true) {
			if (isInitialized() && isRunning()) break;
			long waitFor = Math.min(500, endTm-System.currentTimeMillis());
			try { Thread.sleep(waitFor); } catch (InterruptedException e) { break; }
		}
		if (callback!=null)
			callback.joinedCluster();
	}

	public void leaveCluster() {
		// Stop cluster checker
		if (checkerTask!=null && !checkerTask.isCancelled()) {
			log_info("CLM: Stopping cluster checker...");
			checkerTask.cancel(true);
			checkerTask = null;
		}

		// Leave cluster
		log_info("CLM: Leaving cluster...");
		long startTm = System.currentTimeMillis();
		if (atomix.isRunning())
			atomix.stop().join();
		long endTm = System.currentTimeMillis();
		log_debug("CLM: Left cluster in {}ms", endTm-startTm);
		atomix = null;
		brokerUtil = null;
		if (callback!=null)
			callback.leftCluster();
	}

	// ------------------------------------------------------------------------

	public static String getLocalHostName() {
		String hostname = null;
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			//log_error("Exception while getting Node hostname: ", e);
		}
		if (StringUtils.isBlank(hostname))
			hostname = getLocalHostAddress();
		return hostname;
	}

	public static String getLocalHostAddress() {
		String address = null;
		try {
			address = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			//log_error("Exception while getting Node local address: ", e);
		}
		if (StringUtils.isBlank(address))
			address = UUID.randomUUID().toString();
		return address;
	}

	// ------------------------------------------------------------------------

	private String createMemberName(int port) { return createMemberName(getLocalHostName()+":"+port); }
	private String createMemberName(String address) {
		return NODE_NAME_PREFIX+address.replace(":", "_");
	}

	private Node createNode(String address, String port) { return createNode(address, Integer.parseInt(port)); }
	private Node createNode(String address, int port) { return createNode(address+":"+port); }
	private Node createNode(String address) {
		return Node.builder()
				.withId(createMemberName(address))
				.withAddress(Address.from(address))
				.build();
	}
	private Node createNode(ClusterManagerProperties.NodeProperties nodeProperties) {
		String nodeId = nodeProperties.getId();
		if (StringUtils.isBlank(nodeId))
			nodeId = createMemberName(nodeProperties.getAddress().port());
		return Node.builder()
				.withId(nodeId)
				.withAddress(nodeProperties.getAddress())
				.build();
	}

	public static Address getAddressFromString(String localAddressStr) {
		Address localAddress;
		localAddressStr = localAddressStr.trim();
		if (StringUtils.isBlank(localAddressStr)) {
			localAddress = Address.local();
		} else
		if (StringUtils.isNumeric(localAddressStr)) {
			localAddress = Address.from(Integer.parseInt(localAddressStr));
		} else {
			localAddress = Address.from(localAddressStr);
		}
		return localAddress;
	}

	private NodeDiscoveryProvider buildNodeDiscoveryProvider(List<String> addresses) {
		return buildNodeDiscoveryProviderFromProperties(
				addresses!=null
						? addresses.stream()
								.map(ClusterManager::getAddressFromString)
								.map(address -> new ClusterManagerProperties.NodeProperties(null, address, null))
								.collect(Collectors.toList())
						: null);
	}

	private NodeDiscoveryProvider buildNodeDiscoveryProviderFromProperties(List<ClusterManagerProperties.NodeProperties> nodePropertiesList) {
		List<Node> nodes = new ArrayList<>();
		if (nodePropertiesList!=null) {
			nodes = nodePropertiesList.stream().map(this::createNode).collect(Collectors.toList());
		}
		log_info("CLM: Building Atomix: Other members: {}", nodes);
		return BootstrapDiscoveryProvider.builder()
				.withNodes(nodes)
				//.withHeartbeatInterval(Duration.ofSeconds(5))
				//.withFailureThreshold(2)
				//.withFailureTimeout(Duration.ofSeconds(1))
				.build();
	}

	private MemberId[] getMemberIds(Set<Node> nodes) {
		List<MemberId> memberIdList = new ArrayList<>();
		for (Node node : nodes)
			memberIdList.add(MemberId.from(node.id().id()));
		return memberIdList.toArray(new MemberId[0]);
	}

	private Member[] getMembers(Set<Node> nodes) {
		List<Member> memberList = new ArrayList<>();
		for (Node node : nodes)
			memberList.add(Member.builder()
					.withId(node.id().id())
					.withAddress(node.address())
					.build());
		return memberList.toArray(new Member[0]);
	}

	private Atomix buildAtomix(ClusterManagerProperties properties, Address localAddress, NodeDiscoveryProvider bootstrapDiscoveryProvider) {
		// Configuring local cluster member
		AtomixBuilder atomixBuilder = Atomix.builder();

		// Cluster id
		String clusterId = properties.getClusterId();
		if (StringUtils.isNotBlank(clusterId)) {
			log_info("CLM: Building Atomix: Cluster-id: {}", clusterId);
			atomixBuilder.withClusterId(clusterId);
		}

		// Local member id and address
		String memId = properties.getLocalNode().getId();
		memId = StringUtils.isBlank(memId) ? createMemberName(localAddress.port()) : memId;
		MemberId localMemberId = MemberId.from(memId);
		log_info("CLM: Building Atomix: Local-Member-Id: {}", localMemberId);
		log_info("CLM: Building Atomix: Local-Member-Address: {}", localAddress);
		atomixBuilder
				.withMemberId(localMemberId)
				.withAddress(localAddress)
				.withProperties(properties.getLocalNode().getProperties());

		// Configure membership protocol
		boolean useSwim = properties.isUseSwim();
		long failureTimeout = Math.max(100L, properties.getFailureTimeout());
		GroupMembershipProtocol memProto;
		atomixBuilder
				.withMembershipProtocol(memProto = useSwim
						? SwimMembershipProtocol.builder()
								//.withGossipInterval(Duration.ofMillis(250))
								//.withGossipFanout(2)
								.withFailureTimeout(Duration.ofMillis(failureTimeout))
								.build()
						: HeartbeatMembershipProtocol.builder()
								//.withHeartbeatInterval(Duration.ofMillis(1000))
								.withFailureTimeout(Duration.ofMillis(failureTimeout))
								//.withFailureThreshold(2)
								.build()
				);
		log_info("CLM: Building Atomix: Membership protocol: {}", memProto.getClass().getSimpleName());

		// Configure Management and Partition groups
		boolean usePBInMg = properties.isUsePBInMg();
		boolean usePBInPg = properties.isUsePBInPg();
		String mgName = properties.getMgName();
		String pgName = properties.getPgName();
		if (StringUtils.isBlank(mgName)) mgName = "system";
		if (StringUtils.isBlank(pgName)) pgName = "data";
		log_debug("CLM: Building Atomix: Cluster Groups: mg-type-PB={}, pg-type-PB={}, mg-name={}, pg-name={}",
				usePBInMg, usePBInPg, mgName, pgName);
		atomixBuilder
				.withManagementGroup(usePBInMg
						? PrimaryBackupPartitionGroup.builder(mgName)
								.withNumPartitions(1)
								//.withMemberGroupStrategy(MemberGroupStrategy.NODE_AWARE)
								.build()
						: RaftPartitionGroup.builder(mgName)
								.withNumPartitions(1)
								.withMembers(getMemberIds(bootstrapDiscoveryProvider.getNodes()))
								//.withMembers(getMembers(bootstrapDiscoveryProvider.getNodes()))
								//.withDataDirectory(new File("raft-mg"))
								//.withMemberGroupStrategy(MemberGroupStrategy.NODE_AWARE)
								.build()
				)
				.withPartitionGroups(usePBInPg
						? PrimaryBackupPartitionGroup.builder(pgName)
								.withNumPartitions(8)
								//.withMemberGroupStrategy(MemberGroupStrategy.NODE_AWARE)
								.build()
						: RaftPartitionGroup.builder(pgName)
								.withNumPartitions(8)
								.withMembers(getMemberIds(bootstrapDiscoveryProvider.getNodes()))
								//.withMembers(getMembers(bootstrapDiscoveryProvider.getNodes()))
								//.withDataDirectory(new File("raft-pg"))
								//.withMemberGroupStrategy(MemberGroupStrategy.NODE_AWARE)
								.build()
				);

		// Configure Bootstrap Discovery Provider
		atomixBuilder
				//.withMulticastEnabled()
				.withMembershipProvider(bootstrapDiscoveryProvider);

		// Configure TLS for messaging
		log_info("CLM: Building Atomix: TLS enabled={}", properties.getTls().isEnabled());
		if (properties.getTls().isEnabled()) {
			atomixBuilder
					.withTlsEnabled(true)
					.withKeyStore(properties.getTls().getKeystore())
					.withKeyStorePassword(properties.getTls().getKeystorePassword())
					.withTrustStore(properties.getTls().getTruststore())
					.withTrustStorePassword(properties.getTls().getTruststorePassword());
		}

		return atomixBuilder.build();
	}
}
