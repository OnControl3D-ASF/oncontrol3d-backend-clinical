package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.profiles.domain.model.DoctorProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * Ownership (IDOR) rules: org -> doctor -> patient.
 * Fixture: organization 100 owns doctor 10 (profile 1), who owns patient 20 (profile 2).
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private AuthorizationService authorizationService;

    private User organization;
    private User otherOrganization;
    private Profile doctorAccount;
    private Profile patientAccount;
    private Profile strangerDoctorAccount;
    private DoctorProfile doctor;
    private PatientProfile patient;

    @BeforeEach
    void setUp() {
        organization = User.builder().id(100L).email("org@h.com").build();
        otherOrganization = User.builder().id(200L).email("other@h.com").build();
        doctorAccount = Profile.builder().id(1L).profileType(ProfileType.DOCTOR).user(organization).build();
        patientAccount = Profile.builder().id(2L).profileType(ProfileType.PATIENT).user(organization).build();
        strangerDoctorAccount = Profile.builder().id(3L).profileType(ProfileType.DOCTOR).build();
        doctor = DoctorProfile.builder().id(10L).profile(doctorAccount).organization(organization).build();
        patient = PatientProfile.builder().id(20L).profile(patientAccount).doctorProfile(doctor).build();

        lenient().when(doctorProfileRepository.findById(10L)).thenReturn(Optional.of(doctor));
        lenient().when(doctorProfileRepository.findByProfileId(1L)).thenReturn(Optional.of(doctor));
        lenient().when(doctorProfileRepository.findByProfileId(3L))
                .thenReturn(Optional.of(DoctorProfile.builder().id(30L).build()));
        lenient().when(patientProfileRepository.findById(20L)).thenReturn(Optional.of(patient));
        lenient().when(patientProfileRepository.findByProfileId(2L)).thenReturn(Optional.of(patient));
        lenient().when(profileRepository.findById(1L)).thenReturn(Optional.of(doctorAccount));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void unauthenticatedRequest_isDenied() {
        assertThatThrownBy(() -> authorizationService.requireOrganization(100L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not authenticated");
    }

    @Test
    void requireOrganization_allowsOnlyItself() {
        authenticateAs(organization);
        assertThatCode(() -> authorizationService.requireOrganization(100L)).doesNotThrowAnyException();

        authenticateAs(otherOrganization);
        assertThatThrownBy(() -> authorizationService.requireOrganization(100L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireDoctor_allowsDoctorItselfAndOwningOrganization() {
        authenticateAs(doctorAccount);
        assertThatCode(() -> authorizationService.requireDoctor(10L)).doesNotThrowAnyException();

        authenticateAs(organization);
        assertThatCode(() -> authorizationService.requireDoctor(10L)).doesNotThrowAnyException();
    }

    @Test
    void requireDoctor_deniesOtherDoctorsAndOrganizations() {
        authenticateAs(strangerDoctorAccount);
        assertThatThrownBy(() -> authorizationService.requireDoctor(10L)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(otherOrganization);
        assertThatThrownBy(() -> authorizationService.requireDoctor(10L)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(patientAccount);
        assertThatThrownBy(() -> authorizationService.requireDoctor(10L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireDoctor_deniesUnknownDoctor() {
        authenticateAs(organization);
        assertThatThrownBy(() -> authorizationService.requireDoctor(99L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Doctor not found");
    }

    @Test
    void requirePatientAccess_allowsPatientTheirDoctorAndOrganization() {
        authenticateAs(patientAccount);
        assertThatCode(() -> authorizationService.requirePatientAccess(20L)).doesNotThrowAnyException();

        authenticateAs(doctorAccount);
        assertThatCode(() -> authorizationService.requirePatientAccess(20L)).doesNotThrowAnyException();

        authenticateAs(organization);
        assertThatCode(() -> authorizationService.requirePatientAccess(20L)).doesNotThrowAnyException();
    }

    @Test
    void requirePatientAccess_deniesStrangers() {
        authenticateAs(strangerDoctorAccount);
        assertThatThrownBy(() -> authorizationService.requirePatientAccess(20L)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(otherOrganization);
        assertThatThrownBy(() -> authorizationService.requirePatientAccess(20L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requirePatientAccess_deniesUnknownPatient() {
        authenticateAs(organization);
        assertThatThrownBy(() -> authorizationService.requirePatientAccess(99L))
                .hasMessage("Patient not found");
    }

    @Test
    void requireProfileOwnership_allowsOwnerProfileAndOwningOrganization() {
        authenticateAs(doctorAccount);
        assertThatCode(() -> authorizationService.requireProfileOwnership(null, 1L)).doesNotThrowAnyException();

        authenticateAs(organization);
        assertThatCode(() -> authorizationService.requireProfileOwnership(null, 1L)).doesNotThrowAnyException();
    }

    @Test
    void requireProfileOwnership_deniesNonOwners() {
        authenticateAs(strangerDoctorAccount);
        assertThatThrownBy(() -> authorizationService.requireProfileOwnership(1L, 2L))
                .isInstanceOf(AccessDeniedException.class);

        authenticateAs(otherOrganization);
        assertThatThrownBy(() -> authorizationService.requireProfileOwnership(1L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
