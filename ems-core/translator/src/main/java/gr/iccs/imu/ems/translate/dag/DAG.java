/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.dag;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gr.iccs.imu.ems.translate.model.NamedElement;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizV8Engine;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class DAG {
    // Graph-related fields
    @JsonIgnore
    private transient Function<NamedElement, String> fullNameProvider = NamedElement::getName;
    private DirectedAcyclicGraph<DAGNode, DAGEdge> _graph;
    @JsonIgnore
    private transient DAGNode _root;
    @JsonIgnore
    private transient Map<NamedElement, DAGNode> _namedElementToNodesMapping;
    @JsonIgnore
    private transient Map<String, DAGNode> _nameToNodesMapping;


    public DAG() {
        // let everything 'null'
    }

    public DAG(Function<NamedElement,String> fullNameProvider) {
        this.fullNameProvider = fullNameProvider;
        _graph = new DirectedAcyclicGraph<>(DAGEdge.class);
        _root = new DAGNode();
        _graph.addVertex(_root);
        _namedElementToNodesMapping = new HashMap<>();
        _nameToNodesMapping = new HashMap<>();
    }

    public DAGNode getRootNode() {
        return _root;
    }

    public Set<DAGNode> getTopLevelNodes() {
        log.debug("DAG.getTopLevelNodes()");
        if (_graph==null || _root==null) {
            log.debug("DAG.getTopLevelNodes(): _graph or _root is null. Returning empty set");
            return Collections.emptySet();
        }
        Set<DAGNode> children = _graph.outgoingEdgesOf(_root).stream()
                .map(DAGEdge::getTarget)
                .collect(Collectors.toSet());
        log.debug("DAG.getTopLevelNodes(): top-level-nodes={}", children);
        return children;
    }

    public boolean isTopLevelNode(DAGNode node) {
        Set<DAGNode> parents = getParentNodes(node);
        for (DAGNode parent : parents) {
            if (parent == _root) return true;
        }
        return false;
    }

    public Set<DAGNode> getLeafNodes() {
        Iterator<DAGNode> it = _graph.iterator();
        Set<DAGNode> leafs = new HashSet<>();
        it.forEachRemaining(node -> {
            if (node != _root && _graph.outgoingEdgesOf(node).isEmpty()) {
                leafs.add(node);
            }
        });
        return leafs;
    }

    public Set<DAGNode> getParentNodes(DAGNode node) {
        Set<DAGEdge> edges = _graph.incomingEdgesOf(node);
        return edges.stream().map(DAGEdge::getSource).collect(Collectors.toSet());
    }

    public Set<DAGNode> getNodeChildren(DAGNode node) {
        try {
            //log.debug("DAG.getNodeChildren(): node={}", node);
            Set<DAGNode> children = _graph.outgoingEdgesOf(node).stream().map(DAGEdge::getTarget).collect(java.util.stream.Collectors.toSet());
            //log.debug("DAG.getNodeChildren(): parent={}, children={}", node, children);
            return children;
        } catch (IllegalArgumentException iae) {
            log.warn("DAG.getNodeChildren(): Node not in DAG: node={}", node);
            return null;
        }
    }

    // ====================================================================================================================================================
    // Add node methods

    public DAGNode addTopLevelNode(NamedElement elem) {
        return addTopLevelNode(elem, null);
    }

    public DAGNode addTopLevelNode(NamedElement elem, String effectiveFullName) {
        if (elem == null) throw new IllegalArgumentException("DAG.addTopLevelNode(): Argument #1 cannot be null");

        log.debug("DAG.addTopLevelNode(): top-level-element={}, effective-full-name={}", elem.getName(), effectiveFullName);
        DAGNode node = _namedElementToNodesMapping.get(elem);
        log.debug("DAG.addTopLevelNode(): cached-node={}", node);
        if (node != null && effectiveFullName != null && !effectiveFullName.trim().isEmpty() && !node.getName().equals(effectiveFullName)) {
            log.debug("DAG.addTopLevelNode(): Cached-node has different full-name than effective-full-name. A new node will be created: {} != {}", node.getName(), effectiveFullName);
            node = null;
        }
        boolean newNode = false;
        if (node == null) {
            String fullName = (effectiveFullName == null || effectiveFullName.trim().isEmpty()) ? fullNameProvider.apply(elem) : effectiveFullName.trim();

            if (!_nameToNodesMapping.containsKey(fullName)) {

                node = new DAGNode(elem, fullName);
                newNode = _graph.addVertex(node);
                if (newNode) log.debug("DAG.addTopLevelNode(): Element added in DAG: {}", node.getName());
                else log.debug("DAG.addTopLevelNode(): Element already in DAG and replaced: {}", node.getName());

                _namedElementToNodesMapping.put(elem, node);
                if (_nameToNodesMapping.put(node.getName(), node) != null) {
                    log.warn("DAG.addTopLevelNode(): _nameToNodesMapping: {}", _nameToNodesMapping);
                    throw new RuntimeException("Element name already exists in DAG: " + node.getName());
                }

            } else {
                node = _nameToNodesMapping.get(fullName);
                newNode = _graph.addVertex(node);
                if (newNode) log.debug("DAG.addTopLevelNode()-2: Element added in DAG: {}", node.getName());
                else log.debug("DAG.addTopLevelNode()-2: Element already in DAG and replaced: {}", node.getName());

                _namedElementToNodesMapping.put(elem, node);
            }
        } else {
            log.debug("DAG.addTopLevelNode(): Element already in DAG: {}", node.getName());
        }

        DAGEdge edge = new DAGEdge();
        boolean newEdge = _graph.addEdge(_root, node, edge);
        if (newNode) log.debug("DAG.addTopLevelNode(): Element set as Top-Level in DAG: {}", node.getName());
        else log.debug("DAG.addTopLevelNode(): Element is already set as Top-Level in DAG: {}", node.getName());

        return node;
    }

    public DAGNode addNode(NamedElement parent, NamedElement elem) {
        if (parent == null) throw new IllegalArgumentException("DAG.addNode(): Argument #1 'parent' cannot be null");
        if (elem == null) throw new IllegalArgumentException("DAG.addNode(): Argument #2 'elem' cannot be null");

        log.debug("DAG.addNode(): parent={}, element={}", parent.getName(), elem.getName());
        DAGNode node = _namedElementToNodesMapping.get(elem);
        log.debug("DAG.addNode(): cached-node={}", node);
        boolean newNode = false;
        if (node == null) {
            String fullName = fullNameProvider.apply(elem);

            if (!_nameToNodesMapping.containsKey(fullName)) {

                node = new DAGNode(elem, fullName);
                newNode = _graph.addVertex(node);
                if (newNode) log.debug("DAG.addNode(): Element added in DAG: {}", node.getName());
                else log.debug("DAG.addNode(): Element already in DAG and replaced: {}", node.getName());

                _namedElementToNodesMapping.put(elem, node);
                if (_nameToNodesMapping.put(node.getName(), node) != null) {
                    log.warn("DAG.addNode(): _nameToNodesMapping: {}", _nameToNodesMapping);
                    throw new RuntimeException("Element name already exists in DAG: " + node.getName());
                }

            } else {
                node = _nameToNodesMapping.get(fullName);
                newNode = _graph.addVertex(node);
                if (newNode) log.debug("DAG.addNode()-2: Element added in DAG: {}", node.getName());
                else log.debug("DAG.addNode()-2: Element already in DAG and replaced: {}", node.getName());

                _namedElementToNodesMapping.put(elem, node);
            }
        } else {
            log.debug("DAG.addNode(): Element already in DAG: {}", node.getName());
        }

        DAGNode parentNode = _namedElementToNodesMapping.get(parent);
        DAGEdge edge = new DAGEdge();
        boolean newEdge = _graph.addEdge(parentNode, node, edge);
        if (newNode) log.debug("DAG.addNode(): Edge added in DAG: {} --> {} ", parent.getName(), node.getName());
        else log.debug("DAG.addNode(): Edge is already in DAG: {} --> {}", parent.getName(), node.getName());

        return node;
    }

    // ====================================================================================================================================================
    // Remove node method

    public DAGNode removeNode(NamedElement elem) {
        if (elem == null) throw new IllegalArgumentException("DAG.removeNode(): Argument cannot be null");

        // check if children nodes exist
        DAGNode node = _namedElementToNodesMapping.get(elem);
        if (node == null) {
            log.warn("DAG.removeNode(): Element not found (_namedElementToNodesMapping): {}", elem.getName());
            return null;
        }
        Set<DAGEdge> edges = _graph.outgoingEdgesOf(node);
        if (edges != null && edges.size() > 0)
            throw new RuntimeException("Element being removed has children: " + node.getName());

        // remove node from DAG
        _graph.removeVertex(node);        // This also removes edges touching this node
        _namedElementToNodesMapping.remove(elem);
        log.debug("DAG.removeNode(): Element removed from DAG: {}", node.getName());

        return node;
    }

    // ====================================================================================================================================================
    // Add/Remove edge methods

    public DAGEdge addEdge(NamedElement elemFrom, NamedElement elemTo) {
        if (elemFrom == null)
            throw new IllegalArgumentException("DAG.addEdge(): Argument #1 'elemFrom' cannot be null");
        if (elemTo == null) throw new IllegalArgumentException("DAG.addEdge(): Argument #2 'elemTo' cannot be null");

        Iterator<DAGNode> it = _graph.iterator();
        DAGNode nodeFrom = null;
        DAGNode nodeTo = null;
        while (it.hasNext() && (nodeFrom == null || nodeTo == null)) {
            DAGNode node = it.next();
            if (node.getElement() == elemFrom) nodeFrom = node;
            if (node.getElement() == elemTo) nodeTo = node;
        }
        if (nodeFrom != null && nodeTo != null) {
            DAGEdge edge = new DAGEdge();
            boolean newEdge = _graph.addEdge(nodeFrom, nodeTo, edge);
            if (newEdge) log.debug("DAG.addEdge(): Edge added in DAG: {} --> {} ", elemFrom.getName(), elemTo.getName());
            else log.debug("DAG.addEdge(): Edge is already in DAG: {} --> {}", elemFrom.getName(), elemTo.getName());
            return edge;
        } else {
            throw new RuntimeException(String.format("Adding edge FAILED: elem-from=%s -> elem-to=%s. Node not found in DAG: node-from=%s --> node-to=%s",
                    elemFrom.getName(), elemTo.getName(), (nodeFrom != null ? nodeFrom.getName() : null), (nodeTo != null ? nodeTo.getName() : null)));
        }
    }

    public DAGEdge addEdge(String elemFrom, String elemTo) {
        if (elemFrom == null)
            throw new IllegalArgumentException("DAG.addEdge(): Argument #1 'elemFrom' cannot be null");
        if (elemTo == null) throw new IllegalArgumentException("DAG.addEdge(): Argument #2 'elemTo' cannot be null");
        log.debug("DAG.addEdge(): Adding edge in DAG: {} --> {} ", elemFrom, elemTo);

        Iterator<DAGNode> it = _graph.iterator();
        DAGNode nodeFrom = null;
        DAGNode nodeTo = null;
        while (it.hasNext() && (nodeFrom == null || nodeTo == null)) {
            DAGNode node = it.next();
            if (elemFrom.equals(node.getName())) nodeFrom = node;
            if (elemTo.equals(node.getName())) nodeTo = node;
        }
        if (nodeFrom != null && nodeTo != null) {
            DAGEdge edge = new DAGEdge();
            boolean newEdge = _graph.addEdge(nodeFrom, nodeTo, edge);
            if (newEdge) log.debug("DAG.addEdge(): Edge added in DAG: {} --> {} ", elemFrom, elemTo);
            else log.debug("DAG.addEdge(): Edge is already in DAG: {} --> {}", elemFrom, elemTo);
            return edge;
        } else {
            throw new RuntimeException(String.format("Adding edge FAILED: elem-from=%s -> elem-to=%s. Node not found in DAG: node-from=%s --> node-to=%s",
                    elemFrom, elemTo, (nodeFrom != null ? nodeFrom.getName() : null), (nodeTo != null ? nodeTo.getName() : null)));
        }
    }

    public DAGEdge removeEdge(NamedElement elemFrom, NamedElement elemTo) {
        if (elemFrom == null)
            throw new IllegalArgumentException("DAG.removeEdge(): Argument #1 'elemFrom' cannot be null");
        if (elemTo == null) throw new IllegalArgumentException("DAG.removeEdge(): Argument #2 'elemTo' cannot be null");

        Iterator<DAGNode> it = _graph.iterator();
        DAGNode nodeFrom = null;
        DAGNode nodeTo = null;
        while (it.hasNext() && (nodeFrom == null || nodeTo == null)) {
            DAGNode node = it.next();
            if (node.getElement() == elemFrom) nodeFrom = node;
            if (node.getElement() == elemTo) nodeTo = node;
        }
        if (nodeFrom != null && nodeTo != null) {
            DAGEdge deletedEdge = _graph.removeEdge(nodeFrom, nodeTo);
            if (deletedEdge != null)
                log.debug("DAG.removeEdge(): Edge removed from DAG: {} --> {} ", elemFrom.getName(), elemTo.getName());
            else log.warn("DAG.removeEdge(): Edge not found in DAG: {} --> {}", elemFrom.getName(), elemTo.getName());
            return deletedEdge;
        } else {
            throw new RuntimeException(String.format("Removing edge FAILED: elem-from=%s -> elem-to=%s. Node not found in DAG: node-from=%s --> node-to=%s",
                    elemFrom.getName(), elemTo.getName(), (nodeFrom != null ? nodeFrom.getName() : null), (nodeTo != null ? nodeTo.getName() : null)));
        }
    }

    public DAGEdge removeEdge(DAGNode nodeFrom, DAGNode nodeTo) {
        if (nodeFrom == null)
            throw new IllegalArgumentException("DAG.removeEdge(): Argument #1 'nodeFrom' cannot be null");
        if (nodeTo == null) throw new IllegalArgumentException("DAG.removeEdge(): Argument #2 'nodeTo' cannot be null");

        DAGEdge deletedEdge = _graph.removeEdge(nodeFrom, nodeTo);
        if (deletedEdge != null)
            log.debug("DAG.removeEdge(): Edge removed from DAG: {} --> {} ", nodeFrom.getElementName(), nodeTo.getElementName());
        else
            log.warn("DAG.removeEdge(): Edge not found in DAG: {} --> {}", nodeFrom.getElementName(), nodeTo.getElementName());
        return deletedEdge;
    }

    // ====================================================================================================================================================
    // Traverse, query and modify graph methods

    public void traverseDAG(java.util.function.Consumer<? super DAGNode> action) {
        log.debug("DAG.traverseDAG(): Traversing graph: Begin");
        _graph.iterator().forEachRemaining(action);
        log.debug("DAG.traverseDAG(): Traversing graph: End");
    }

    public Set<DAGNode> getAllDAGNodes() {
        return _graph.vertexSet();
    }

    public Set<DAGEdge> getAllDAGEdges() {
        return _graph.edgeSet();
    }

    public void clearDAG() {
        _graph.removeAllEdges(_graph.edgeSet());
        _graph.removeAllVertices(_graph.vertexSet());
    }

    public void addDAGNode(DAGNode node) {
        _graph.addVertex(node);
    }

    public void addDAGEdge(DAGNode src, DAGNode trg) {
        _graph.addEdge(src, trg);
    }

    // ====================================================================================================================================================
    // Export methods

    public String exportToDot() {
        if (_graph==null) {
            log.warn("DAG.exportToDot(): Cannot export: DAG has not been initialized");
            return null;
        }

        DOTExporter<DAGNode, DAGEdge> exporter = new DOTExporter<>(node -> "NODE_" + node.getId());
        exporter.setVertexAttributeProvider(node -> {
            LinkedHashMap<String, Attribute> vertexAttributes = new LinkedHashMap<>();
            String label;
            if (node.getName() != null) {
                if (node.getGrouping() != null) {
                    label = String.format("%s\n[%s]", node.getName(), node.getGrouping());
                } else {
                    label = node.getName();
                }
            } else {
                label = "<ROOT>";
            }
            // See: https://graphviz.org/doc/info/attrs.html
            vertexAttributes.put("label", new DefaultAttribute<>(label, AttributeType.STRING));
            /*
            vertexAttributes.put("color", new DefaultAttribute<>("red", AttributeType.STRING));
            vertexAttributes.put("fontcolor", new DefaultAttribute<>("yellow", AttributeType.STRING));
            vertexAttributes.put("fillcolor", new DefaultAttribute<>("cyan:green;0.3", AttributeType.STRING));
            vertexAttributes.put("style", new DefaultAttribute<>("radial", AttributeType.STRING));
            vertexAttributes.put("gradientangle", new DefaultAttribute<>(60, AttributeType.INT));
            */
            return vertexAttributes;
        });
        Writer writer = new StringWriter();
        exporter.exportGraph(_graph, writer);
        return writer.toString();
    }

    public List<String> exportDAG(String baseFileName, String[] exportFormats, int imageWidth) {
        try {
            if (!checkExportConfiguration(baseFileName, exportFormats, imageWidth)) return null;

            // Export DAG in DOT format (can be viewd with GraphViz tool)
            String dot = exportToDot();
            log.debug("DAG.exportDAG(): Results of exportToDot(): Graph in DOT format:\n{}", dot);
            if (dot==null) {
                log.warn("DAG.exportDAG(): Cannot export: DAG has not been initialized");
                return null;
            }

            // Export DOT into specified formats and save to file(s)
            return exportDAG(dot, baseFileName, exportFormats, imageWidth);

        } catch (Exception ex) {
            log.error("DAG.exportDAG(): Graph export FAILED: ", ex);
            return null;
        }
    }

    public List<String> exportDAG(@NonNull String dot, String baseFileName, String[] exportFormats, int imageWidth) {
        try {
            if (!checkExportConfiguration(baseFileName, exportFormats, imageWidth)) return null;

            // Configure Graphviz rendering engine to V8. It's faster
            // See also: https://github.com/nidi3/graphviz-java
            Graphviz.useEngine(new GraphvizV8Engine());

            // Export DOT into specified formats and save to file(s)
            List<String> exportFilesList = new LinkedList<>();
            MutableGraph mg = new Parser().read(dot);
            for (String f : exportFormats) {
                Format fmt = Format.valueOf(f.toUpperCase());
                String exportFile = baseFileName + "." + f;
                Graphviz.fromGraph(mg).width(imageWidth).render(fmt).toFile(new File(exportFile));
                exportFilesList.add(exportFile);
                log.info("DAG.exportDAG(): Graph exported in {} format: {}", fmt, exportFile);
            }
            return exportFilesList;

        } catch (Exception ex) {
            log.error("DAG.exportDAG(): Graph export FAILED: ", ex);
            return null;
        }
    }

    protected boolean checkExportConfiguration(String baseFileName, String[] exportFormats, int imageWidth) {
        // check export configuration
        if (exportFormats == null || exportFormats.length == 0) {
            log.warn("DAG.checkExportConfiguration(): No export formats specified for Graph export: {}", Arrays.toString(exportFormats));
            return false;
        }
        if (imageWidth < 1) {
            log.warn("DAG.checkExportConfiguration(): Invalid image width for Graph export: {}", imageWidth);
            return false;
        }
        return true;
    }

    public String toString() {
        return _graph!=null ? _graph.toString() : null;
    }
}
