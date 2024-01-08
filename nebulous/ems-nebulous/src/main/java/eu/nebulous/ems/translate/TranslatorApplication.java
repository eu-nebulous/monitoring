/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package eu.nebulous.ems.translate;

import gr.iccs.imu.ems.translate.TranslationContext;
import gr.iccs.imu.ems.translate.TranslationContextPrinter;
import gr.iccs.imu.ems.translate.TranslationContextPrinterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Paths;

/*
 * Run the 'TranslatorApplication' from command line
 *
 * 1) Compile application and retrieve dependencies:
 *      mvn clean package
 *      mvn dependency:copy-dependencies
 *
 * 2) Set environment variables:
 *      EMS_CONFIG_DIR=....
 *      SPRING_CONFIG_LOCATION=classpath:rule-templates.yml,file:${EMS_CONFIG_DIR}/ems-server.yml
 *
 * 3) Run the application:
 *    Windows:
 *      java -cp target\classes;target\dependency\* eu.nebulous.ems.translate.TranslatorApplication ...<<MODEL_NAME>>...
 *    Linux:
 *      java -cp target/classes:target/dependency/* eu.nebulous.ems.translate.TranslatorApplication ...<<MODEL_NAME>>...
 */
@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class TranslatorApplication implements CommandLineRunner {

    private static boolean standalone = false;
    private final NebulousEmsTranslator translator;
    private final NebulousEmsTranslatorProperties translatorProperties;
    private final TranslationContextPrinter printer;
    private final TranslationContextPrinterProperties printerProperties;

    public static void main(String[] args) {
        standalone = true;
        SpringApplication.run(TranslatorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        if (!standalone) return;    // Execute only if called by 'main()'

        log.info("Testing MODEL-to-EPL Translator");
        log.info("Base dir: {}", Paths.get("").toFile().getAbsolutePath());

        log.info("Args: {}", java.util.Arrays.asList(args));
        String modelPath = (args.length > 0 && !args[0].trim().isEmpty())
                ? args[0].trim() : "ems-main/surveillance_app_SAMPLE_metric_model.yml";

        log.info("App-model: {}", modelPath);
        translatorProperties.setModelsDir("");
        TranslationContext _TC = translator.translate(modelPath);
        //log.info("TC: {}", _TC);

        printerProperties.getDag().setExportToFileEnabled(true);
        printerProperties.getDag().setExportFormats(new String[] { "svg" });
        printerProperties.getDag().setExportImageWidth(1600);
        printerProperties.getDag().setExportPath(".");
        printer.printResults(_TC, modelPath+"-export");
    }
}