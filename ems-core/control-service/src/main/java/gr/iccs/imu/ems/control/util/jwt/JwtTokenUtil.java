/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util.jwt;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * Run:
 * java -cp .\target\control-service.jar  -Dloader.main=jwt.util.gr.iccs.imu.ems.control.JwtTokenUtil -Dlogging.level.ROOT=WARN -Dlogging.level.gr.iccs.imu.ems.util=ERROR org.springframework.boot.loader.PropertiesLauncher createKey
 * -or-
 * java -cp .\target\control-service.jar  -Dloader.main=jwt.util.gr.iccs.imu.ems.control.JwtTokenUtil -Dlogging.level.ROOT=WARN -Dlogging.level.gr.iccs.imu.ems.util=ERROR org.springframework.boot.loader.PropertiesLauncher create [USER]?
 * -or-
 * java -cp .\target\control-service.jar  -Dloader.main=jwt.util.gr.iccs.imu.ems.control.JwtTokenUtil -Dlogging.level.ROOT=WARN -Dlogging.level.gr.iccs.imu.ems.util=ERROR org.springframework.boot.loader.PropertiesLauncher parser [TOKEN]
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = { "gr.iccs.imu.ems.control.util.jwt", "gr.iccs.imu.ems.util", "com.ulisesbocchio" })
@RequiredArgsConstructor
public class JwtTokenUtil {
    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(JwtTokenUtil.class);
        springApplication.setBannerMode(Banner.Mode.OFF);
        springApplication.setWebApplicationType(WebApplicationType.NONE);
        springApplication.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = springApplication.run(args);

