package com.oncontrol.oncontrolbackend.iam.infrastructure.controller;

import com.oncontrol.oncontrolbackend.iam.application.dto.LoginRequest;
import com.oncontrol.oncontrolbackend.iam.application.dto.RegisterRequest;
import com.oncontrol.oncontrolbackend.iam.application.service.AuthService;
import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.LoginRateLimiter;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.JwtService;
import com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.ProfileIdentity;
import com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks the auth API contract consumed by the frontend:
 * status codes and the JSON fields "type", "token", "profile", "message".
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private ProfileService profileService;
    @Mock
    private JwtService jwtService;
    @Mock
    private LoginRateLimiter loginRateLimiter;

    @InjectMocks
    private AuthController controller;

    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static User organization() {
        return User.builder().id(1L).email("org@h.com").organizationName("Hospital")
                .role(UserRole.ORGANIZATION).build();
    }

    private String loginBody(String email, String password) throws Exception {
        return json.writeValueAsString(new LoginRequest(email, password));
    }

    @Test
    void registerOrganization_returns201WithToken() throws Exception {
        when(authService.registerOrganization(any())).thenReturn(organization());
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt");
        RegisterRequest request = RegisterRequest.builder().email("org@h.com").password("Secret123!")
                .organizationName("Hospital").country("Peru").city("Lima").build();

        mockMvc.perform(post("/api/auth/register/organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ORGANIZATION"))
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.email").value("org@h.com"));
    }

    @Test
    void registerOrganization_returns400OnDuplicatedEmail() throws Exception {
        when(authService.registerOrganization(any())).thenThrow(new IllegalArgumentException("Email already exists"));
        RegisterRequest request = RegisterRequest.builder().email("org@h.com").password("Secret123!")
                .organizationName("Hospital").country("Peru").city("Lima").build();

        mockMvc.perform(post("/api/auth/register/organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    void login_organization_returns200AndResetsLimiter() throws Exception {
        when(authService.login(any())).thenReturn(organization());
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("org@h.com", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORGANIZATION"))
                .andExpect(jsonPath("$.token").value("jwt"));

        verify(loginRateLimiter).reset("127.0.0.1");
    }

    @Test
    void login_doctorProfile_returnsProfileToken() throws Exception {
        when(authService.login(any())).thenThrow(new IllegalArgumentException("Invalid credentials"));
        DoctorProfileResponse doctor = DoctorProfileResponse.builder().email("doc@h.com").profileId("DOC-1").build();
        when(profileService.loginProfile("doc@h.com", "pw")).thenReturn(doctor);
        ProfileIdentity identity = new ProfileIdentity(ProfileType.DOCTOR, "doc@h.com", 1L, 5L, 7L, null);
        when(profileService.resolveIdentity("doc@h.com")).thenReturn(identity);
        when(jwtService.generateTokenForProfile(identity)).thenReturn("doc-jwt");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("doc@h.com", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("doc-jwt"))
                .andExpect(jsonPath("$.profile.profileId").value("DOC-1"));
    }

    @Test
    void login_patientProfile_returnsProfileToken() throws Exception {
        when(authService.login(any())).thenThrow(new IllegalArgumentException("Invalid credentials"));
        PatientProfileResponse patient = PatientProfileResponse.builder().email("pat@h.com").profileId("PAT-1").build();
        when(profileService.loginProfile("pat@h.com", "pw")).thenReturn(patient);
        ProfileIdentity identity = new ProfileIdentity(ProfileType.PATIENT, "pat@h.com", 1L, 9L, 7L, 12L);
        when(profileService.resolveIdentity("pat@h.com")).thenReturn(identity);
        when(jwtService.generateTokenForProfile(identity)).thenReturn("pat-jwt");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("pat@h.com", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("pat-jwt"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new IllegalArgumentException("Invalid credentials"));
        when(profileService.loginProfile(anyString(), anyString())).thenThrow(new IllegalArgumentException("nope"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("x@h.com", "bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        verify(loginRateLimiter, never()).reset(anyString());
    }

    @Test
    void login_blockedClient_returns429WithoutCheckingCredentials() throws Exception {
        when(loginRateLimiter.isBlocked("203.0.113.9")).thenReturn(true);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                        .content(loginBody("org@h.com", "pw")))
                .andExpect(status().isTooManyRequests());

        verify(authService, never()).login(any());
    }
}
