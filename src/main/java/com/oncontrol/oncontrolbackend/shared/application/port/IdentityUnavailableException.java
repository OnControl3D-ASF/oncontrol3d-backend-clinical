package com.oncontrol.oncontrolbackend.shared.application.port;

/** The auth service is down, timed out or rejected the request; the caller must answer 503. */
public class IdentityUnavailableException extends RuntimeException {

    public IdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