        try {
            execCommand(ctx.getBean(JwtTokenService.class), args);
        } catch (Exception e) {
            System.err.printf("%sERROR: %s%s\n", ConsoleColors.RED_BOLD_BRIGHT, getExceptionMessages(e), ConsoleColors.RESET);
            exit(1);
        }
    }

    public static void execCommand(JwtTokenService jwtService, String... args) {
        if (args.length>0) {
            String token;
            if ("createKey".equalsIgnoreCase(args[0].trim())) {
                String key = jwtService.keyToString(jwtService.createKey());
                System.out.printf("%sNew secret key:\n%s%s%s\n", ConsoleColors.WHITE_BOLD_BRIGHT, ConsoleColors.YELLOW_BOLD_BRIGHT, key, ConsoleColors.RESET);
            } else if ("create".equalsIgnoreCase(args[0].trim())) {
                String user = args.length > 1 && !args[1].trim().isEmpty() ? args[1].trim() : "USER";
                token = jwtService.createToken(user);
                System.out.printf("%sNew JWT token for user %s%s:\n%s%s%s\n",
                        ConsoleColors.GREEN_BOLD_BRIGHT, ConsoleColors.WHITE_BOLD_BRIGHT, user, ConsoleColors.CYAN_BOLD_BRIGHT, token, ConsoleColors.RESET);
            } else if ("parse".equalsIgnoreCase(args[0].trim())) {
                token = args[1];
                try {
                    Claims claims = jwtService.parseToken(token);
                    System.out.printf("%sToken claims: %s %s%s\n", ConsoleColors.GREEN_BOLD_BRIGHT, ConsoleColors.CYAN_BOLD_BRIGHT, claims, ConsoleColors.RESET);
                } catch (Exception e) {
                    System.err.printf("%s%s%s\n", ConsoleColors.RED_BOLD_BRIGHT, getExceptionMessages(e), ConsoleColors.RESET);
                    exit(2);
                }
            } else {
                System.err.printf("%sUnknown command: %s %s %s\n", ConsoleColors.RED_BOLD_BRIGHT, ConsoleColors.RED_BACKGROUND+ConsoleColors.YELLOW_BOLD_BRIGHT, args[0], ConsoleColors.RESET);
                exit(3);
            }
        } else {
            System.err.printf("%sNo command specified%s\n", ConsoleColors.RED_BOLD_BRIGHT, ConsoleColors.RESET);
            exit(4);
        }
    }

    private static String getExceptionMessages(Exception e) {
        StringBuilder s = new StringBuilder();
        s.append(e.getMessage());
        Throwable t = e.getCause();
        while (t!=null) { s.append(" -> ").append(t.getMessage()); t = t.getCause(); }
        return s.toString();
    }

    protected static void exit(int errorCode) {
        System.exit(errorCode);
    }

    // See: https://stackoverflow.com/questions/5762491/how-to-print-color-in-console-using-system-out-println
    public static class ConsoleColors {
        // Reset
        public static final String RESET = "\033[0m";  // Text Reset

        // Regular Colors
        public static final String BLACK = "\033[0;30m";   // BLACK
        public static final String RED = "\033[0;31m";     // RED
        public static final String GREEN = "\033[0;32m";   // GREEN
        public static final String YELLOW = "\033[0;33m";  // YELLOW
        public static final String BLUE = "\033[0;34m";    // BLUE
        public static final String PURPLE = "\033[0;35m";  // PURPLE
        public static final String CYAN = "\033[0;36m";    // CYAN
        public static final String WHITE = "\033[0;37m";   // WHITE

        // Bold
        public static final String BLACK_BOLD = "\033[1;30m";  // BLACK
        public static final String RED_BOLD = "\033[1;31m";    // RED
        public static final String GREEN_BOLD = "\033[1;32m";  // GREEN
        public static final String YELLOW_BOLD = "\033[1;33m"; // YELLOW
        public static final String BLUE_BOLD = "\033[1;34m";   // BLUE
        public static final String PURPLE_BOLD = "\033[1;35m"; // PURPLE
        public static final String CYAN_BOLD = "\033[1;36m";   // CYAN
        public static final String WHITE_BOLD = "\033[1;37m";  // WHITE

        // Underline
        public static final String BLACK_UNDERLINED = "\033[4;30m";  // BLACK
        public static final String RED_UNDERLINED = "\033[4;31m";    // RED
        public static final String GREEN_UNDERLINED = "\033[4;32m";  // GREEN
        public static final String YELLOW_UNDERLINED = "\033[4;33m"; // YELLOW
        public static final String BLUE_UNDERLINED = "\033[4;34m";   // BLUE
        public static final String PURPLE_UNDERLINED = "\033[4;35m"; // PURPLE
        public static final String CYAN_UNDERLINED = "\033[4;36m";   // CYAN
        public static final String WHITE_UNDERLINED = "\033[4;37m";  // WHITE

        // Background
        public static final String BLACK_BACKGROUND = "\033[40m";  // BLACK
        public static final String RED_BACKGROUND = "\033[41m";    // RED
        public static final String GREEN_BACKGROUND = "\033[42m";  // GREEN
        public static final String YELLOW_BACKGROUND = "\033[43m"; // YELLOW
        public static final String BLUE_BACKGROUND = "\033[44m";   // BLUE
        public static final String PURPLE_BACKGROUND = "\033[45m"; // PURPLE
        public static final String CYAN_BACKGROUND = "\033[46m";   // CYAN
        public static final String WHITE_BACKGROUND = "\033[47m";  // WHITE

        // High Intensity
        public static final String BLACK_BRIGHT = "\033[0;90m";  // BLACK
        public static final String RED_BRIGHT = "\033[0;91m";    // RED
        public static final String GREEN_BRIGHT = "\033[0;92m";  // GREEN
        public static final String YELLOW_BRIGHT = "\033[0;93m"; // YELLOW
        public static final String BLUE_BRIGHT = "\033[0;94m";   // BLUE
        public static final String PURPLE_BRIGHT = "\033[0;95m"; // PURPLE
        public static final String CYAN_BRIGHT = "\033[0;96m";   // CYAN
        public static final String WHITE_BRIGHT = "\033[0;97m";  // WHITE

        // Bold High Intensity
        public static final String BLACK_BOLD_BRIGHT = "\033[1;90m"; // BLACK
        public static final String RED_BOLD_BRIGHT = "\033[1;91m";   // RED
        public static final String GREEN_BOLD_BRIGHT = "\033[1;92m"; // GREEN
        public static final String YELLOW_BOLD_BRIGHT = "\033[1;93m";// YELLOW
        public static final String BLUE_BOLD_BRIGHT = "\033[1;94m";  // BLUE
        public static final String PURPLE_BOLD_BRIGHT = "\033[1;95m";// PURPLE
        public static final String CYAN_BOLD_BRIGHT = "\033[1;96m";  // CYAN
        public static final String WHITE_BOLD_BRIGHT = "\033[1;97m"; // WHITE

        // High Intensity backgrounds
        public static final String BLACK_BACKGROUND_BRIGHT = "\033[0;100m";// BLACK
        public static final String RED_BACKGROUND_BRIGHT = "\033[0;101m";// RED
        public static final String GREEN_BACKGROUND_BRIGHT = "\033[0;102m";// GREEN
        public static final String YELLOW_BACKGROUND_BRIGHT = "\033[0;103m";// YELLOW
        public static final String BLUE_BACKGROUND_BRIGHT = "\033[0;104m";// BLUE
        public static final String PURPLE_BACKGROUND_BRIGHT = "\033[0;105m"; // PURPLE
        public static final String CYAN_BACKGROUND_BRIGHT = "\033[0;106m";  // CYAN
        public static final String WHITE_BACKGROUND_BRIGHT = "\033[0;107m";   // WHITE
    }
}
