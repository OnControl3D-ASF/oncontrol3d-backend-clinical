package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.profiles.application.dto.ProfileIdentity;
import com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    private static Number number(Claims claims, String name) {
        return claims.get(name, Number.class);
    }

    private Claims claimsOf(String token) {
        return jwtService.extractClaim(token, c -> c);
    }

    @Test
    void generateToken_organization_emitsContractClaims() {
        String token = jwtService.generateToken(organization());

        Claims claims = claimsOf(token);
        assertThat(claims.getSubject()).isEqualTo("org@hospital.com");
        assertThat(claims.getIssuer()).isEqualTo("oncontrol3d-clinical");
        assertThat(claims.get("role", String.class)).isEqualTo("ORGANIZATION");
        assertThat(number(claims, "organizationId").longValue()).isEqualTo(7L);
        assertThat(claims.get("profileId")).isNull();
        assertThat(claims.get("doctorProfileId")).isNull();
        assertThat(claims.get("patientProfileId")).isNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void generateTokenForProfile_doctor_emitsRoleAndNumericIds() {
        ProfileIdentity doctor = new ProfileIdentity(ProfileType.DOCTOR, "doc@hospital.com", 1L, 5L, 7L, null);

        Claims claims = claimsOf(jwtService.generateTokenForProfile(doctor));

        assertThat(claims.getSubject()).isEqualTo("doc@hospital.com");
        assertThat(claims.getIssuer()).isEqualTo("oncontrol3d-clinical");
        assertThat(claims.get("role", String.class)).isEqualTo("DOCTOR");
        assertThat(claims.get("organizationId")).isInstanceOf(Number.class);
        assertThat(number(claims, "organizationId").longValue()).isEqualTo(1L);
        assertThat(number(claims, "profileId").longValue()).isEqualTo(5L);
        assertThat(number(claims, "doctorProfileId").longValue()).isEqualTo(7L);
        assertThat(claims.get("patientProfileId")).isNull();
    }

    @Test
    void generateTokenForProfile_patient_emitsPatientAndDoctorIds() {
        ProfileIdentity patient = new ProfileIdentity(ProfileType.PATIENT, "pat@mail.com", 1L, 9L, 7L, 12L);

        Claims claims = claimsOf(jwtService.generateTokenForProfile(patient));

        assertThat(claims.get("role", String.class)).isEqualTo("PATIENT");
        assertThat(number(claims, "profileId").longValue()).isEqualTo(9L);
        assertThat(number(claims, "doctorProfileId").longValue()).isEqualTo(7L);
        assertThat(number(claims, "patientProfileId").longValue()).isEqualTo(12L);
        assertThat(number(claims, "organizationId").longValue()).isEqualTo(1L);
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

    @Test
    void validateSecret_acceptsKeysOfAtLeast256Bits() {
        assertThatCode(() -> jwtService.validateSecret()).doesNotThrowAnyException();
    }

    @Test
    void validateSecret_rejectsBlankSecret() {
        assertThatThrownBy(() -> newJwtService("   ", 60_000L).validateSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is required");
    }

    @Test
    void validateSecret_rejectsShortSecret() {
        assertThatThrownBy(() -> newJwtService("too-short", 60_000L).validateSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
