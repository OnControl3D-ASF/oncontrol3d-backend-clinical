package com.oncontrol.oncontrolbackend.bdd;

import com.oncontrol.oncontrolbackend.support.PostgresContainerConfig;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application context (real security filter chain, JPA and PostgreSQL in Testcontainers).
 * Requests go through MockMvc, so they exercise the same filters as production.
 */
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(PostgresContainerConfig.class)
public class CucumberSpringConfiguration {
}
