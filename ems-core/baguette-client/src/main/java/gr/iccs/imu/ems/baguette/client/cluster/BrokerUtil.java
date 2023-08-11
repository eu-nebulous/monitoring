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
import io.atomix.core.Atomix;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static gr.iccs.imu.ems.baguette.client.cluster.BrokerUtil.NODE_STATUS.*;

@RequiredArgsConstructor
public class BrokerUtil extends AbstractLogBase {
    public enum NODE_STATUS { AGGREGATOR, CANDIDATE, NOT_CANDIDATE, INITIALIZING, STEPPING_DOWN, RETIRING, NOT_SET }

    protected final static Collection<NODE_STATUS> BROKER_STATUSES = Arrays.asList(AGGREGATOR, RETIRING);
    protected final static Collection<NODE_STATUS> CANDIDATE_STATUSES = Arrays.asList(CANDIDATE, AGGREGATOR, INITIALIZING);
    protected final static Collection<NODE_STATUS> NON_CANDIDATE_STATUSES = Arrays.asList(NOT_CANDIDATE, STEPPING_DOWN, RETIRING, NOT_SET);

    public final static String NODE_MESSAGE_TOPIC = "NODE-MESSAGE-TOPIC";
    public final static String STATUS_PROPERTY = "node-status";

    protected final static String MESSAGE_ELECTION = "election";
    protected final static String MESSAGE_APPOINT = "appoint";
    protected final static String MESSAGE_INITIALIZE = "initialize";
    protected final static String MESSAGE_READY = "ready";
    private static final String MARKER_NEW_CONFIGURATION = "New config: ";

    private final Atomix atomix;
    private final ClusterManager clusterManager;
    private final AtomicBoolean backOff = new AtomicBoolean();

    @Getter @Setter
    private NodeCallback callback;

    public BrokerUtil(ClusterManager clusterManager, NodeCallback callback) {
        this.clusterManager = clusterManager;
        this.atomix = clusterManager.getAtomix();
        this.callback = callback;
    }

    void processBrokerMessage(Object m) {
        if (m == null) return;
        String message = m.toString();
        log_info("BRU: **** Broker message received: {}", message);

        String messageType = message.split(" ", 2)[0];
        if (MESSAGE_ELECTION.equalsIgnoreCase(messageType)) {
            // Get excluded nodes (if any)
            List<String> excludes = Arrays.stream(message.split(" "))
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .filter(s -> s.startsWith("-"))
                    .map(s -> s.substring(1))
                    .collect(Collectors.toList());
            // Start election
            log_info("BRU: **** BROKER: Starting Broker election: ");
            election(excludes);
        } else if (MESSAGE_APPOINT.equalsIgnoreCase(messageType)) {
            String newBrokerId = message.split(" ", 2)[1];
            appointment(newBrokerId);
        } else if (MESSAGE_INITIALIZE.equalsIgnoreCase(messageType)) {
            String newBrokerId = message.split(" ", 2)[1];
            log_info("BRU: **** BROKER: New Broker initializes: {}", newBrokerId);
            // Back off if i am also initializing but have a lower score or command order
            backOff();
        } else if (MESSAGE_READY.equalsIgnoreCase(messageType)) {
            String[] part = message.split(" ", 3);
            String brokerId = part[1];
            String newConfig = part[2];
            // Strip 'New config.' marker
            if (newConfig.startsWith(MARKER_NEW_CONFIGURATION)) {
                newConfig = newConfig.substring(MARKER_NEW_CONFIGURATION.length()).trim();
            } else {
                log_error("BRU: !!!!  BUG: New configuration not properly marked: {}  !!!!", newConfig);
            }
            log_info("BRU: **** BROKER: New Broker is ready: {}, New config: {}", brokerId, newConfig);

            // If i am not the new Broker then reset broker status
            Member local = getLocalMember();
            NODE_STATUS localStatus = getLocalStatus();
            log_debug("BRU: Nodes: local={}, broker={}", local.id().id(), brokerId);
            if (BROKER_STATUSES.contains(localStatus))
                if (!local.id().id().equals(brokerId)) {
                    // Temporarily make node unavailable for being elected as Broker, until step down completes
                    setLocalStatus(STEPPING_DOWN);

                    // Step down
                    log_info("BRU: Old broker steps down: {}", local.id().id());
                    if (callback!=null)
                        callback.stepDown();

                    // After step down, and if node hasn't retired, node status changes to 'candidate'
                    if (RETIRING!=localStatus)
                        setLocalStatus(CANDIDATE);
                    else
                        setLocalStatus(NOT_CANDIDATE);
                }

            // Pass new configuration to callback
            log_info("BRU: Node configuration updated: {}", newConfig);
            if (callback!=null) {
                callback.setConfiguration(newConfig);
            }
        } else
            log_warn("BRU:    BROKER: Unknown message received: {}", message);
    }

