/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.model.*;
import eu.nebulous.ems.translate.NebulousEmsTranslatorProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

import static eu.nebulous.ems.translate.analyze.AnalysisUtils.*;

// ------------------------------------------------------------------------
//  Constraints decomposition methods
// ------------------------------------------------------------------------

@Slf4j
@Service
@RequiredArgsConstructor
class ConstraintsHelper extends AbstractHelper {
    private final static String DEFAULT_CONSTRAINT_NAME_SUFFIX = "_CONSTRAINT";

    private final NebulousEmsTranslatorProperties properties;
    private final MetricsHelper metricsHelper;

    void decomposeConstraints(TranslationContext _TC, Map<NamesKey, Object> sloList) {
        sloList.forEach((sloNamesKey, sloSpec) -> {
            ServiceLevelObjective slo = ServiceLevelObjective.builder()
                    .name(sloNamesKey.name())
                    .object(sloSpec)
                    .build();
            _TC.addSLO(slo);
            _TC.getDAG().addTopLevelNode(slo);

            Object object = asMap(sloSpec).get("constraint");
            if (object==null)
                throw createException("SLO without 'constraint': "+sloSpec);
            if (object instanceof Map constraintSpec) {
                Constraint constraint = decomposeConstraint(_TC, asMap(constraintSpec), sloNamesKey, slo);
                slo.setConstraint(constraint);
            } else
                throw createException("SLO constraint is not Map: "+sloSpec);
        });
    }

    Constraint decomposeConstraint(@NonNull TranslationContext _TC, Map<String, Object> constraintSpec, NamesKey parentNamesKey, NamedElement parent) {
        // Get constraint type
        String type = getSpecField(constraintSpec, "type");
        if (StringUtils.isBlank(type))
            throw createException("Constraint without 'type': "+constraintSpec);

        String constraintName = getSpecName(constraintSpec);
        if (StringUtils.isBlank(constraintName))
            constraintName = parentNamesKey.child + DEFAULT_CONSTRAINT_NAME_SUFFIX;
        NamesKey constraintNamesKey = createNamesKey(parentNamesKey, constraintName);
        if ($$(_TC).constraintsUsed.containsKey(constraintNamesKey)) {
            Constraint cachedConstraint = $$(_TC).constraintsUsed.get(constraintNamesKey);
            _TC.getDAG().addNode(parent, cachedConstraint);
            return cachedConstraint;
        }

        // Delegate constraint decomposition based on type
        Constraint constraintNode = switch (type) {
            case "metric" ->
                    decomposeMetricConstraint(_TC, constraintSpec, parentNamesKey, parent);
            case "logical" ->
                    decomposeLogicalConstraint(_TC, constraintSpec, parentNamesKey, parent);
            case "conditional" ->
                    decomposeConditionalConstraint(_TC, constraintSpec, parentNamesKey, parent);
            default ->
                    throw createException("Constraint 'type' not supported: " + constraintSpec);
        };
        $$(_TC).constraintsUsed.put(constraintNamesKey, constraintNode);

        return constraintNode;
    }

    private MetricConstraint decomposeMetricConstraint(@NonNull TranslationContext _TC, Map<String, Object> constraintSpec, NamesKey parentNamesKey, NamedElement parent) {
        // Get needed fields
        String constraintName = getSpecName(constraintSpec);
        String metricName = getMandatorySpecField(constraintSpec, "metric", "Metric Constraint without 'metric': ");
        String comparisonOperator = getMandatorySpecField(constraintSpec, "operator", "Metric Constraint without 'operator': ");
        Double threshold = getSpecNumber(constraintSpec, "threshold", "Metric Constraint without 'threshold': ");

        if (StringUtils.isBlank(constraintName))
            constraintName = parentNamesKey.child + DEFAULT_CONSTRAINT_NAME_SUFFIX;
        NamesKey constraintNamesKey = createNamesKey(parentNamesKey, constraintName);
        NamesKey metricNamesKey = createNamesKey(parentNamesKey, metricName);

        // Further field checks
        if (! $$(_TC).allMetrics.containsKey(metricNamesKey))
            throw createException("Unspecified metric '"+metricNamesKey.name()+"' found in metric constraint: "+ constraintSpec);

        if (! isComparisonOperator(comparisonOperator))
            throw createException("Unknown comparison operator '"+comparisonOperator+"' in metric constraint: "+ constraintSpec);

        // Update TC
        MetricConstraint metricConstraint = MetricConstraint.builder()
                .name(constraintNamesKey.name())
                .object(constraintSpec)
                .comparisonOperator(ComparisonOperatorType.byOperator(comparisonOperator))
                .threshold(threshold)
                .build();
        _TC.getDAG().addNode(parent, metricConstraint);

        // Decompose metric
        MetricContext metric = metricsHelper.decomposeMetric(
                _TC, asMap($$(_TC).allMetrics.get(metricNamesKey)), constraintNamesKey, metricConstraint);

        // Complete TC update
        metricConstraint.setMetricContext(metric);
        _TC.addMetricConstraint(metricConstraint);

        return metricConstraint;
    }

