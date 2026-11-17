package com.oncontrol.oncontrolbackend.shared.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredSecretsEnvironmentPostProcessorTest {

    private final RequiredSecretsEnvironmentPostProcessor processor = new RequiredSecretsEnvironmentPostProcessor();

    @Test
    void startsWhenAllSecretsAreProvided() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_PASSWORD", "db-secret")
                .withProperty("JWT_SECRET", "jwt-secret")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}")
                .withProperty("authorization.jwt.secret", "${JWT_SECRET}");

        assertThatCode(() -> processor.postProcessEnvironment(env, null)).doesNotThrowAnyException();
    }

    @Test
    void failsWhenPlaceholdersCannotBeResolved() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "${DB_PASSWORD}")
                .withProperty("authorization.jwt.secret", "${JWT_SECRET}");

        assertThatThrownBy(() -> processor.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[DB_PASSWORD, JWT_SECRET]");
    }

    @Test
    void failsWhenASecretIsBlank() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "db-secret")
                .withProperty("authorization.jwt.secret", "  ");

        assertThatThrownBy(() -> processor.postProcessEnvironment(env, null))
                .hasMessageContaining("[JWT_SECRET]");
    }
}
