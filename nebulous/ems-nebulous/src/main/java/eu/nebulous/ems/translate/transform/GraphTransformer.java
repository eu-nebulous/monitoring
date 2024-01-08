/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.transform;

import gr.iccs.imu.ems.translate.Grouping;
import gr.iccs.imu.ems.translate.dag.DAG;
import gr.iccs.imu.ems.translate.dag.DAGNode;
import gr.iccs.imu.ems.translate.model.Metric;
import gr.iccs.imu.ems.translate.model.MetricContext;
import gr.iccs.imu.ems.translate.model.MetricVariable;
import gr.iccs.imu.ems.translate.model.NamedElement;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphTransformer {
    private final NebulousEmsTranslatorProperties properties;

    public void transformGraph(DAG dag) {
        log.debug("GraphTransformer.transformGraph():  Transforming DAG...");
        if (properties.isPruneMvv()) {
            removeMVV(dag, dag.getRootNode());
        } else {
            log.debug("GraphTransformer.transformGraph():  MVV pruning from DAG is disabled");
        }

        if (properties.isAddTopLevelMetrics()) {
            addTopLevelMetrics(dag);
        } else {
            log.debug("GraphTransformer.transformGraph():  Adding Metric for Top-Level Metric Contexts in DAG is disabled");
        }

        log.debug("GraphTransformer.transformGraph():  Transforming DAG... done");
    }

    // Branch pruning when all nodes in branch are MVVs or Metric Variables calculated using exclusively MVVs or other Metric Variables calculated using MVVs
    private void removeMVV(DAG dag, DAGNode node) {
        log.debug("GraphTransformer.removeMVV():  Checking node {}...", node);

        // first process children (i.e. first prune child MVV's)
        Set<DAGNode> children = dag.getNodeChildren(node);
        log.debug("GraphTransformer.removeMVV():  Initial node children: node={}, children: {}", node, children);
        if (children != null) {
            for (DAGNode child : children) {
                removeMVV(dag, child);
            }
        }

        children = dag.getNodeChildren(node);
        log.debug("GraphTransformer.removeMVV():  Node children after pruning: node={}, children: {}", node, children);

        // check if this node is MVV (i.e. has no child MVV's and is Metric Variable)
        if (node.getElement() != null) {
            if (children==null || children.isEmpty()) {
                if (node.getElement() instanceof MetricVariable) {
                    // remove from DAG
                    log.debug("GraphTransformer.removeMVV():  Node is MVV: node={}", node);
                    dag.removeNode(node.getElement());
                    log.debug("GraphTransformer.removeMVV():  MVV node pruned: node={}", node);
                    return;
                }
            }
        }
        // i.e. 'node' is not Metric variable or it has children that are not MVVs
        log.debug("GraphTransformer.removeMVV():  Node is not MVV: node={}", node);
    }

    private void addTopLevelMetrics(DAG dag) {
        log.debug("GraphTransformer.addTopLevelMetrics():  Adding parent metric to all Top-Level metric contexts...");

        dag.getTopLevelNodes().forEach(tln -> {
            NamedElement elem = tln.getElement();
            log.debug("GraphTransformer.addTopLevelMetrics():   - Top-Level node: {}", tln.getName());
            if (elem!=null) {
                log.debug("GraphTransformer.addTopLevelMetrics():   - Top-Level node element: {} ({})", elem.getName(), elem.getClass().getName());
                if (elem instanceof MetricContext mc) {
                    Metric m = mc.getMetric();
                    log.debug("GraphTransformer.addTopLevelMetrics():   - Top-Level node element is Metric Context: context={}, metric={}", mc.getName(), m.getName());

                    // add Metric as metric context's parent
                    dag.addTopLevelNode(m).setGrouping(Grouping.GLOBAL);
                    dag.addNode(m, mc);
                    dag.removeEdge(dag.getRootNode(), tln);
                    log.debug("GraphTransformer.addTopLevelMetrics():   - New Top-Level node added for metric {}, instead of metric context {}", m.getName(), mc.getName());
                } else {
                    log.debug("GraphTransformer.addTopLevelMetrics():   - Ignoring element: {}", elem.getName());
                }
            } else {
                log.debug("GraphTransformer.addTopLevelMetrics():   - Top-Level node with 'null' element encountered: {}", tln.getName());
            }
        });

        log.debug("GraphTransformer.addTopLevelMetrics():  Adding parent metric to all Top-Level metric contexts...ok");
    }
}