    private LogicalConstraint decomposeLogicalConstraint(TranslationContext _TC, Map<String, Object> constraintSpec, NamesKey parentNamesKey, NamedElement parent) {
        // Get needed fields
        String constraintName = getSpecName(constraintSpec);
        String logicalOperator = getMandatorySpecField(constraintSpec, "operator", "Logical Constraint without 'operator': ");
        List<Object> composingConstraints = asList(constraintSpec.get("constraints"));

        if (StringUtils.isBlank(constraintName))
            constraintName = parentNamesKey.child + DEFAULT_CONSTRAINT_NAME_SUFFIX;
        NamesKey constraintNamesKey = createNamesKey(parentNamesKey, constraintName);

        // Further field checks
        if (! isLogicalOperator(logicalOperator))
            throw createException("Unknown logical operator '"+logicalOperator+"' in metric constraint: "+ constraintSpec);
        if (composingConstraints==null || composingConstraints.isEmpty())
            throw createException("At least one composing constraint must be provided in logical constraint: "+ constraintSpec);

        // Update TC
        LogicalConstraint logicalConstraint = LogicalConstraint.builder()
                .name(constraintNamesKey.name())
                .object(constraintSpec)
                .logicalOperator(LogicalOperatorType.valueOf(logicalOperator.trim().toUpperCase()))
                .build();
        _TC.getDAG().addNode(parent, logicalConstraint);

        // Decompose composing constraints
        List<Constraint> composingConstraintsList = new ArrayList<>();
        for (Object cc : composingConstraints) {
            String sloName = cc.toString();
            Constraint childConstraint = decomposeComposingConstraint(_TC, sloName, parentNamesKey, logicalConstraint);
            composingConstraintsList.add(childConstraint);
        }
        logicalConstraint.setConstraints(composingConstraintsList);

        // Complete TC update
        _TC.addLogicalConstraint(logicalConstraint);

        return logicalConstraint;
    }

    private IfThenConstraint decomposeConditionalConstraint(TranslationContext _TC, Map<String, Object> constraintSpec, NamesKey parentNamesKey, NamedElement parent) {
        // Get needed fields
        String constraintName = getSpecName(constraintSpec);

        if (StringUtils.isBlank(constraintName))
            constraintName = parentNamesKey.child + DEFAULT_CONSTRAINT_NAME_SUFFIX;
        NamesKey constraintNamesKey = createNamesKey(parentNamesKey, constraintName);

        // Get the referenced constraints names (their SLOs' actually)
        String   ifSloName = getMandatorySpecField(constraintSpec, "if", "Unspecified IF part in conditional constraint '"+constraintNamesKey.name()+"': "+ constraintSpec);
        String thenSloName = getMandatorySpecField(constraintSpec, "then", "Unspecified THEN part in conditional constraint '"+constraintNamesKey.name()+"': "+ constraintSpec);
        String elseSloName = getSpecField(constraintSpec, "else");

        // Update TC
        IfThenConstraint conditionalConstraint = IfThenConstraint.builder()
                .name(constraintNamesKey.name())
                .object(constraintSpec)
                .build();
        _TC.getDAG().addNode(parent, conditionalConstraint);

        // Decompose the composing metrics
        Constraint   ifConstraint = decomposeComposingConstraint(_TC,   ifSloName, parentNamesKey, conditionalConstraint);
        Constraint thenConstraint = decomposeComposingConstraint(_TC, thenSloName, parentNamesKey, conditionalConstraint);
        Constraint elseConstraint = StringUtils.isNotBlank(elseSloName)
                ? decomposeComposingConstraint(_TC, elseSloName, parentNamesKey, conditionalConstraint) : null;

        // Update the conditional (main) constraint
        conditionalConstraint.setIfConstraint(ifConstraint);
        conditionalConstraint.setThenConstraint(thenConstraint);
        if (elseConstraint!=null)
            conditionalConstraint.setElseConstraint(elseConstraint);

        // Complete TC update
        _TC.addIfThenConstraint(conditionalConstraint);

        return conditionalConstraint;
    }

    private Constraint decomposeComposingConstraint(TranslationContext _TC, String sloName, NamesKey parentNamesKey, Constraint parentConstraint) {
        // Get referenced constraint spec (its SLO spec actually)
        Object sloSpec = $$(_TC).allSLOs.get(NamesKey.create(parentNamesKey.parent, sloName));

        // Construct SLO namesKey
        NamesKey sloNamesKey = NamesKey.create(getContainerName(sloSpec), sloName);

        // Get constraint spec from SLO spec
        Map<String, Object> constraintSpec = asMap(asMap(sloSpec).get("constraint"));

        // Decompose composing constraint
        return decomposeConstraint(_TC, constraintSpec, sloNamesKey, parentConstraint);
    }
}