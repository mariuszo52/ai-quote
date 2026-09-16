package com.aiquote.backend.auth;

import com.aiquote.backend.tenant.AuthenticatedUser;
import com.aiquote.backend.tenant.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtService {

    // application.yml falls back to this well-known, publicly-visible value when
    // JWT_SECRET is unset, purely so local dev works with zero setup. Anyone who reads
    // the source can forge a valid token for any user/company if this value is ever
    // actually used in a reachable deployment — see the startup check below.
    private static final String INSECURE_DEV_DEFAULT_SECRET = "dev-only-insecure-secret-change-me-please-0123456789abcdef";

    private final SecretKey key;
    private final Duration validity;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") Duration validity) {
        if (INSECURE_DEV_DEFAULT_SECRET.equals(secret)) {
            log.error("app.jwt.secret is using the built-in development default. This value is "
                    + "public (it's in source control) — anyone can forge a valid login token with "
                    + "it. Set the JWT_SECRET environment variable to a long random value before "
                    + "this is reachable outside local development.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = validity;
    }

    public String generateToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("companyId", user.getCompanyId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.valueOf(claims.getSubject());
        Long companyId = ((Number) claims.get("companyId")).longValue();
        UserRole role = UserRole.valueOf(claims.get("role", String.class));
        return new AuthenticatedUser(userId, companyId, role);
    }
}
