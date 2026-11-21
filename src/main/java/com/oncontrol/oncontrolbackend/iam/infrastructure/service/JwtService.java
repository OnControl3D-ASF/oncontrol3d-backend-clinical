package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.profiles.application.dto.ProfileIdentity;
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

    /** Issuer of tokens minted by clinical (auth service uses "oncontrol3d-auth"). */
    static final String ISSUER = "oncontrol3d-clinical";

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
        claims.put("role", "ORGANIZATION");
        claims.put("organizationId", user.getId());
        return createToken(claims, user.getEmail());
    }

    /**
     * Token for doctors and patients following the claim contract (numeric ids,
     * role DOCTOR | PATIENT).
     */
    public String generateTokenForProfile(ProfileIdentity identity) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", identity.type().name());
        claims.put("organizationId", identity.organizationId());
        claims.put("profileId", identity.profileId());
        claims.put("doctorProfileId", identity.doctorProfileId());
        claims.put("patientProfileId", identity.patientProfileId());
        return createToken(claims, identity.email());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .issuer(ISSUER)
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
