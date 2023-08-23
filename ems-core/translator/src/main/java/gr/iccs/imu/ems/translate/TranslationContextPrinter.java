/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.translate;

import gr.iccs.imu.ems.translate.model.NamedElement;
import gr.iccs.imu.ems.util.FunctionDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationContextPrinter {
	private final TranslationContextPrinterProperties properties;
	
	public void printResults(TranslationContext _TC, String exportName) {
		if (! properties.isPrintResults()) {
			log.debug("TranslationContextPrinter.printResults(): Translation results printing is disabled");
			return;
		}

		// Print analysis results
		log.info("*********************************************************");
		log.info("****      T R A N S L A T I O N   R E S U L T S      ****");
		log.info("*********************************************************");
		log.info("Model Name: {}", _TC.getModelName());

		// Print DAG
		String dot = null;
		if (properties.getDag().isExportToDotEnabled()) {
			log.info("Decomposition Graph:\n{}", _TC.getDAG());
			log.info("*********************************************************");
			try {
				if (_TC.getDAG().getRootNode()!=null) {
					dot = _TC.getDAG().exportToDot();
					log.info("Decomposition Graph in DOT format:\n{}", dot);
				} else {
					log.warn("Decomposition Graph is empty.");
					log.warn("Translation Context loaded from cache?");
				}
			} catch (Exception ex) {
				log.error("Decomposition Graph in DOT format: EXCEPTION: ", ex);
			}
		}
		// Export DAG to files
		if (properties.getDag().isExportToFileEnabled()) {
			log.info("*********************************************************");
			log.info("Decomposition Graph export to file(s)");
			try {
				// Get graph export configuration
				String exportPath = properties.getDag().getExportPath();
				String[] exportFormats = properties.getDag().getExportFormats();
				int imageWidth = properties.getDag().getExportImageWidth();

				// Get base name and path of export files
				if (exportPath == null) exportPath = "";
				exportName = StringUtils.stripToEmpty(exportName);
				if (exportName.isEmpty()) exportName = "noname";
				String baseFileName = String.format("%s/%s-%d", exportPath, exportName, System.currentTimeMillis());
				List<String> exportFiles;
				if (dot!=null) {
					exportFiles = _TC.getDAG().exportDAG(dot, baseFileName, exportFormats, imageWidth);
				} else {
					exportFiles = _TC.getDAG().exportDAG(baseFileName, exportFormats, imageWidth);
				}
				_TC.setExportFiles(exportFiles);
				//log.info("Decomposition Graph export to file(s): ok");
			} catch (Exception ex) {
				log.error("Decomposition Graph export to file(s): EXCEPTION: ", ex);
			}
		}

		// Print other translation results
		log.info("*********************************************************");
		log.info("Event-to-Action map:\n{}", map2string( _TC.getE2A() ));
		log.info("*********************************************************");
		log.info("SLO set:\n{}", _TC.getSLO() );
		log.info("*********************************************************");
		log.info("Component-to-Sensor map:\n{}", map2string( _TC.getC2S() ));
		log.info("*********************************************************");
		log.info("Data-to-Sensor map:\n{}", map2string( _TC.getD2S() ));
		log.info("*********************************************************");
		log.info("Monitors:\n {}", _TC.getMONS() );
		log.info("*********************************************************");
		log.info("Grouping-to-EPL Rules map:\n{}", prettifyG2R(_TC.getG2R(), ""));
		log.info("*********************************************************");
		log.info("Grouping-to-Topics map:\n{}", _TC.getG2T());
		log.info("*********************************************************");
		log.info("Topics-Connections map:\n{}", _TC.getTopicConnections());
		log.info("*********************************************************");
		log.info("Metric-to-Metric Context map:\n{}", map2string(_TC.getM2MC()));
		log.info("*********************************************************");
		log.info("MVV set:\n{}", _TC.getMVV());
		log.info("*********************************************************");
		log.info("MVV_CP map:\n{}", _TC.getMvvCP());
		log.info("*********************************************************");
		log.info("CMVAR set:\n{}", _TC.getCompositeMetricVariableNames());
		log.info("*********************************************************");
		log.info("Function Definitions set:\n{}", getFunctionNames(_TC.getFUNC()));
		log.info("*********************************************************");
		log.info("Metric Constraints:\n{}", _TC.getMetricConstraints());
		log.info("*********************************************************");
		log.info("Load-Annotated Metrics:\n{}", _TC.getLoadAnnotatedMetricsSet());
		log.info("*********************************************************");
		log.info("Additional Results:\n{}", _TC.getAdditionalResults());
		log.info("*********************************************************");
		log.info("Export files:\n{}", _TC.getExportFiles());
		log.info("*********************************************************");
	}

	public String prettifyG2R(Map<String, Map<String, Set<String>>> map, String startIdent) {
		StringBuilder sb = new StringBuilder();
		String ident2 = startIdent+"  ";
		String ident3 = startIdent+"    ";
		String ident4 = startIdent+"\n      ";
		map.forEach((groupingName, groupingTopics) -> {
			sb.append(startIdent).append("-----------------------\n");
			sb.append(startIdent).append(groupingName).append(": \n");
			groupingTopics.forEach((topicName, topicRules) -> {
				sb.append(ident2).append(topicName).append(": \n");
				topicRules.forEach(
						ruleStr -> {
							ruleStr = ruleStr
									.replace("\r\n", ident4)
									.replace("\n", ident4);
							sb.append(ident3).append("- ").append(ruleStr).append("\n");
						}
				);
			});
		});
		return sb.toString();
	}
	
	protected Map<String,List<String>> map2string(Map map) {
		if (map==null) return null;
		Map<String,List<String>> newMap = new HashMap<>();
		for (Object key : map.keySet()) {
			Set values = (Set) map.get(key);
			ArrayList<String> list = new ArrayList<>();
			if (key==null) {
				newMap.put( key+"::"+key, list );
			} else
			if (key instanceof NamedElement) {
				newMap.put( key.getClass().getSimpleName()+"::"+((NamedElement)key).getName(), list );
			} else {
				newMap.put( key.getClass().getSimpleName()+"::"+key, list );
			}
			for (Object val : values) {
				if (val instanceof NamedElement) {
					list.add( val.getClass().getSimpleName()+"::"+((NamedElement)val).getName() );
				} else {
					list.add( val.getClass().getSimpleName()+"::"+val );
				}
			}
		}
		return newMap;
	}
	
	protected Collection<String> getFunctionNames(Collection<FunctionDefinition> col) {
		return col.stream()
				.map(FunctionDefinition::getName)
				.collect(Collectors.toList());
	}
}