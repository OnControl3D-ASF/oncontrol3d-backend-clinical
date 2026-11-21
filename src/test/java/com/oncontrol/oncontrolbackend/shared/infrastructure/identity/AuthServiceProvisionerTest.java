package com.oncontrol.oncontrolbackend.shared.infrastructure.identity;

import com.oncontrol.oncontrolbackend.shared.application.port.IdentityConflictException;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthServiceProvisionerTest {

    private static final String BASE = "http://auth.test:8082";
    private static final String TOKEN = "internal-token-0123456789-0123456789-abc";

    private MockRestServiceServer server;
    private AuthServiceProvisioner provisioner;

    private static NewAccount doctor() {
        return new NewAccount("doc@h.com", "Secret123!", "DOCTOR", 1L, 5L, 7L, null);
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        provisioner = new AuthServiceProvisioner(builder.build(), TOKEN);
    }

    @Test
    void provision_postsAccountWithInternalTokenAndReturnsAccountId() {
        server.expect(requestTo(BASE + "/internal/accounts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(jsonPath("$.email").value("doc@h.com"))
                .andExpect(jsonPath("$.password").value("Secret123!"))
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.organizationId").value(1))
                .andExpect(jsonPath("$.profileId").value(5))
                .andExpect(jsonPath("$.doctorProfileId").value(7))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accountId\":42}"));

        long accountId = provisioner.provision(doctor());

        assertThat(accountId).isEqualTo(42L);
        server.verify();
    }

    @Test
    void provision_organizationOmitsProfileIds() {
        server.expect(requestTo(BASE + "/internal/accounts"))
                .andExpect(jsonPath("$.role").value("ORGANIZATION"))
                .andExpect(jsonPath("$.profileId").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accountId\":9}"));

        long accountId = provisioner.provision(new NewAccount("org@h.com", "Secret123!", "ORGANIZATION", 3L, null, null, null));

        assertThat(accountId).isEqualTo(9L);
    }

    @Test
    void provision_conflictBecomesIdentityConflictException() {
        server.expect(requestTo(BASE + "/internal/accounts")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> provisioner.provision(doctor())).isInstanceOf(IdentityConflictException.class);
    }

    @Test
    void provision_serverErrorBecomesIdentityUnavailableAndIsNotRetried() {
        // MockRestServiceServer expects exactly one request: a retry would fail the verify.
        server.expect(requestTo(BASE + "/internal/accounts")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> provisioner.provision(doctor())).isInstanceOf(IdentityUnavailableException.class);
        server.verify();
    }

    @Test
    void provision_timeoutBecomesIdentityUnavailable() {
        server.expect(requestTo(BASE + "/internal/accounts"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> provisioner.provision(doctor())).isInstanceOf(IdentityUnavailableException.class);
    }

    @Test
    void provision_rejectedTokenBecomesIdentityUnavailable() {
        server.expect(requestTo(BASE + "/internal/accounts")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provisioner.provision(doctor())).isInstanceOf(IdentityUnavailableException.class);
    }

    @Test
    void deprovision_sendsDeleteWithInternalToken() {
        server.expect(requestTo(BASE + "/internal/accounts/42"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withNoContent());

        provisioner.deprovision(42L);

        server.verify();
    }

    @Test
    void deprovision_failureBecomesIdentityUnavailable() {
        server.expect(requestTo(BASE + "/internal/accounts/42")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> provisioner.deprovision(42L)).isInstanceOf(IdentityUnavailableException.class);
    }

    @Test
    void blankInternalToken_failsFast() {
        assertThatThrownBy(() -> new AuthServiceProvisioner(RestClient.create(BASE), "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_INTERNAL_TOKEN");
    }
}
