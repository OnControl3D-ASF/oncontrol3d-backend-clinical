package com.oncontrol.oncontrolbackend.iam.infrastructure.controller;

import com.oncontrol.oncontrolbackend.iam.application.dto.LoginRequest;
import com.oncontrol.oncontrolbackend.iam.application.dto.RegisterRequest;
import com.oncontrol.oncontrolbackend.iam.application.service.AuthService;
import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.LoginRateLimiter;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.JwtService;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AuthController handles authentication for Organizations and Profiles
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management endpoints")
public class AuthController {

    private final AuthService authService;
    private final ProfileService profileService;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;

    /** Client key for rate limiting: first X-Forwarded-For hop (Render proxy) or the socket address. */
    private String clientKey(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    @PostMapping("/register/organization")
    @Operation(summary = "Register a new organization", description = "Register a new organization (company)")
    public ResponseEntity<?> registerOrganization(@Valid @RequestBody RegisterRequest request) {
        try {
            User organization = authService.registerOrganization(request);
            String token = jwtService.generateToken(organization);
            
            Map<String, Object> response = new HashMap<>();
            response.put("type", "ORGANIZATION"); // Frontend needs this to identify user type
            response.put("id", organization.getId());
            response.put("email", organization.getEmail());
            response.put("organizationName", organization.getOrganizationName());
            response.put("country", organization.getCountry());
            response.put("city", organization.getCity());
            response.put("role", organization.getRole());
            response.put("token", token);
            response.put("message", "Organization registered successfully");
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Login for organizations and profiles (doctors/patients)")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Brute-force protection: 5 attempts per minute per client IP.
        String client = clientKey(http);
        if (loginRateLimiter.isBlocked(client)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Demasiados intentos. Espera un minuto e inténtalo de nuevo.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
        }
        loginRateLimiter.recordAttempt(client);

        try {
            // Try organization login first
            Object result = authService.login(request);
            
            if (result instanceof User) {
                User organization = (User) result;
                String token = jwtService.generateToken(organization);
                
                Map<String, Object> response = new HashMap<>();
                response.put("id", organization.getId());
                response.put("email", organization.getEmail());
                response.put("organizationName", organization.getOrganizationName());
                response.put("country", organization.getCountry());
                response.put("city", organization.getCity());
                response.put("role", organization.getRole());
                response.put("token", token);
                response.put("type", "ORGANIZATION");
                response.put("message", "Login successful");

                loginRateLimiter.reset(client);
                return ResponseEntity.ok(response);
            }
        } catch (IllegalArgumentException e) {
            // Try profile login
            try {
                Object profile = profileService.loginProfile(request.getEmail(), request.getPassword());
                
                // Generate JWT token for the profile
                String token = null;
                if (profile instanceof com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse) {
                    com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse doctorProfile = 
                        (com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse) profile;
                    token = jwtService.generateTokenForProfile(
                        doctorProfile.getEmail(), 
                        doctorProfile.getProfileId(), 
                        "DOCTOR"
                    );
                } else if (profile instanceof com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse) {
                    com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse patientProfile = 
                        (com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse) profile;
                    token = jwtService.generateTokenForProfile(
                        patientProfile.getEmail(), 
                        patientProfile.getProfileId(), 
                        "PATIENT"
                    );
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("profile", profile);
                response.put("token", token);
                response.put("message", "Login successful");

                loginRateLimiter.reset(client);
                return ResponseEntity.ok(response);
            } catch (IllegalArgumentException e2) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "Invalid credentials");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
        }
        
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", "Invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
}
