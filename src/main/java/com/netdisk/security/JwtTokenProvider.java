package com.netdisk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {
    private final SecretKey secretKey;

    public JwtTokenProvider(com.netdisk.config.AppProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String userUuid, String sessionUuid, long seconds) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("sid", sessionUuid);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userUuid)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(seconds)))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
    }
}
