/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.webconf;

//import properties.gr.iccs.imu.ems.control.StaticResourceProperties;
import gr.iccs.imu.ems.control.properties.WebSecurityProperties;
import gr.iccs.imu.ems.control.util.jwt.JwtTokenService;
import gr.iccs.imu.ems.util.PasswordUtil;
import gr.iccs.imu.ems.util.StrUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.security.InvalidParameterException;
import java.util.*;

@Slf4j
@Order(1)
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig implements InitializingBean {

    public final static String ROLE_USER_FORM = "ROLE_USER_FORM";
    public static final String ROLE_JWT_TOKEN = "ROLE_JWT_TOKEN";
    public static final String ROLE_API_KEY = "ROLE_API_KEY";
    public static final String ROLE_OTP = "ROLE_OTP";

    //private final StaticResourceProperties staticResourceProperties;
    private final WebSecurityProperties properties;
    private final PasswordUtil passwordUtil;
    private final JwtTokenService jwtTokenService;

    private final Map<String,Long> otpCache = new HashMap<>();

    @Value("${melodic.security.enabled:true}")
    private boolean securityEnabled;
    private boolean propertiesCopied;

    // JWT Token authentication fields
    private boolean jwtAuthEnabled;
    private String jwtRequestParam;
    private boolean printSampleJwt;

    // API-Key authentication fields
    private boolean apiKeyAuthEnabled;
    private String apiKeyRequestHeader;
    private String apiKeyRequestParam;
    private String apiKeyValue;

    // OTP authentication fields
    private boolean otpAuthEnabled;
    private long otpDuration;
    private String otpRequestHeader;
    private String otpRequestParam;

    // User form authentication fields
    private boolean userFormAuthEnabled;
    private String username;
    private String password;

    private String loginPage;
    private String loginUrl;
    private String loginSuccessUrl;
    private String loginFailureUrl;
    private String logoutUrl;
    private String logoutSuccessUrl;

    // Permitted URLs
    private String[] permittedUrls;

    private final static String divider = "--------------------------------------------------------------------------------";

    @Override
    public void afterPropertiesSet() {
        copyPropertiesToLocalFields();
    }

    private void copyPropertiesToLocalFields() {
        if (properties==null) return;
        if (propertiesCopied) return;

        // JWT Token authentication fields
        jwtAuthEnabled = properties.getJwtAuthentication().isEnabled();
        jwtRequestParam = properties.getJwtAuthentication().getRequestParameter();
        printSampleJwt = properties.getJwtAuthentication().isPrintSampleToken();

        // API-Key authentication fields
        apiKeyAuthEnabled = properties.getApiKeyAuthentication().isEnabled();
        apiKeyRequestHeader = properties.getApiKeyAuthentication().getRequestHeader();
        apiKeyRequestParam = properties.getApiKeyAuthentication().getRequestParameter();
        apiKeyValue = properties.getApiKeyAuthentication().getValue();

        // OTP authentication fields
        otpAuthEnabled = properties.getOtpAuthentication().isEnabled();
        otpDuration = properties.getOtpAuthentication().getDuration();
        otpRequestHeader = properties.getOtpAuthentication().getRequestHeader();
        otpRequestParam = properties.getOtpAuthentication().getRequestParameter();

        // User form authentication fields
        userFormAuthEnabled = properties.getFormAuthentication().isEnabled();
        username = properties.getFormAuthentication().getUsername();
        password = properties.getFormAuthentication().getPassword();

        loginPage = properties.getFormAuthentication().getLoginPage();
        loginUrl = properties.getFormAuthentication().getLoginUrl();
        loginSuccessUrl = properties.getFormAuthentication().getLoginSuccessUrl();
        loginFailureUrl = properties.getFormAuthentication().getLoginFailureUrl();
        logoutUrl = properties.getFormAuthentication().getLogoutUrl();
        logoutSuccessUrl = properties.getFormAuthentication().getLogoutSuccessUrl();

        // Permitted URLs
        permittedUrls = properties.getPermittedUrls()!=null
                ? properties.getPermittedUrls().toArray(new String[0])
                : new String[0];

        propertiesCopied = true;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        if (securityEnabled && userFormAuthEnabled && (StringUtils.isBlank(username) || StringUtils.isEmpty(password)))
            throw new InvalidParameterException("User form authentication is enabled but username or password is blank");
        if (securityEnabled && apiKeyAuthEnabled && StringUtils.isBlank(apiKeyValue))
            throw new InvalidParameterException("API Key authentication is enabled but no API Key provided or it is blank");
        if (permittedUrls==null) permittedUrls = new String[] { "/favicon.ico", "/health" };

        if (securityEnabled && userFormAuthEnabled) {
            log.debug("afterPropertiesSet: Admin Username: {}", username);
            log.debug("afterPropertiesSet: Admin Password: {}", passwordUtil.encodePassword(password));
        }
        if (securityEnabled && apiKeyAuthEnabled) {
            log.debug("afterPropertiesSet: API Key: {}", passwordUtil.encodePassword(apiKeyValue));
        }
        if (printSampleJwt) {
            try {
                log.info("afterPropertiesSet:\n{}\nSample JWT Token: \nBearer {}\n{}",
                        divider, jwtTokenService.createToken("USER"), divider);
            } catch (Throwable e) {
                String s = StrUtil.exceptionToDetailsString(e);
                log.error("afterPropertiesSet: Failed to generate sample JWT Token: {}", s);
                log.debug("afterPropertiesSet: Failed to generate sample JWT Token: EXCEPTION:\n", e);
            }
        }

        log.debug("afterPropertiesSet: ---------------------");
        log.debug("afterPropertiesSet:      securityEnabled: {}", securityEnabled);
        log.debug("afterPropertiesSet: ---------------------");
        log.debug("afterPropertiesSet:  jwtTokenAuthEnabled: {}", jwtAuthEnabled);
        log.debug("afterPropertiesSet: jwtTokenRequestParam: {}", jwtRequestParam);
        log.debug("afterPropertiesSet: ---------------------");
        log.debug("afterPropertiesSet:    apiKeyAuthEnabled: {}", apiKeyAuthEnabled);
        log.debug("afterPropertiesSet:  apiKeyRequestHeader: {}", apiKeyRequestHeader);
        log.debug("afterPropertiesSet:   apiKeyRequestParam: {}", apiKeyRequestParam);
        log.debug("afterPropertiesSet: ---------------------");
        log.debug("afterPropertiesSet:       otpAuthEnabled: {}", otpAuthEnabled);
        log.debug("afterPropertiesSet:          otpDuration: {}", otpDuration);
        log.debug("afterPropertiesSet:     otpRequestHeader: {}", otpRequestHeader);
        log.debug("afterPropertiesSet:      otpRequestParam: {}", otpRequestParam);
        log.debug("afterPropertiesSet: ---------------------");
        log.debug("afterPropertiesSet:  userFormAuthEnabled: {}", userFormAuthEnabled);
        log.debug("afterPropertiesSet:         username: {}", username);
        log.debug("afterPropertiesSet:        loginPage: {}", loginPage);
        log.debug("afterPropertiesSet:         loginUrl: {}", loginUrl);
        log.debug("afterPropertiesSet:  loginSuccessUrl: {}", loginSuccessUrl);
        log.debug("afterPropertiesSet:     loginFailUrl: {}", loginFailureUrl);
        log.debug("afterPropertiesSet:        logoutUrl: {}", logoutUrl);
        log.debug("afterPropertiesSet: logoutSuccessUrl: {}", logoutSuccessUrl);
        log.debug("afterPropertiesSet: ---------------------");
        log.debug("afterPropertiesSet:        permittedUrls: {}", Arrays.asList(permittedUrls));
        log.debug("afterPropertiesSet: ---------------------");
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        copyPropertiesToLocalFields();
        UserDetails userDetails;
        if (this.userFormAuthEnabled && StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            userDetails = User.builder()
                    .username(username)
                    .password(passwordEncoder().encode(password))
                    .authorities(ROLE_USER_FORM)
                    .build();
            log.debug("WebSecurityConfig: User Form Admin credentials have been set: username={}", username);
        } else {
            userDetails = User.builder().build();
            log.warn("WebSecurityConfig: No Form Admin credentials provided");
        }
        return new InMemoryUserDetailsManager(userDetails);
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*@Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                // Spring Security should completely ignore the following URLs
                .antMatchers(staticResourceProperties.getFaviconContext(), "/health");
    }*/

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {

        // Check configuration settings
        checkSettings();

        // Check if authentication is disabled
        log.debug("WebSecurityConfig: security-enabled={}, user-form-auth-enabled={}, jwt-token-auth-enabled={}, api-key-auth-enabled={}, otp-auth-enabled={}",
                securityEnabled, userFormAuthEnabled, jwtAuthEnabled, apiKeyAuthEnabled, otpAuthEnabled);
        if (!securityEnabled || !userFormAuthEnabled && !jwtAuthEnabled && !apiKeyAuthEnabled && !otpAuthEnabled) {
            log.warn("WebSecurityConfig: Authentication is disabled");
            // Authorize all requests
            httpSecurity
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(
                            authorize -> authorize.anyRequest().permitAll());
            return httpSecurity.build();
        }

        // Common security settings
        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.ALWAYS));

        // Add and Configure User Form authentication
        if (userFormAuthEnabled) {
            log.debug("WebSecurityConfig: User form Authentication is enabled");
            httpSecurity
                    .formLogin(formLogin -> formLogin
                            .loginPage(loginPage).permitAll()
                            .loginProcessingUrl(loginUrl).permitAll()
                            .defaultSuccessUrl(loginSuccessUrl, false)
                            .failureUrl(loginFailureUrl).permitAll()
                    )
                    .logout(logout -> logout
                            .logoutUrl(logoutUrl).permitAll()
                            .logoutSuccessUrl(logoutSuccessUrl).permitAll()
                            .invalidateHttpSession(true)
                            .deleteCookies("JSESSIONID")
                    );
            log.debug("WebSecurityConfig: User form Authentication has been configured");
        }

        // Add configured authentication filters
        Class<? extends Filter> lastAuthFilterClass = UsernamePasswordAuthenticationFilter.class;
        Filter f;
        if (apiKeyAuthEnabled) {
            log.debug("WebSecurityConfig: API-Key Authentication is enabled");
            httpSecurity
                    .addFilterAfter(f=apiKeyAuthenticationFilter(), lastAuthFilterClass);
            lastAuthFilterClass = f.getClass();
            log.debug("WebSecurityConfig: API-Key Authentication filter added");
        }
        if (jwtAuthEnabled) {
            log.debug("WebSecurityConfig: JWT-Token Authentication is enabled");
            httpSecurity
                    .addFilterAfter(f=jwtAuthorizationFilter(), lastAuthFilterClass);
            lastAuthFilterClass = f.getClass();
            log.debug("WebSecurityConfig: JWT-Token Authentication filter added");
        }
        if (otpAuthEnabled) {
            log.debug("WebSecurityConfig: OTP Authentication is enabled");
            httpSecurity
                    .addFilterAfter(f=otpAuthenticationFilter(), lastAuthFilterClass);
            lastAuthFilterClass = f.getClass();
            log.debug("WebSecurityConfig: OTP Authentication filter added");
        }
        if (apiKeyAuthEnabled || jwtAuthEnabled || otpAuthEnabled) {
            httpSecurity
                    .addFilterAfter((servletRequest, servletResponse, filterChain) -> {
                        boolean isAuthenticated = SecurityContextHolder.getContext() != null
                                && SecurityContextHolder.getContext().getAuthentication() != null
                                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated();
                        log.trace("WebSecurityConfig: Redirection filters: authenticated={}", isAuthenticated);
                        if (isAuthenticated && (servletRequest instanceof HttpServletRequest request)) {
                            String uri = request.getRequestURI();
                            log.trace("WebSecurityConfig: Redirection filters: Request uri={}", uri);
                            if (StringUtils.startsWithAny(uri, loginUrl, loginPage)) {
                                log.debug("WebSecurityConfig: Redirection filter: Redirecting {} to {}...", uri, loginSuccessUrl);
                                ((HttpServletResponse)servletResponse).sendRedirect(loginSuccessUrl);
                            }
                        }
                        filterChain.doFilter(servletRequest, servletResponse);
                    }, lastAuthFilterClass);
        }

        if (userFormAuthEnabled) {
            httpSecurity
                    //.authorizeHttpRequests(
                    //        authorize -> authorize.requestMatchers("/broker/credentials", "/baguette/ref/**").hasAnyRole(ROLE_JWT_TOKEN, ROLE_API_KEY))
                    .authorizeHttpRequests(
                            authorize -> authorize.requestMatchers(permittedUrls).permitAll())
                    .authorizeHttpRequests(
                            authorize -> authorize.anyRequest().authenticated());
        } else {
            httpSecurity
                    .authorizeHttpRequests(
                            authorize -> authorize.anyRequest().authenticated());
        }

        return httpSecurity.build();
    }

    private void checkSettings() {
        // Check User Form authentication settings
        boolean userFormAuthEnabled = this.userFormAuthEnabled && StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password);
        if (this.userFormAuthEnabled && !userFormAuthEnabled) {
            if (StringUtils.isBlank(username))
                log.warn("WebSecurityConfig: User Form authentication is enabled but -no- Username has been provided. It will not be possible to login from User form");
            if (StringUtils.isBlank(password))
                log.warn("WebSecurityConfig: User Form authentication is enabled but -no- Password has been provided. It will not be possible to login from User form");
        }

        // Check JWT Token authentication settings
        // Nothing to do

        // Check API Key authentication settings
        boolean apiKeyAuthEnabled = this.apiKeyAuthEnabled && StringUtils.isNotBlank(apiKeyValue)
                && (StringUtils.isNotBlank(apiKeyRequestHeader) || StringUtils.isNotBlank(apiKeyRequestParam));
        if (this.apiKeyAuthEnabled && !apiKeyAuthEnabled) {
            if (StringUtils.isBlank(apiKeyValue))
                log.warn("WebSecurityConfig: API Key authentication is enabled but -no- API Key has been provided. It will not be possible to authenticate using API Key");
            else
                log.warn("WebSecurityConfig: API Key authentication is enabled but -no- API Key request header or parameter has been set. It will not be possible to authenticate using API Key");
        }

        // Check OTP authentication settings
        boolean otpAuthEnabled = this.otpAuthEnabled
                && (StringUtils.isNotBlank(otpRequestHeader) || StringUtils.isNotBlank(otpRequestParam));
        if (this.otpAuthEnabled && !otpAuthEnabled) {
            log.warn("WebSecurityConfig: OTP authentication is enabled but -no- OTP request header or parameter has been set. It will not be possible to authenticate using OTP");
        }
    }

    public Filter jwtAuthorizationFilter() {
        return (servletRequest, servletResponse, filterChain) -> {
            if (servletRequest instanceof HttpServletRequest req) {

                // Get JWT token from Authorization header
                String jwtValue = req.getHeader(JwtTokenService.HEADER_STRING);
                log.debug("jwtAuthorizationFilter: Authorization Header: {}", passwordUtil.encodePassword(jwtValue));

                // ...else get JWT token from 'jwtRequestParam' query parameter
                if (StringUtils.isBlank(jwtValue)) {
                    if (StringUtils.isNotBlank(jwtRequestParam)) {
                        log.debug("jwtAuthorizationFilter: Authorization Header is missing. Checking for '{}' parameter", jwtRequestParam);
                        jwtValue = req.getParameter(jwtRequestParam);
                        log.debug("jwtAuthorizationFilter: '{}' parameter value: {}", jwtRequestParam, passwordUtil.encodePassword(jwtValue));
                        if (StringUtils.isNotBlank(jwtValue))
                            jwtValue = JwtTokenService.TOKEN_PREFIX + jwtValue;
                    } else {
                        log.debug("jwtAuthorizationFilter: JWT token not found in headers and no JWT token parameter has been set");
                    }
                }

                // Check JWT token validity
                if (jwtValue!=null && jwtValue.startsWith(JwtTokenService.TOKEN_PREFIX)) {
                    try {
                        log.debug("jwtAuthorizationFilter: Parsing Authorization header...");
                        Claims claims = jwtTokenService.parseToken(jwtValue);
                        String user = claims.getSubject();
                        Set<String> audience = claims.getAudience();
                        log.debug("jwtAuthorizationFilter: Authorization header -->     user: {}", user);
                        log.debug("jwtAuthorizationFilter: Authorization header --> audience: {}", audience);
                        if (user!=null && audience!=null) {
                            if (audience.contains(JwtTokenService.AUDIENCE_UPPERWARE)) {
                                log.debug("jwtAuthorizationFilter: JWT token is valid");
                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(user, null,
                                                Collections.singletonList(new SimpleGrantedAuthority(ROLE_JWT_TOKEN)));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                                log.debug("jwtAuthorizationFilter: Security context updated");
                            } else {
                                log.debug("jwtAuthorizationFilter: Audience claim is invalid: {}", audience);
                            }
                        } else {
                            log.debug("jwtAuthorizationFilter: JWT token does not contain claim Audience");
                        }
                    } catch (Exception ex) {
                        log.debug("jwtAuthorizationFilter: JWT token is not valid: EXCEPTION: ", ex);
                    }
                } else {
                    log.debug("jwtAuthorizationFilter: No or invalid Authorization header");
                }
            } else {
                log.warn("jwtAuthorizationFilter: Not an HttpServletRequest");
            }

            // continue filter chain processing
            filterChain.doFilter(servletRequest, servletResponse);
        };
    }

    public Filter apiKeyAuthenticationFilter() {
        return (servletRequest, servletResponse, filterChain) -> {
            log.trace("apiKeyAuthenticationFilter: BEGIN: request={}", servletRequest);
            if (StringUtils.isNotBlank(apiKeyValue)) {
                if (servletRequest instanceof HttpServletRequest request && servletResponse instanceof HttpServletResponse) {

                    log.trace("apiKeyAuthenticationFilter: http-request={}", request);
                    String apiKey = request.getHeader(apiKeyRequestHeader);
                    log.debug("apiKeyAuthenticationFilter: Request Header API Key: {}={}", apiKeyRequestHeader, passwordUtil.encodePassword(apiKey));
                    if (StringUtils.isBlank(apiKey)) {
                        apiKey = request.getParameter(apiKeyRequestParam);
                        log.debug("apiKeyAuthenticationFilter: Request Parameter API Key: {}={}", apiKeyRequestParam, passwordUtil.encodePassword(apiKey));
                    }
                    if (StringUtils.isNotBlank(apiKey)) {
                        log.debug("apiKeyAuthenticationFilter: API Key found");

                        if (apiKeyValue.equals(apiKey)) {
                            log.debug("apiKeyAuthenticationFilter: API Key is correct");
                            try {
                                // construct one of Spring's auth tokens
                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(apiKeyRequestHeader, apiKeyValue,
                                                Collections.singletonList(new SimpleGrantedAuthority(ROLE_API_KEY)));
                                // store completed authentication in security context
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                                log.debug("apiKeyAuthenticationFilter: Security context has been updated");
                            } catch (Exception e) {
                                log.error("apiKeyAuthenticationFilter: EXCEPTION: ", e);
                            }
                        } else {
                            log.debug("apiKeyAuthenticationFilter: API Key is incorrect");
                        }
                    } else {
                        log.debug("apiKeyAuthenticationFilter: No API Key found in request headers or parameters");
                    }
                } else {
                    throw new IllegalArgumentException("API Key Authentication filter does not support non-HTTP requests and responses. Req-class: "
                            +servletRequest.getClass().getName()+"  Resp-class: "+servletResponse.getClass().getName());
                }
            } else {
                log.warn("apiKeyAuthenticationFilter: No API-Key specified");
            }

            // continue down the chain
            filterChain.doFilter(servletRequest, servletResponse);
        };
    }

    public Filter otpAuthenticationFilter() {
        return (servletRequest, servletResponse, filterChain) -> {
            log.trace("OTPAuthenticationFilter: BEGIN: request={}", servletRequest);
            if (otpAuthEnabled) {
                if (servletRequest instanceof HttpServletRequest request && servletResponse instanceof HttpServletResponse) {

                    log.trace("OTPAuthenticationFilter: http-request={}", request);
                    String otp = request.getHeader(otpRequestHeader);
                    log.debug("OTPAuthenticationFilter: Request Header OTP: {}={}", otpRequestHeader, passwordUtil.encodePassword(otp));
                    if (StringUtils.isBlank(otp)) {
                        otp = request.getParameter(otpRequestParam);
                        log.debug("OTPAuthenticationFilter: Request Parameter OTP: {}={}", otpRequestParam, passwordUtil.encodePassword(otp));
                    }
                    if (StringUtils.isNotBlank(otp)) {
                        log.debug("OTPAuthenticationFilter: OTP provided");

                        if (otpCache.containsKey(otp)) {
                            long issueTimestamp = otpCache.remove(otp);
                            boolean expired = (System.currentTimeMillis() - issueTimestamp) > otpDuration;

                            if (!expired) {
                                log.debug("OTPAuthenticationFilter: OTP found in cache");
                                try {
                                    // construct one of Spring's auth tokens
                                    UsernamePasswordAuthenticationToken authentication =
                                            new UsernamePasswordAuthenticationToken(otpRequestHeader, otp,
                                                    Collections.singletonList(new SimpleGrantedAuthority(ROLE_OTP)));
                                    // store completed authentication in security context
                                    SecurityContextHolder.getContext().setAuthentication(authentication);
                                    log.debug("OTPAuthenticationFilter: Security context has been updated");
                                } catch (Exception e) {
                                    log.error("OTPAuthenticationFilter: EXCEPTION: ", e);
                                }
                            } else {
                                log.debug("OTPAuthenticationFilter: OTP found in cache but has expired");
                            }
                        } else {
                            log.debug("OTPAuthenticationFilter: OTP not found in cache");
                        }
                    } else {
                        log.debug("OTPAuthenticationFilter: No OTP provided in request headers or parameters");
                    }
                } else {
                    throw new IllegalArgumentException("OTP Authentication filter does not support non-HTTP requests and responses. Req-class: "
                            +servletRequest.getClass().getName()+"  Resp-class: "+servletResponse.getClass().getName());
                }
            } else {
                log.warn("OTPAuthenticationFilter: OTP is disabled");
            }

            // continue down the chain
            filterChain.doFilter(servletRequest, servletResponse);
        };
    }

    public String otpCreate() {
        String newOtp = RandomStringUtils.randomAlphanumeric(32, 64);
        otpCache.put(newOtp, System.currentTimeMillis());
        return newOtp;
    }

    public long otpIssueTimestamp(String otp) {
        return otpCache.get(otp);
    }

    public long otpExpirationTimestamp(String otp) {
        return otpCache.get(otp) + otpDuration;
    }

    public long otpDuration(String otp) {
        return otpDuration;
    }

    public void otpRemove(String otp) {
        otpCache.remove(otp);
    }

    public void otpClearCache() {
        otpCache.clear();
    }
}