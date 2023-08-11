/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.util.jwt;

import gr.iccs.imu.ems.util.PasswordUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class JwtTokenService {

    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_STRING = "Authorization";
    public static final String REFRESH_HEADER_STRING = "Refresh";
    public static final String AUDIENCE_UPPERWARE = "UPPERWARE";
    public static final String AUDIENCE_JWT = "JWT_SERVER";

    private JwtTokenProperties jwtTokenProperties;
    private PasswordUtil passwordUtil;

    // ------------------------------------------------------------------------
    // Key-related methods
    // ------------------------------------------------------------------------

    public Key createKey() {
        Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        log.debug("JwtTokenService.createKey(): algorithm={}, format={}, key-size={}, base64-encoded-key={}",
                key.getAlgorithm(), key.getFormat(), key.getEncoded().length, passwordUtil.encodePassword(keyToString(key)));
        return key;
    }

    @SneakyThrows
    protected Key getKeyFromProperties() {
        if (StringUtils.isBlank(jwtTokenProperties.getSecret()))
            throw new InvalidPropertiesFormatException("JWT token secret key is blank. Check 'jwt.secret' property.");
        Key key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtTokenProperties.getSecret()));
        log.debug("JwtTokenService.getKeyFromProperties(): algorithm={}, format={}, key-size={}, base64-encoded-key={}",
                key.getAlgorithm(), key.getFormat(), key.getEncoded().length, passwordUtil.encodePassword(keyToString(key)));
        return key;
    }

    protected String keyToString(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    // ------------------------------------------------------------------------
    // JWT-related methods
    // ------------------------------------------------------------------------

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKeyFromProperties())
                .build()
                .parseClaimsJws(token.replace(TOKEN_PREFIX, ""))
                .getBody();
    }

    public String createToken(String userName) {
        return createToken(userName, getKeyFromProperties());
    }

    public String createToken(String userName, Key key) {
        return Jwts.builder()
                .setSubject(userName)
                .setAudience(AUDIENCE_UPPERWARE)
                .setExpiration(new Date(System.currentTimeMillis() + jwtTokenProperties.getExpirationTime()))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(String userName) {
        Map<String, Object> header = new HashMap<>();
        header.put(Header.CONTENT_TYPE, REFRESH_HEADER_STRING);
        return Jwts.builder()
                .setSubject(userName)
                .setHeader(header)
                .setAudience(AUDIENCE_JWT)
                .setId(UUID.randomUUID().toString())
                .setExpiration(new Date(System.currentTimeMillis() + jwtTokenProperties.getRefreshTokenExpirationTime()))
                .signWith(getKeyFromProperties())
                .compact();
    }
}