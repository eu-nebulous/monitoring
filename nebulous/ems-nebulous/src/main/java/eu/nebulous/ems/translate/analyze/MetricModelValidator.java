/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate.analyze;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.helger.schematron.pure.SchematronResourcePure;
import com.helger.schematron.svrl.jaxb.FailedAssert;
import com.helger.schematron.svrl.jaxb.SchematronOutputType;
import com.helger.schematron.svrl.jaxb.Text;
import com.helger.xml.transform.StringStreamSource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricModelValidator {
    private static final String DEFAULT_SCHEMATRON_FILE = "classpath:metric-model-schematron.xml";

    private final ResourceLoader resourceLoader;

    // ================================================================================================================
    // Model validation methods

    public void validateModel(@NonNull Object metricModel, String modelName) throws Exception {
        log.debug("MetricModelValidator.validateModel(): Validating metric model: {}", metricModel);

        // -- Schematron Validation -------------------------------------------
		validateWithSchematron(metricModel, modelName);

        // --------------------------------------------------------------------

        log.debug("MetricModelValidator.validateModel(): Validating metric model completed: {}", metricModel);
    }

    // ========================================================================
    //  Schematron validation
    // ========================================================================

    public void validateWithSchematron(Object modelObj, String modelName) throws Exception {
        // ----------------------------------------------------------
        // Convert Map to XML

        XmlMapper mapper = new XmlMapper();
        mapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        String xmlStr = mapper
                .writerWithDefaultPrettyPrinter()
                .withRootName("metricModel")
                .writeValueAsString(modelObj);
        log.trace("SCHEMATRON: Validating metric model: {}", xmlStr);

        // ----------------------------------------------------------
        // Load Schematron rules

        String schematronFile = DEFAULT_SCHEMATRON_FILE;
        Resource resource = resourceLoader.getResource(schematronFile);
        SchematronResourcePure schRes;
        try (InputStream is = resource.getInputStream()) {
            schRes = SchematronResourcePure.fromInputStream(schematronFile, is);
            if (!schRes.isValidSchematron())
                throw new IllegalArgumentException("Invalid Schematron file: " + schematronFile);
        }

        // ----------------------------------------------------------
        // Validate model using schematron -- Alt. #1

        // Check metric model validity
		/*EValidity validity = schRes.getSchematronValidity(new StringStreamSource(xmlStr));
		boolean isMetricModelValid = validity.isValid();
		log.debug("SCHEMATRON:  Metric model is valid: {}", isMetricModelValid);
		log.debug("SCHEMATRON:  Validity: {}", validity);*/

        // ----------------------------------------------------------
        // Validate model using schematron -- Alt. #2

        // Validate metric model and get failed asserts
        SchematronOutputType schOutput = schRes.applySchematronValidationToSVRL(new StringStreamSource(xmlStr));
        assert schOutput != null;
        List<Object> failedAsserts = schOutput.getActivePatternAndFiredRuleAndFailedAssert();
        int failedAssertsCount = 0;
        for (Object object : failedAsserts) {
            if (object instanceof FailedAssert failedAssert) {
                failedAssertsCount++;
                log.warn("SCHEMATRON:  FAILED-ASSERT: {} -- {}",
                        failedAssert.getId(), //failedAssert.getTest(),
                        getSchematronDiagnosticMessages( failedAssert.getDiagnosticReferenceOrPropertyReferenceOrText() )
                );
            }
        }
        boolean isMetricModelValid = (failedAssertsCount == 0);

        // ----------------------------------------------------------
        // Report validation results

        log.debug("SCHEMATRON:  Num. of asserts failed: {}", failedAssertsCount);
        log.debug("SCHEMATRON:  Metric model is valid: {}", isMetricModelValid);

        if (! isMetricModelValid)
            throw new IllegalArgumentException ("Invalid Metric Model: "+modelName);
        log.info("MetricModelValidator:  Metric model is valid: {}", modelName);
    }

    public List getSchematronDiagnosticMessages(Object o) {
        if (o instanceof String s) {
            s = s.replaceAll("[ \t\r\n]+", " ").trim();
            log.trace("SCHEMATRON: ----> STRING: {}", s);
            return Collections.singletonList(s);
        } else if (o instanceof Text t) {
            log.trace("SCHEMATRON: ----> TEXT: {} = {}", t.getContentCount(), t.getContent().size());
            return t.getContent().stream().map(this::getSchematronDiagnosticMessages).flatMap(List::stream).toList();
        } else if (o instanceof Collection c) {
            log.trace("SCHEMATRON: ----> COLLECTION: {}", c.size());
            return c.stream().flatMap(xx -> getSchematronDiagnosticMessages(xx).stream()).toList();
        } else {
            log.trace("SCHEMATRON: ----> OTHER: {} {}", o.getClass(), o);
            return Collections.singletonList(o.toString());
        }
    }
}