    private void aggregatorStepDown() {
        // Save previous status
        NODE_STATUS oldStatus = getLocalStatus();

        // Temporarily make node unavailable for being elected as Aggregator, until step down completes
        setLocalStatus(STEPPING_DOWN);

        switch (oldStatus) {
            case CANDIDATE:
                log_debug("BRU: Node is not Aggregator. Clearing back-off flag");
                backOff.set(false); break;
            case INITIALIZING:
                log_debug("BRU: Node is initializing. Back-off flag set");
                backOff.set(true); break;
            case AGGREGATOR:
                // Step down
                log_info("BRU: Aggregator steps down: {}", getLocalMember().id().id());
                if (callback!=null)
                    callback.stepDown();
                backOff.set(false);
                log_info("BRU: Old aggregator stepped down");
                break;
            case STEPPING_DOWN:
                log_debug("stepDown(): Node is already stepping down. Nothing to do");
                backOff.set(false);
                break;
        }

        // After step down, and if node hasn't retired, node status changes to 'candidate'
        if (oldStatus!=RETIRING)
            setLocalStatus(CANDIDATE);
        else
            setLocalStatus(NOT_CANDIDATE);
    }

    public void backOff() {
        NODE_STATUS state = getLocalStatus();
        if (state==INITIALIZING) {
            log_debug("BRU: Set Back-off flag to step down after initialization");
            backOff.set(true);
        } else
        if (state==AGGREGATOR) {
            log_debug("BRU: Stepping down because Back-off flag has been set");
            aggregatorStepDown();
        }
    }

    public boolean isBackOffSet() {
        return backOff.get();
    }

    public void startElection() {
        log_info("BRU: Broker election requested: broadcasting election message...");
        atomix.getCommunicationService().broadcastIncludeSelf(NODE_MESSAGE_TOPIC, MESSAGE_ELECTION);
    }

    public void election(List<String> excludeNodes) {
        // Find the new Brokering node
        if (excludeNodes == null) excludeNodes = Collections.emptyList();
        final List<String> excludes = excludeNodes;
        Member broker = atomix.getMembershipService().getMembers().stream()
                .filter(m -> m.isActive() && m.isReachable())
                .filter(m -> !excludes.contains(m.id().id()))
                .filter(m -> CANDIDATE_STATUSES.contains(getNodeStatus(m)))
                .map(m -> new MemberWithScore(m, clusterManager.getScoreFunction()))
                .peek(ms -> log_info("BRU: Member-Score: {} => {}  {}", ms.getMember().id().id(), ms.getScore(),
                        ms.getMember().properties().getProperty("uuid", null)))
                .max(MemberWithScore::compareTo)
                .orElse(MemberWithScore.NULL_MEMBER)
                .getMember();
        log_info("BRU: Broker: {}", broker != null ? broker.id().id() : null);

        // If local node is the selected broker...
        if (getLocalMember().equals(broker)) {
            appointment(broker.id().id());
        }
    }

    private void appointment(String appointedNodeId) {
        // Check i am appointed
        Member local = getLocalMember();
        if (! local.id().id().equals(appointedNodeId)) {
            log_debug("BRU: I am not appointed: me={} <> appointed={}", local.id().id(), appointedNodeId);
            return;
        }

        // Check if i am already a broker
        NODE_STATUS localStatus = getLocalStatus();
        if (BROKER_STATUSES.contains(localStatus)) {
            if (localStatus==RETIRING) {
                log_error("BRU: !!!! BUG: RETIRING AGGREGATOR HAS BEEN ELECTED AGAIN !!!!");
            } else {
                log_info("BRU: Aggregator elected again");
            }
        } else {
            // Start initializing as Broker...
            aggregatorInitialize();
        }

        // Notify others that this node is ready to serve as Aggregator
        String brokerId = local.id().id();
        String newConf = MARKER_NEW_CONFIGURATION +
                (callback!=null ? callback.getConfiguration(local) : "");
        atomix.getCommunicationService().broadcastIncludeSelf(NODE_MESSAGE_TOPIC, MESSAGE_READY + " " + brokerId + " " + newConf);
    }

