package com.oncontrol.oncontrolbackend.shared.application.port;

/**
 * Outbound port to the auth service (identity provider). Clinical owns all business data and
 * orchestrates every account-creating flow; the dependency is one-way clinical -> auth.
 */
public interface IdentityProvisioner {

    /** Returned by {@link #provision} when provisioning is disabled (nothing to compensate). */
    long NOT_PROVISIONED = -1L;

    /**
     * Creates the credentials of a new account in the auth service.
     *
     * @return the auth account id, or {@link #NOT_PROVISIONED} when provisioning is disabled
     * @throws IdentityConflictException    the email is already registered in auth (HTTP 409)
     * @throws IdentityUnavailableException auth is down, timed out or answered with an error
     */
    long provision(NewAccount account);

    /** Compensating action: removes an account (idempotent on the auth side). */
    void deprovision(long accountId);
}
