package com.oncontrol.oncontrolbackend.iam.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private final LoginRateLimiter limiter = new LoginRateLimiter();

    @Test
    void unknownClient_isNotBlocked() {
        assertThat(limiter.isBlocked("10.0.0.1")).isFalse();
    }

    @Test
    void blocksAfterFiveAttemptsInsideWindow() {
        for (int i = 0; i < 4; i++) {
            limiter.recordAttempt("10.0.0.1");
        }
        assertThat(limiter.isBlocked("10.0.0.1")).isFalse();

        limiter.recordAttempt("10.0.0.1");
        assertThat(limiter.isBlocked("10.0.0.1")).isTrue();
    }

    @Test
    void limitsArePerClient() {
        for (int i = 0; i < 5; i++) {
            limiter.recordAttempt("10.0.0.1");
        }
        assertThat(limiter.isBlocked("10.0.0.2")).isFalse();
    }

    @Test
    void resetClearsAttempts() {
        for (int i = 0; i < 5; i++) {
            limiter.recordAttempt("10.0.0.1");
        }
        limiter.reset("10.0.0.1");

        assertThat(limiter.isBlocked("10.0.0.1")).isFalse();
    }
}