    private void aggregatorInitialize() {
        if (backOff.getAndSet(false)) {
            log_warn("BRU: Node cannot be initialized as Aggregator. Back off flag is set");
            return;
        }

        // Notify others that this node starts initializing as Broker
        log_info("BRU: Node will become Broker. Initializing...");
        atomix.getCommunicationService().broadcast(NODE_MESSAGE_TOPIC, MESSAGE_INITIALIZE + " " + getLocalMember().id().id());
        setLocalStatus(INITIALIZING);

        // Start initializing as Aggregator...
        if (callback!=null)
            callback.initialize();

        // Update node status to Broker
        setLocalStatus(AGGREGATOR);
        log_info("BRU: Node is ready to act as Aggregator. Ready");

        if (backOff.getAndSet(false)) {
            log_debug("initialize(): Back-off flag has been set. Stepping down immediately.");
            aggregatorStepDown();
        }
    }

    public void appoint(String brokerId) {
        // Check if already a broker
        if (getBrokers().stream().anyMatch(m -> m.id().id().equals(brokerId))) {
            log_info("BRU: Node is already a broker: {}", brokerId);
            if (getNodeStatus(brokerId)==RETIRING)
                setNodeStatus(brokerId, AGGREGATOR);
            return;
        }

        // Check if not a candidate
        NODE_STATUS brokerStatus = getNodeStatus(brokerId);
        log_debug("BRU: Node status: {}", brokerStatus);
        if (NON_CANDIDATE_STATUSES.contains(brokerStatus)) {
            log_info("BRU: Node is not a broker candidate: {}", brokerId);
            return;
        }

        // Broadcast appointment message
        atomix.getCommunicationService().broadcastIncludeSelf(NODE_MESSAGE_TOPIC, MESSAGE_APPOINT + " " + brokerId);
        log_info("BRU: Broker appointment broadcast: {}", brokerId);
    }

    public void retire() {
        NODE_STATUS localStatus = getLocalStatus();
        if (BROKER_STATUSES.contains(localStatus)) {
            if (localStatus==RETIRING) {
                log_info("BRU: Already retiring");
            } else {
                setLocalStatus(RETIRING);
                log_info("BRU: Broker retires: broadcasting election message...");
                String localNodeId = getLocalMember().id().id();
                atomix.getCommunicationService().broadcast(NODE_MESSAGE_TOPIC, MESSAGE_ELECTION + " -" + localNodeId);
                //election(Collections.singletonList(localNodeId));
            }
        } else
            log_info("BRU: Not an Aggregator");
    }

    public List<Member> getBrokers() {
        return atomix.getMembershipService().getMembers().stream()
                .filter(m -> m.isActive() && m.isReachable())
                .filter(m -> BROKER_STATUSES.contains(getNodeStatus(m)))
                .collect(Collectors.toList());
    }

    public Member getLocalMember() {
        return atomix.getMembershipService().getLocalMember();
    }

    public NODE_STATUS getLocalStatus() {
        return getNodeStatus(getLocalMember());
    }

    public void setLocalStatus(@NonNull NODE_STATUS status) {
        setNodeStatus(getLocalMember(), status);
    }

    public NODE_STATUS getNodeStatus(@NonNull Member member) {
        return NODE_STATUS.valueOf(member.properties().getProperty(STATUS_PROPERTY, NOT_SET.name()));
    }

