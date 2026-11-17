package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-256-bits-0123456789";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = newJwtService(SECRET, 60_000L);
    }

    private static JwtService newJwtService(String secret, long expirationMs) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        ReflectionTestUtils.setField(service, "expiration", expirationMs);
        return service;
    }

    private static User organization() {
        return User.builder()
                .id(7L)
                .email("org@hospital.com")
                .organizationName("Hospital Central")
                .role(UserRole.ORGANIZATION)
                .build();
    }

    @Test
    void generateToken_embedsSubjectRoleAndUserId() {
        String token = jwtService.generateToken(organization());

        assertThat(jwtService.extractUsername(token)).isEqualTo("org@hospital.com");
        assertThat(jwtService.extractRole(token)).isEqualTo(UserRole.ORGANIZATION);
        assertThat(jwtService.extractUserId(token)).isEqualTo(7L);
        assertThat(jwtService.extractExpiration(token)).isAfter(new Date());
    }

    @Test
    void generateTokenForProfile_embedsProfileClaims() {
        String token = jwtService.generateTokenForProfile("doc@hospital.com", "DOC-001", "DOCTOR");

        assertThat(jwtService.extractUsername(token)).isEqualTo("doc@hospital.com");
        assertThat(jwtService.<String>extractClaim(token, c -> c.get("profileId", String.class))).isEqualTo("DOC-001");
        assertThat(jwtService.<String>extractClaim(token, c -> c.get("profileType", String.class))).isEqualTo("DOCTOR");
    }

    @Test
    void validateToken_trueForMatchingEmail() {
        String token = jwtService.generateToken(organization());

        assertThat(jwtService.validateToken(token, "org@hospital.com")).isTrue();
    }

    @Test
    void validateToken_falseForDifferentEmail() {
        String token = jwtService.generateToken(organization());

        assertThat(jwtService.validateToken(token, "other@hospital.com")).isFalse();
    }

    @Test
    void expiredToken_isRejected() {
        JwtService expired = newJwtService(SECRET, -1_000L);
        String token = expired.generateToken(organization());

        assertThatThrownBy(() -> jwtService.validateToken(token, "org@hospital.com"))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        JwtService attacker = newJwtService("another-secret-key-with-at-least-256-bits-9876543210", 60_000L);
        String forged = attacker.generateToken(organization());

        assertThatThrownBy(() -> jwtService.extractUsername(forged))
                .isInstanceOf(SignatureException.class);
    }
}
