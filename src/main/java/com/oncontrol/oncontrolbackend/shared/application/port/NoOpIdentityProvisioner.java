package com.oncontrol.oncontrolbackend.shared.application.port;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Active while AUTH_PROVISIONING_ENABLED is false (default): clinical keeps its own
 * credentials and does not call the auth service.
 */
@Component
@ConditionalOnProperty(name = "app.auth.provisioning.enabled", havingValue = "false", matchIfMissing = true)
class NoOpIdentityProvisioner implements IdentityProvisioner {

    @Override
    public long provision(NewAccount account) {
        return NOT_PROVISIONED;
    }

    @Override
    public void deprovision(long accountId) {
        // nothing was provisioned
    }
}
