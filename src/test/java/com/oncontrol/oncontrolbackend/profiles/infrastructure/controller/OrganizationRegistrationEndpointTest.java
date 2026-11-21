package com.oncontrol.oncontrolbackend.profiles.infrastructure.controller;

import com.oncontrol.oncontrolbackend.iam.application.service.OrganizationOnboardingService;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public POST /api/organizations: onboarding moved from /api/auth/register/organization to clinical. */
@ExtendWith(MockitoExtension.class)
class OrganizationRegistrationEndpointTest {

    private static final String BODY = """
            {"email":"org@hospital.com","password":"Secret123!","organizationName":"Hospital Central",
             "country":"Peru","city":"Lima"}
            """;

    @Mock
    private ProfileService profileService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private OrganizationOnboardingService onboardingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OrganizationController(profileService, authorizationService, onboardingService)).build();
    }

    @Test
    void register_returns201WithOrganizationBodyAndNoTokenOrPassword() throws Exception {
        when(onboardingService.register(any())).thenReturn(Fixtures.organization());

        mockMvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ORGANIZATION"))
                .andExpect(jsonPath("$.id").value(Fixtures.ORG_ID))
                .andExpect(jsonPath("$.email").value("org@hospital.com"))
                .andExpect(jsonPath("$.organizationName").value("Hospital Central"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_duplicatedEmail_returns400() throws Exception {
        when(onboardingService.register(any())).thenThrow(new IllegalArgumentException("Email already exists"));

        mockMvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    void register_authDown_returns503() throws Exception {
        when(onboardingService.register(any())).thenThrow(new IdentityUnavailableException("down", null));

        mockMvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Identity service unavailable, try again later"));
    }

    @Test
    void register_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }
}
