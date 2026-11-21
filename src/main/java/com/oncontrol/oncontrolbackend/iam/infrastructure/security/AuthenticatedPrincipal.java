package com.oncontrol.oncontrolbackend.iam.infrastructure.security;

/**
 * Authenticated caller built from JWT claims only (no database lookup).
 * Id spaces: organizationId = organization (users) PK; profileId = Profile PK;
 * doctorProfileId = DoctorProfile.id; patientProfileId = PatientProfile.id.
 * Profile ids are null for ORGANIZATION tokens.
 */
public record AuthenticatedPrincipal(
        PrincipalRole role,
        String email,
        Long organizationId,
        Long profileId,
        Long doctorProfileId,
        Long patientProfileId) {
}