    public void setNodeStatus(@NonNull Member member, @NonNull NODE_STATUS status) {
        log_trace("BRU: setNodeStatus: Node properties BEFORE CHANGE: {}", member.properties());
        String oldStatusName = (String) member.properties().setProperty(STATUS_PROPERTY, status.name());
        log_trace("BRU: setNodeStatus: Node properties AFTER CHANGE:  {}", member.properties());
        log_debug("BRU: setNodeStatus: Status changed: {} --> {}", oldStatusName, status);
        NODE_STATUS oldStatus = StringUtils.isNotBlank(oldStatusName) ? NODE_STATUS.valueOf(oldStatusName) : null;
        if (callback!=null & oldStatus!=status)
            callback.statusChanged(oldStatus, status);
    }

    public NODE_STATUS getNodeStatus(@NonNull String memberId) {
        Member member = getMemberById(memberId);
        if (member != null)
            return getNodeStatus(member);
        return null;
    }

    public void setNodeStatus(@NonNull String memberId, @NonNull NODE_STATUS status) {
        Member member = getMemberById(memberId);
        if (member != null)
            setNodeStatus(member, status);
    }

    private Member getMemberById(@NonNull String id) {
        return atomix.getMembershipService().getMembers().stream()
                .filter(m -> m.isActive() && m.isReachable())
                .filter(m -> m.id().id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public void setCandidate() {
        NODE_STATUS localStatus = getLocalStatus();
        if (localStatus==NOT_CANDIDATE || localStatus==NOT_SET) {
            setLocalStatus(CANDIDATE);
            log_info("BRU: Node becomes Aggregator candidate");
        } else
            log_info("BRU: Node is already Aggregator candidate");
    }

    public void clearCandidate() {
        NODE_STATUS localStatus = getLocalStatus();
        if (BROKER_STATUSES.contains(localStatus)) {
            log_warn("BRU: Node is the Aggregator. Select 'retire' first");
            return;
        }
        if (localStatus==INITIALIZING) {
            log_warn("BRU: Node is initializing for Aggregator. Step down first");
            return;
        }
        if (localStatus==STEPPING_DOWN) {
            log_warn("BRU: Node is stepping down. Wait step down complete");
            return;
        }
        if (localStatus==CANDIDATE) {
            setLocalStatus(NOT_CANDIDATE);
            log_info("BRU: Node removed from Broker candidates");
        } else
            log_info("BRU: Node is not Aggregator candidate");
    }

    public List<MemberWithScore> getCandidates() {
        return atomix.getMembershipService().getMembers().stream()
                .filter(m -> m.isActive() && m.isReachable())
                .filter(m -> CANDIDATE_STATUSES.contains(getNodeStatus(m)))
                .map(m -> new MemberWithScore(m, clusterManager.getScoreFunction()))
                .collect(Collectors.toList());
    }

    public List<MemberWithScore> getActiveNodes() {
        return atomix.getMembershipService().getMembers().stream()
                .filter(m -> m.isActive() && m.isReachable())
                .map(m -> new MemberWithScore(m, clusterManager.getScoreFunction()))
                .collect(Collectors.toList());
    }

    public void checkBroker() {
        List<Member> brokers = getBrokers();
        log_info("BRU: Brokers after cluster change: {}", brokers);

        // Check if any node is initializing as broker (then don't start election)
        if (getActiveNodes().stream()
                .map(MemberWithScore::getMember)
                .map(this::getNodeStatus)
                .noneMatch(s -> INITIALIZING==s || AGGREGATOR==s))
        {
            startElection();
        }
    }

    public void checkBrokerNumber() {
        List<Member> brokers = getBrokers();
        log_debug("BRU: Check number of Brokers in cluster: {}", brokers);

        // Check if there are more than one brokers in cluster
        long numOfBrokers = getActiveNodes().stream()
                .map(MemberWithScore::getMember)
                .map(this::getNodeStatus)
                .filter(s -> AGGREGATOR==s)
                .count();
        log_info("BRU: Number of Brokers in cluster: {}", numOfBrokers);
        if (numOfBrokers>1) {
            log_warn("BRU: {} brokers found in the cluster. Starting election...", numOfBrokers);
            startElection();
        }
    }

    public interface NodeCallback {
        void joinedCluster();
        void leftCluster();

        void initialize();
        void stepDown();
        void statusChanged(NODE_STATUS oldStatus, NODE_STATUS newStatus);
        void clusterChanged(ClusterMembershipEvent event);
        String getConfiguration(Member local);
        void setConfiguration(String newConfig);
    }
}
