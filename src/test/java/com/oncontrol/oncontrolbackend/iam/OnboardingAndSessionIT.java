package com.oncontrol.oncontrolbackend.iam;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.JwtService;
import com.oncontrol.oncontrolbackend.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full wiring (real security chain + PostgreSQL, provisioning flag off): public onboarding then /api/me. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(PostgresContainerConfig.class)
class OnboardingAndSessionIT {

    private static final String BODY = """
            {"email":"onboard@hospital.com","password":"Secret123!","organizationName":"Hospital Onboard",
             "country":"Peru","city":"Lima"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    void publicOnboarding_thenMeWithTokenOfEitherIssuer() throws Exception {
        mockMvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByEmail("onboard@hospital.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("Secret123!");   // flag off: local bcrypt kept

        String token = jwtService.generateToken(saved);
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORGANIZATION"))
                .andExpect(jsonPath("$.organizationName").value("Hospital Onboard"));

        mockMvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
