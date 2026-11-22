package com.oncontrol.oncontrolbackend.shared.infrastructure.config;

import com.oncontrol.oncontrolbackend.iam.infrastructure.config.SecurityConfig;
import com.oncontrol.oncontrolbackend.iam.infrastructure.filter.JwtAuthenticationFilter;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.JwtService;
import com.oncontrol.oncontrolbackend.shared.infrastructure.controller.HealthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS is driven by {@code app.cors.allowed-origins} (env {@code APP_CORS_ALLOWED_ORIGINS}, fed on AKS
 * from the edge ConfigMap PUBLIC_ORIGIN): the configured origin is echoed, anything else gets no CORS headers.
 */
@SpringJUnitWebConfig(WebConfigCorsTest.TestConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=https://oc3d.example.test")
class WebConfigCorsTest {

    private static final String ALLOWED = "https://oc3d.example.test";
    private static final String EVIL = "https://evil.example.test";

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, WebConfig.class, HealthController.class})
    static class TestConfig {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(Mockito.mock(JwtService.class));
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void allowedOrigin_isEchoedWithCredentials() throws Exception {
        mockMvc.perform(get("/health").header(ORIGIN, ALLOWED))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void allowedOrigin_preflightSucceeds() throws Exception {
        mockMvc.perform(options("/health").header(ORIGIN, ALLOWED).header(ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
    }

    @Test
    void foreignOrigin_getsNoCorsHeader() throws Exception {
        mockMvc.perform(get("/health").header(ORIGIN, EVIL))
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void foreignOrigin_preflightIsRejected() throws Exception {
        mockMvc.perform(options("/health").header(ORIGIN, EVIL).header(ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void localhostDefault_isNotAllowedOnceTheOriginIsConfigured() throws Exception {
        mockMvc.perform(get("/health").header(ORIGIN, "http://localhost:3000"))
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
