package com.oncontrol.oncontrolbackend.shared.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fails fast, before any bean is created, when a required secret is missing.
 *
 * <p>Why this is needed: Spring Boot leaves an unresolvable placeholder such as
 * {@code ${DB_PASSWORD}} as a literal string when binding {@code spring.datasource.*},
 * so the application would try to connect using the text "${DB_PASSWORD}" as the
 * password instead of refusing to start.
 *
 * <p>Registered in {@code META-INF/spring.factories}; runs after all config data
 * (application.properties, profiles, .env) has been loaded.
 */
public class RequiredSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Spring property -> environment variable that must provide it. */
    static final Map<String, String> REQUIRED_SECRETS = new LinkedHashMap<>();

    static {
        REQUIRED_SECRETS.put("spring.datasource.password", "DB_PASSWORD");
        REQUIRED_SECRETS.put("authorization.jwt.secret", "JWT_SECRET");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> missing = new ArrayList<>();
        REQUIRED_SECRETS.forEach((property, variable) -> {
            if (!hasValue(environment, property)) {
                missing.add(variable);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required secrets " + missing
                    + ": set them as environment variables (see .env.example). The service will not start without them.");
        }
    }

    private static boolean hasValue(ConfigurableEnvironment environment, String property) {
        try {
            String value = environment.getProperty(property);
            return value != null && !value.isBlank();
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
