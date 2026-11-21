package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.infrastructure.security.AuthenticatedPrincipal;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.PrincipalRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the JWT claim contract shared with the auth service: a literal claim map
 * (exactly what auth issues) signed with the shared secret must become the expected principal.
 */
class ClaimsContractTest {

    private static final String SECRET = "test-secret-key-with-at-least-256-bits-0123456789";
    private static final String OTHER_SECRET = "another-secret-key-with-at-least-256-bits-9876543210";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 60_000L);
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(Map<String, Object> claims, String secret, long expiresInMs) {
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(key(secret))
                .compact();
    }

    private static Map<String, Object> doctorClaims(String issuer) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "doc@hospital.com");
        claims.put("iss", issuer);
        claims.put("role", "DOCTOR");
        claims.put("organizationId", 1);   // jjwt parses small numbers as Integer
        claims.put("profileId", 5);
        claims.put("doctorProfileId", 7);
        return claims;
    }

    @Test
    void doctorClaims_fromAuth_becomePrincipalWithLongIds() {
        AuthenticatedPrincipal principal = jwtService.parsePrincipal(
                sign(doctorClaims("oncontrol3d-auth"), SECRET, 60_000L));

        assertThat(principal).isEqualTo(new AuthenticatedPrincipal(
                PrincipalRole.DOCTOR, "doc@hospital.com", 1L, 5L, 7L, null));
    }

    @Test
    void patientClaims_carryPatientAndDoctorIds() {
        Map<String, Object> claims = doctorClaims("oncontrol3d-auth");
        claims.put("sub", "pat@mail.com");
        claims.put("role", "PATIENT");
        claims.put("profileId", 9);
        claims.put("patientProfileId", 12);

        AuthenticatedPrincipal principal = jwtService.parsePrincipal(sign(claims, SECRET, 60_000L));

        assertThat(principal).isEqualTo(new AuthenticatedPrincipal(
                PrincipalRole.PATIENT, "pat@mail.com", 1L, 9L, 7L, 12L));
    }

    @Test
    void organizationClaims_haveNullProfileIds() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "org@hospital.com");
        claims.put("iss", "oncontrol3d-auth");
        claims.put("role", "ORGANIZATION");
        claims.put("organizationId", 3);

        AuthenticatedPrincipal principal = jwtService.parsePrincipal(sign(claims, SECRET, 60_000L));

        assertThat(principal).isEqualTo(new AuthenticatedPrincipal(
                PrincipalRole.ORGANIZATION, "org@hospital.com", 3L, null, null, null));
    }

    @Test
    void clinicalIssuedToken_isParsedToo() {
        String token = jwtService.generateTokenForProfile(
                new com.oncontrol.oncontrolbackend.profiles.application.dto.ProfileIdentity(
                        com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType.DOCTOR,
                        "doc@hospital.com", 1L, 5L, 7L, null));

        assertThat(jwtService.parsePrincipal(token)).isEqualTo(new AuthenticatedPrincipal(
                PrincipalRole.DOCTOR, "doc@hospital.com", 1L, 5L, 7L, null));
    }

    @Test
    void unknownIssuer_isRejected() {
        String token = sign(doctorClaims("evil-issuer"), SECRET, 60_000L);

        assertThatThrownBy(() -> jwtService.parsePrincipal(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void missingOrUnknownRole_isRejected() {
        Map<String, Object> noRole = doctorClaims("oncontrol3d-auth");
        noRole.remove("role");
        Map<String, Object> legacyRole = doctorClaims("oncontrol3d-auth");
        legacyRole.put("role", "PROFILE");

        assertThatThrownBy(() -> jwtService.parsePrincipal(sign(noRole, SECRET, 60_000L)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.parsePrincipal(sign(legacyRole, SECRET, 60_000L)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void missingSubjectOrOrganization_isRejected() {
        Map<String, Object> noSub = doctorClaims("oncontrol3d-auth");
        noSub.remove("sub");
        Map<String, Object> noOrg = doctorClaims("oncontrol3d-auth");
        noOrg.remove("organizationId");

        assertThatThrownBy(() -> jwtService.parsePrincipal(sign(noSub, SECRET, 60_000L)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.parsePrincipal(sign(noOrg, SECRET, 60_000L)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredOrOtherSecret_isRejected() {
        String expired = sign(doctorClaims("oncontrol3d-auth"), SECRET, -1_000L);
        String foreign = sign(doctorClaims("oncontrol3d-auth"), OTHER_SECRET, 60_000L);

        assertThatThrownBy(() -> jwtService.parsePrincipal(expired)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.parsePrincipal(foreign)).isInstanceOf(JwtException.class);
    }
}
