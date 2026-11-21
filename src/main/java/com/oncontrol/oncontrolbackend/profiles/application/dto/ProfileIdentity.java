package com.oncontrol.oncontrolbackend.profiles.application.dto;

import com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType;

/**
 * Identity ids of a doctor/patient profile, as carried in the JWT claim contract.
 * <ul>
 *   <li>organizationId: organization (users) PK</li>
 *   <li>profileId: Profile PK</li>
 *   <li>doctorProfileId: DoctorProfile.id (the doctor itself, or the patient's doctor)</li>
 *   <li>patientProfileId: PatientProfile.id (patients only)</li>
 * </ul>
 */
public record ProfileIdentity(
        ProfileType type,
        String email,
        Long organizationId,
        Long profileId,
        Long doctorProfileId,
        Long patientProfileId) {
}
