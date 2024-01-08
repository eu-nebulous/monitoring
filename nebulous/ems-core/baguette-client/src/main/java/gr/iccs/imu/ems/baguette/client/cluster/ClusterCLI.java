/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.cluster;

import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class ClusterCLI extends AbstractLogBase {

	private final ClusterManager clusterManager;

	@Getter @Setter
	private String prompt = " -> ";
	@Getter @Setter
	private boolean promptUpdate;

	public void updatePrompt() {
		if (promptUpdate) {
			setPrompt((clusterManager != null && clusterManager.isRunning())
					? "[" + clusterManager.getLocalMember().id().id() + "] => "
					: " => ");
		}
	}

	public void run() {
		run(false, false, false, true);
	}

	public void run(boolean joinOnStart, boolean leaveOnExit, boolean autoElect, boolean allowExit) {
		if (joinOnStart && !clusterManager.isInitialized()) {
			clusterManager.initialize();
		}
		if (joinOnStart && !clusterManager.isRunning()) {
			clusterManager.joinCluster(autoElect);
		}
		updatePrompt();

		// Start doing work...
		while (true) {
			try {
				String line = readLine(prompt);
				if (StringUtils.isBlank(line)) continue;
				String[] cmd = line.trim().split(" ");

				if ("exit".equalsIgnoreCase(cmd[0])) {
					if (allowExit)
						break;
				} else {
					executeCommand(line, cmd);
				}

			} catch (Exception ex) {
				log_error("CLI: Exception caught: ", ex);
			}
		}

		if (leaveOnExit && clusterManager.isRunning())
			clusterManager.leaveCluster();
	}

	public void executeCommand(String line, String[] cmd) {
		if ("properties".equalsIgnoreCase(cmd[0])) {
			Properties properties = clusterManager.getLocalMember().properties();
			log_info("CLI: Local member properties:");
			for (String propName : properties.stringPropertyNames()) {
				log_info("CLI:    {} = {}", propName, properties.getProperty(propName));
			}
		} else if ("set".equalsIgnoreCase(cmd[0])) {
			String setStr = line.trim().split(" ", 2)[1];
			int p = setStr.indexOf("=");
			String propName = setStr.substring(0, p).trim();
			String propValue = setStr.substring(p + 1).trim();
			log_info("CLI: SET PROPERTY: {} = {}", propName, propValue);
			clusterManager.getLocalMember().properties().setProperty(propName, propValue);
		} else if ("unset".equalsIgnoreCase(cmd[0])) {
			String propName = cmd[1].trim();
			log_info("CLI: UNSET PROPERTY: {}", propName);
			clusterManager.getLocalMember().properties().setProperty(propName, "");
		} else if ("score".equalsIgnoreCase(cmd[0])) {
			if (cmd.length==1) {
				log_info("CLI: Score function: {}", clusterManager.getScoreFunction());
			} else {
				String formula = clusterManager.getScoreFunction().getFormula();
				Properties defs = new Properties();
				defs.putAll(clusterManager.getScoreFunction().getArgumentDefaults());
				double defScore = clusterManager.getScoreFunction().getDefaultScore();
				boolean throwExceptions = clusterManager.getScoreFunction().isThrowExceptions();
				if (!"-".equals(cmd[1]) && !"same".equalsIgnoreCase(cmd[1]))
					formula = cmd[1];
				for (int i = 2; i < cmd.length; i++) {
					String[] part = cmd[i].split("=", 2);
					if ("default".equalsIgnoreCase(part[0])) {
						throwExceptions = false;
						if ("-".equals(part[1]))
							throwExceptions = true;
						else
							defScore = Double.parseDouble(part[1]);
					} else if ("clear-defaults".equalsIgnoreCase(part[0]))
						defs.clear();
					else
						defs.setProperty(part[0], String.valueOf(Double.parseDouble(part[1])));
				}
				clusterManager.setScoreFunction(MemberScoreFunction.builder()
						.formula(formula)
						.argumentDefaults(defs)
						.defaultScore(defScore)
						.throwExceptions(throwExceptions)
						.build());
			}

		} else if ("members".equalsIgnoreCase(cmd[0])) {
			// Get cluster members
			log_info("CLI: Cluster members:");
			for (Member member : clusterManager.getMembers()) {
				String memId = member.id().id();
				String memAddress = member.config().getAddress().toString();
				Set<Map.Entry<Object, Object>> memProperties = member.properties().entrySet();
				String active = (member.isActive() ? "active" : "inactive");
				String reachable = (member.isReachable() ? "reachable" : "unreachable");
				log_info("CLI:    {}/{}/{}-{}/{}", memId, memAddress, active, reachable, memProperties);
			}
		} else if ("join".equalsIgnoreCase(cmd[0])) {
			if (cmd.length>1) {
				ArrayList<String> tmp = new ArrayList<>(Arrays.asList(cmd));
				tmp.remove(0);
				clusterManager.getProperties().setMemberAddresses(tmp);
			}

			// Join/start cluster
			clusterManager.initialize();
			clusterManager.joinCluster();
			updatePrompt();

		} else if ("leave".equalsIgnoreCase(cmd[0])) {
			clusterManager.leaveCluster();
			updatePrompt();

		} else if ("message".equalsIgnoreCase(cmd[0])) {
			ClusterCommunicationService communicationService = clusterManager.getAtomix().getCommunicationService();
			String op = cmd[1];
			String topic = cmd[2];
			if ("subscribe".equalsIgnoreCase(op)) {
				communicationService.subscribe(topic, (m) -> {
					log_info("CLI: **** Message: {} on Topic: {}", m, topic);
					return CompletableFuture.completedFuture("Ok");
				}).join();
				log_info("CLI: Subscribed to topic: {}", topic);
			} else
			if ("unsubscribe".equalsIgnoreCase(op)) {
				log_info("CLI: Unsubscribe from topic: {}", topic);
				communicationService.unsubscribe(topic);
			} else
			if ("broadcast".equalsIgnoreCase(op)) {
				log_info("CLI: Broadcast to topic: {}", topic);
				String message = String.join(" ", Arrays.copyOfRange(cmd, 3, cmd.length));
				communicationService.broadcast(topic, message);
			} else
			if ("send".equalsIgnoreCase(op)) {
				MemberId mId = MemberId.from(cmd[3]);
				log_info("CLI: Send to node: {}, on topic: {}", cmd[3], topic);
				String message = String.join(" ", Arrays.copyOfRange(cmd, 4, cmd.length));
				communicationService.send(topic, message, mId).join();
			} else
			if ("unicast".equalsIgnoreCase(op)) {
				MemberId mId = MemberId.from(cmd[3]);
				log_info("CLI: Send to node: {}, on topic: {}", cmd[3], topic);
				String message = String.join(" ", Arrays.copyOfRange(cmd, 3, cmd.length));
				communicationService.unicast(topic, message, mId).join();
			} else
				log_warn("CLI:    Invalid Message operation: {}", op);

		} else if ("broker".equalsIgnoreCase(cmd[0]) || "bl".equalsIgnoreCase(cmd[0])) {
			String op = cmd.length>1 ? cmd[1] : null;
			if ("list".equalsIgnoreCase(op) || "bl".equalsIgnoreCase(cmd[0])) {
				log_info("CLI: Node status and scores:");
				final BrokerUtil brokerUtil1 = clusterManager.getBrokerUtil();
				brokerUtil1.getActiveNodes().forEach(ms -> log_info("CLI:    {}  [{}, {}, {}]",
						ms.getMember().id().id(), brokerUtil1.getNodeStatus(ms.getMember()),
						ms.getScore(), ms.getMember().properties().getProperty("uuid", null)));
			} else
			if ("candidates".equalsIgnoreCase(op)) {
				log_info("CLI: Broker candidates:");
				final BrokerUtil brokerUtil1 = clusterManager.getBrokerUtil();
				brokerUtil1.getCandidates().forEach(ms -> log_info("CLI:    {}  [{}, {}, {}]",
						ms.getMember().id().id(), brokerUtil1.getNodeStatus(ms.getMember()),
						ms.getScore(), ms.getMember().properties().getProperty("uuid", null)));
			} else
			if ("status".equalsIgnoreCase(op)) {
				clusterManager.getBrokerUtil().getBrokers()
						.forEach(m -> log_info("CLI: Current Broker: {}", m.id().id()));
			} else
			if ("elect".equalsIgnoreCase(op)) {
				clusterManager.getBrokerUtil().startElection();
			} else
			if ("retire".equalsIgnoreCase(op)) {
				clusterManager.getBrokerUtil().retire();
			} else
			if ("appoint".equalsIgnoreCase(op)) {
				clusterManager.getBrokerUtil().appoint(cmd[2]);
			} else
			if ("on".equalsIgnoreCase(op)) {
				clusterManager.getBrokerUtil().setCandidate();
			} else
			if ("off".equalsIgnoreCase(op)) {
				clusterManager.getBrokerUtil().clearCandidate();
			} else
				log_warn("CLI:    Invalid Broker operation: {}", op);

		} else
			log_warn("CLI: Unknown command: {}", cmd[0]);
	}
}
