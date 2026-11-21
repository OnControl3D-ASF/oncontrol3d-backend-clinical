package com.oncontrol.oncontrolbackend.shared.infrastructure.identity;

import com.oncontrol.oncontrolbackend.shared.application.port.IdentityConflictException;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityProvisioner;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter to the auth service internal API (/internal/accounts), authenticated with the
 * static X-Internal-Token. connect 2s / read 5s. POST is never retried (not idempotent).
 */
@Component
@ConditionalOnProperty(name = "app.auth.provisioning.enabled", havingValue = "true")
@Slf4j
public class AuthServiceProvisioner implements IdentityProvisioner {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public AuthServiceProvisioner(
            @Value("${app.auth.service-url}") String authServiceUrl,
            @Value("${app.auth.internal-token:}") String internalToken) {
        this(RestClient.builder()
                .baseUrl(authServiceUrl)
                .requestFactory(timeoutFactory())
                .build(), internalToken);
    }

    AuthServiceProvisioner(RestClient restClient, String internalToken) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("AUTH_INTERNAL_TOKEN is required when AUTH_PROVISIONING_ENABLED=true");
        }
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    private record ProvisionResponse(long accountId) {
    }

    @Override
    public long provision(NewAccount account) {
        try {
            ProvisionResponse response = restClient.post()
                    .uri("/internal/accounts")
                    .header(TOKEN_HEADER, internalToken)
                    .body(account)
                    .retrieve()
                    .onStatus(AuthServiceProvisioner::isConflict, (request, res) -> {
                        throw new IdentityConflictException("Email already registered in auth service");
                    })
                    .body(ProvisionResponse.class);
            if (response == null) {
                throw new IdentityUnavailableException("Auth service returned an empty response", null);
            }
            return response.accountId();
        } catch (IdentityConflictException | IdentityUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Auth service provisioning failed: {}", e.getMessage());
            throw new IdentityUnavailableException("Auth service unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public void deprovision(long accountId) {
        try {
            restClient.delete()
                    .uri("/internal/accounts/{id}", accountId)
                    .header(TOKEN_HEADER, internalToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IdentityUnavailableException("Auth service unavailable: " + e.getMessage(), e);
        }
    }

    private static boolean isConflict(HttpStatusCode status) {
        return status.value() == 409;
    }
}
