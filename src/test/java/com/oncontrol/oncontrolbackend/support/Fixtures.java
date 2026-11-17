package com.oncontrol.oncontrolbackend.support;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.profiles.domain.model.DoctorProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType;

/**
 * Shared, deterministic test data.
 * Ownership chain: organization(100) -> doctor(10, profile 1) -> patient(20, profile 2).
 */
public final class Fixtures {

    public static final Long ORG_ID = 100L;
    public static final Long DOCTOR_ID = 10L;
    public static final Long DOCTOR_PROFILE_ACCOUNT_ID = 1L;
    public static final Long PATIENT_ID = 20L;
    public static final Long PATIENT_PROFILE_ACCOUNT_ID = 2L;

    private Fixtures() {
    }

    public static User organization() {
        return User.builder()
                .id(ORG_ID)
                .email("org@hospital.com")
                .password("hashed")
                .organizationName("Hospital Central")
                .role(UserRole.ORGANIZATION)
                .isActive(true)
                .maxDoctors(10)
                .maxPatients(100)
                .build();
    }

    public static Profile doctorAccount() {
        return Profile.builder()
                .id(DOCTOR_PROFILE_ACCOUNT_ID)
                .profileId("DOC-001")
                .profileType(ProfileType.DOCTOR)
                .email("dr.garcia@hospital.com")
                .password("hashed")
                .firstName("Carlos")
                .lastName("Garcia")
                .user(organization())
                .isActive(true)
                .build();
    }

    public static Profile patientAccount() {
        return Profile.builder()
                .id(PATIENT_PROFILE_ACCOUNT_ID)
                .profileId("PAT-001")
                .profileType(ProfileType.PATIENT)
                .email("juan.perez@mail.com")
                .password("hashed")
                .firstName("Juan")
                .lastName("Perez")
                .user(organization())
                .isActive(true)
                .build();
    }

    public static DoctorProfile doctor() {
        return DoctorProfile.builder()
                .id(DOCTOR_ID)
                .profile(doctorAccount())
                .organization(organization())
                .specialization("Oncologia")
                .licenseNumber("CMP-123")
                .isAvailable(true)
                .build();
    }

    public static PatientProfile patient() {
        return patient(doctor());
    }

    public static PatientProfile patient(DoctorProfile doctor) {
        return PatientProfile.builder()
                .id(PATIENT_ID)
                .profile(patientAccount())
                .doctorProfile(doctor)
                .cancerType("Pulmon")
                .cancerStage("II")
                .build();
    }
}
