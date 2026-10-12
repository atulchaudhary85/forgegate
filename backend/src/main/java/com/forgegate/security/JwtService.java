package com.forgegate.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expiryMinutes;
    public JwtService(@Value("${forgegate.jwt.secret}") String secret, @Value("${forgegate.jwt.expiry-minutes}") long expiryMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes; set it in the environment");
        if (expiryMinutes < 1 || expiryMinutes > 10080) throw new IllegalStateException("JWT_EXPIRY_MINUTES must be between 1 and 10080");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }
    public String issue(String email, long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(email).claim("uid", userId).claim("role", role).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expiryMinutes * 60))).signWith(key).compact();
    }
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
