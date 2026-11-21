package com.oncontrol.oncontrolbackend.shared.application.port;

/**
 * Credentials and claim ids of an account to create in the auth service.
 * role is one of ORGANIZATION | DOCTOR | PATIENT. Profile ids are null for organizations.
 */
public record NewAccount(
        String email,
        String password,
        String role,
        Long organizationId,
        Long profileId,
        Long doctorProfileId,
        Long patientProfileId) {
}
