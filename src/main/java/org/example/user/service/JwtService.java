package org.example.user.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtService {

    private final SecretKey secretKey;
    @Getter
    private final long accessTokenTtlMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-ttl-ms:900000}") long accessTokenTtlMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTokenTtlMs;
        log.info("JwtService initialized, accessTokenTtlMs={}", accessTokenTtlMs);
    }

    public String generateAccessToken(UUID userId, String username, List<String> roles) {
        log.debug("Generating access token: userId={}, username={}, roles={}", userId, username, roles);
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenTtlMs)))
                .signWith(secretKey)
                .compact();
        log.debug("Access token generated for userId={}", userId);
        return token;
    }

    public Claims parseClaims(String token) {
        log.trace("Parsing JWT claims");
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        log.trace("JWT claims parsed: subject={}", claims.getSubject());
        return claims;
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            log.debug("JWT is valid: subject={}, expiration={}", claims.getSubject(), claims.getExpiration());
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: subject={}, expiredAt={}", ex.getClaims().getSubject(), ex.getClaims().getExpiration());
            return false;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT: {}", ex.getMessage());
            return false;
        }
    }
}
