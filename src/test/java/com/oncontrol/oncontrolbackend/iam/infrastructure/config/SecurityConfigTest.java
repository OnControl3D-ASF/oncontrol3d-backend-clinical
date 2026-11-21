package com.oncontrol.oncontrolbackend.iam.infrastructure.config;

import com.oncontrol.oncontrolbackend.iam.infrastructure.filter.JwtAuthenticationFilter;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.AuthenticatedPrincipal;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.PrincipalRole;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.JwtService;
import com.oncontrol.oncontrolbackend.shared.infrastructure.controller.HealthController;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security wiring: stateless filter chain, no UserDetailsService, 401 for unauthenticated requests. */
@SpringJUnitWebConfig(SecurityConfigTest.TestConfig.class)
class SecurityConfigTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, HealthController.class})
    static class TestConfig {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(JWT_SERVICE);
        }
    }

    /** Plain static mock: a mock registered as a bean would be field-injected with unresolved @Value placeholders. */
    private static final JwtService JWT_SERVICE = Mockito.mock(JwtService.class);

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(JWT_SERVICE);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void publicRoute_isReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void protectedRoute_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/symptoms/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoute_withInvalidToken_returns401() throws Exception {
        when(JWT_SERVICE.parsePrincipal("bad")).thenThrow(new JwtException("bad"));

        mockMvc.perform(get("/api/symptoms/anything").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoute_withValidToken_passesSecurity() throws Exception {
        when(JWT_SERVICE.parsePrincipal("good")).thenReturn(
                new AuthenticatedPrincipal(PrincipalRole.DOCTOR, "doc@h.com", 1L, 5L, 7L, null));

        // No controller maps this path: reaching 404 proves authentication succeeded (not 401/403).
        mockMvc.perform(get("/api/symptoms/anything").header("Authorization", "Bearer good"))
                .andExpect(status().isNotFound());
    }
}
