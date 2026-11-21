package com.oncontrol.oncontrolbackend.profiles.infrastructure.controller;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.AuthenticatedPrincipal;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The session profile: the legacy login body minus the token, resolved from the token claims
 * (tokens from clinical or from the auth service). The frontend logs in against auth and then
 * calls this endpoint to build its user object.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Session", description = "Authenticated user profile")
public class MeController {

    private final UserRepository userRepository;
    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Current user", description = "Organization, doctor or patient profile of the bearer token")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(switch (principal.role()) {
                case ORGANIZATION -> organizationBody(principal.organizationId());
                case DOCTOR -> Map.of("profile", profileService.getDoctorProfileById(principal.doctorProfileId()));
                case PATIENT -> Map.of("profile", profileService.getPatientProfileById(principal.patientProfileId()));
            });
        } catch (NoSuchElementException | org.springframework.security.access.AccessDeniedException e) {
            return notFound();
        } catch (RuntimeException e) {
            // ProfileService signals a missing profile with RuntimeException("... not found")
            if (e.getMessage() != null && e.getMessage().endsWith("not found")) {
                return notFound();
            }
            throw e;
        }
    }

    private Map<String, Object> organizationBody(Long organizationId) {
        User organization = userRepository.findById(organizationId)
                .orElseThrow(() -> new NoSuchElementException("Organization not found"));
        Map<String, Object> body = new HashMap<>();
        body.put("type", "ORGANIZATION");
        body.put("id", organization.getId());
        body.put("email", organization.getEmail());
        body.put("organizationName", organization.getOrganizationName());
        body.put("country", organization.getCountry());
        body.put("city", organization.getCity());
        body.put("role", organization.getRole());
        return body;
    }

    private static ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Account not found"));
    }
}
