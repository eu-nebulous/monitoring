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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.InvalidPropertiesFormatException;
import java.util.UUID;

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
    protected SecretKey getKeyFromProperties() {
        if (StringUtils.isBlank(jwtTokenProperties.getSecret()))
            throw new InvalidPropertiesFormatException("JWT token secret key is blank. Check 'jwt.secret' property.");
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtTokenProperties.getSecret()));
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
        return Jwts.parser()
                .verifyWith(getKeyFromProperties())
                .build()
                .parseSignedClaims(token.replace(TOKEN_PREFIX, ""))
                .getPayload();
    }

    public String createToken(String userName) {
        return createToken(userName, getKeyFromProperties());
    }

    public String createToken(String userName, Key key) {
        return Jwts.builder()
                .subject(userName)
                .audience().add(AUDIENCE_UPPERWARE).and()
                .expiration(new Date(System.currentTimeMillis() + jwtTokenProperties.getExpirationTime()))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(String userName) {
        return Jwts.builder()
                .subject(userName)
                .header().contentType(REFRESH_HEADER_STRING).and()
                .audience().add(AUDIENCE_JWT).and()
                .id(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + jwtTokenProperties.getRefreshTokenExpirationTime()))
                .signWith(getKeyFromProperties())
                .compact();
    }
}