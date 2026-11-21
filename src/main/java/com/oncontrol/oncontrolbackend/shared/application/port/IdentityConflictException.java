package com.oncontrol.oncontrolbackend.shared.application.port;

/** The auth service already has an account with that email. */
public class IdentityConflictException extends RuntimeException {

    public IdentityConflictException(String message) {
        super(message);
    }
}
