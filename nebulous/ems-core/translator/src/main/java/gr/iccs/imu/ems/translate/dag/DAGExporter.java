/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate.dag;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizV8Engine;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Service
public class DAGExporter {
    public String exportToDot(DAG dag) {
        if (dag._graph==null) {
            log.warn("DAG.exportToDot(): Cannot export: DAG has not been initialized");
            return null;
        }

        // Create a DOT exporter
        DOTExporter<DAGNode, DAGEdge> exporter = new DOTExporter<>(node -> "NODE_" + node.getId());

        // Format the graph
        /*exporter.setGraphAttributeProvider(() -> {
            LinkedHashMap<String, Attribute> graphAttributes = new LinkedHashMap<>();
            // See: https://graphviz.org/docs/layouts/
            graphAttributes.put("layout", new DefaultAttribute<>("circo", AttributeType.STRING));
            graphAttributes.put("beautify", new DefaultAttribute<>("true", AttributeType.BOOLEAN));
            return graphAttributes;
        });*/

        // Format vertices (nodes)
        // See (colors): https://www.pastelcolorpalettes.com/7-color-pastels-rainbow
        List<String> ll = Arrays.asList("#F6CA94", "#FAFABE", "#C1EBC0", "#C7CAFF", "#CDABEB", "#F6C2F3", "#F09EA7");
        //Collections.reverse(ll);
        String[] colorsArr = ll.toArray(new String[0]);
        exporter.setVertexAttributeProvider(node -> {
            LinkedHashMap<String, Attribute> vertexAttributes = new LinkedHashMap<>();

            // Prepare and format labels
            // See: https://graphviz.org/doc/info/shapes.html#html
            String label;
            String col;
            boolean isRoot = false, isSensor = false;
            boolean isBusyStatus = false, isMetricVar = false;
            if (node.getName() != null) {
                if (node.getGrouping() != null) {
                    //label = String.format("%s\n[%s]", node.getName(), node.getGrouping());
                    String[] namePart = node.getName().split("\\.",2);
                    String name = (namePart.length>1)
                            ? "<U>"+namePart[0].trim()+"</U> .."+namePart[1].trim() : "<U>"+namePart[0].trim()+"</U>";
                    String type = node.getElement()!=null ? node.getElement().getClass().getSimpleName() : "-";
                    String topic = StringUtils.defaultIfBlank(node.getTopicName(), "");
                    label = String.format("""
                            <B>%s</B>
                            <BR/>
                            <FONT POINT-SIZE="12">&laquo; %s &raquo;</FONT>
                            <BR/>
                            <I><FONT COLOR="grey">%s</FONT></I>
                            <BR/>
                            <B><FONT COLOR="red">[%s]</FONT></B>
                            """,
                            name, type, topic, node.getGrouping());
                    col = colorsArr[(node.getGrouping().getOrder() % (colorsArr.length-1))];

                    isSensor = node.getElement()!=null &&
                            StringUtils.containsIgnoreCase(node.getElement().getClass().getSimpleName(), "Sensor");
                    isBusyStatus = node.getElement()!=null &&
                            "BusyStatusMetricVariable".equalsIgnoreCase(node.getElement().getClass().getSimpleName());
                    isMetricVar = node.getElement()!=null &&
                            "MetricVariable".equalsIgnoreCase(node.getElement().getClass().getSimpleName());
                } else {
                    label = node.getName();
                    col = "#ffffff";
                }
            } else {
                label = StringEscapeUtils.escapeHtml4("<ROOT>");
                label = "<B>"+label+"</B>";
                col = colorsArr[colorsArr.length-1];
                isRoot = true;
            }
            // See: https://graphviz.org/doc/info/attrs.html
            vertexAttributes.put("label", new DefaultAttribute<>(label, AttributeType.HTML));

            // See: https://graphviz.org/doc/info/shapes.html#polygon
            vertexAttributes.put("shape", new DefaultAttribute<>(
                    isRoot || isSensor ? "oval" : "box", AttributeType.STRING));

            vertexAttributes.put("fillcolor", new DefaultAttribute<>(col+":white;0.3", AttributeType.STRING));
            if (isSensor)
                vertexAttributes.put("fillcolor", new DefaultAttribute<>("white", AttributeType.STRING));
            if (isBusyStatus)
                vertexAttributes.put("fillcolor", new DefaultAttribute<>("yellow:white;0.3", AttributeType.STRING));
            if (isMetricVar)
                vertexAttributes.put("fillcolor", new DefaultAttribute<>("orange:white;0.3", AttributeType.STRING));
            vertexAttributes.put("style", new DefaultAttribute<>("radial, rounded", AttributeType.STRING));
            //vertexAttributes.put("style", new DefaultAttribute<>("filled", AttributeType.STRING));
            vertexAttributes.put("gradientangle", new DefaultAttribute<>(60, AttributeType.INT));

            /*if (isSensor) {
                if (((Sensor) node.getElement()).getAdditionalProperties()!=null) {
                    String sensorType = ((Sensor) node.getElement()).getAdditionalProperties()
                            .getOrDefault("type", "").trim().toLowerCase();
                    String image = switch (sensorType) {
                        case "netdata" -> "netdata-48x48.png";
                        default -> null;
                    };
                    if (StringUtils.isNotBlank(image)) {
                        vertexAttributes.put("image", new DefaultAttribute<>(image, AttributeType.STRING));
                        vertexAttributes.put("imagepos", new DefaultAttribute<>("ml", AttributeType.STRING));
                    }
                }
            }*/
            return vertexAttributes;
        });

        // Format edges
        exporter.setEdgeAttributeProvider(edge -> {
            LinkedHashMap<String, Attribute> edgeAttributes = new LinkedHashMap<>();
//            edgeAttributes.put("dir", new DefaultAttribute<>("back", AttributeType.STRING));
            edgeAttributes.put("arrowtail", new DefaultAttribute<>("vee", AttributeType.STRING));
            return edgeAttributes;
        });

        // Export graph to DOT string
        Writer writer = new StringWriter();
        exporter.exportGraph(dag._graph, writer);
        return writer.toString();
    }

    public List<String> exportDAG(DAG dag, String baseFileName, String[] exportFormats, int imageWidth) {
        try {
            if (!checkExportConfiguration(baseFileName, exportFormats, imageWidth)) return null;

            // Export DAG in DOT format (can be viewed with GraphViz tool)
            String dot = exportToDot(dag);
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
            Graphviz.useDefaultEngines();
            //Graphviz.useEngine(new GraphvizV8Engine());

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
}
