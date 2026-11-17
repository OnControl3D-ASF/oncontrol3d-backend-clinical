package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    /** HMAC-SHA256 requires a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    @Value("${authorization.jwt.secret}")
    private String secretKey;

    @Value("${authorization.jwt.expiration}")
    private Long expiration;

    /**
     * Fail fast at startup: a missing placeholder already stops the context, but an
     * empty or short JWT_SECRET would otherwise only fail on the first login.
     */
    @PostConstruct
    void validateSecret() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required and must not be blank");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes (256 bits); "
                            + "generate one with: openssl rand -hex 32");
        }
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("organizationName", user.getOrganizationName());
        
        return createToken(claims, user.getEmail());
    }

    /**
     * Generate JWT token for profiles (doctors and patients)
     * @param email Profile email
     * @param profileId Profile ID (DOC-xxx or PAT-xxx)
     * @param profileType Type of profile (DOCTOR or PATIENT)
     * @return JWT token
     */
    public String generateTokenForProfile(String email, String profileId, String profileType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "PROFILE");
        claims.put("profileType", profileType);
        claims.put("profileId", profileId);
        claims.put("email", email);
        
        return createToken(claims, email);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, String email) {
        final String username = extractUsername(token);
        return (username.equals(email) && !isTokenExpired(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public UserRole extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return UserRole.valueOf(claims.get("role", String.class));
    }

    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Long.class);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
