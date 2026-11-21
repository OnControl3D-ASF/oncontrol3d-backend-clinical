package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.AuthenticatedPrincipal;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.PrincipalRole;
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
 * Ownership (IDOR) rules evaluated on token claims: org -> doctor -> patient.
 * Fixture: organization 100 owns doctor 10 (Profile PK 1), who owns patient 20 (Profile PK 2).
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

    private AuthenticatedPrincipal organization;
    private AuthenticatedPrincipal otherOrganization;
    private AuthenticatedPrincipal doctorPrincipal;
    private AuthenticatedPrincipal patientPrincipal;
    private AuthenticatedPrincipal strangerDoctor;
    private AuthenticatedPrincipal strangerPatient;
    private Profile doctorAccount;

    @BeforeEach
    void setUp() {
        User org = User.builder().id(100L).email("org@h.com").build();
        doctorAccount = Profile.builder().id(1L).profileType(ProfileType.DOCTOR).user(org).build();
        Profile patientAccount = Profile.builder().id(2L).profileType(ProfileType.PATIENT).user(org).build();
        DoctorProfile doctor = DoctorProfile.builder().id(10L).profile(doctorAccount).organization(org).build();
        PatientProfile patient = PatientProfile.builder().id(20L).profile(patientAccount).doctorProfile(doctor).build();

        organization = new AuthenticatedPrincipal(PrincipalRole.ORGANIZATION, "org@h.com", 100L, null, null, null);
        otherOrganization = new AuthenticatedPrincipal(PrincipalRole.ORGANIZATION, "other@h.com", 200L, null, null, null);
        doctorPrincipal = new AuthenticatedPrincipal(PrincipalRole.DOCTOR, "doc@h.com", 100L, 1L, 10L, null);
        patientPrincipal = new AuthenticatedPrincipal(PrincipalRole.PATIENT, "pat@h.com", 100L, 2L, 10L, 20L);
        strangerDoctor = new AuthenticatedPrincipal(PrincipalRole.DOCTOR, "x@h.com", 200L, 3L, 30L, null);
        strangerPatient = new AuthenticatedPrincipal(PrincipalRole.PATIENT, "y@h.com", 100L, 4L, 10L, 21L);

        lenient().when(doctorProfileRepository.findById(10L)).thenReturn(Optional.of(doctor));
        lenient().when(patientProfileRepository.findById(20L)).thenReturn(Optional.of(patient));
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
    void nonClaimsPrincipal_isDenied() {
        authenticateAs("anonymousUser");
        assertThatThrownBy(() -> authorizationService.requireOrganization(100L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireOrganization_allowsOnlyItself() {
        authenticateAs(organization);
        assertThatCode(() -> authorizationService.requireOrganization(100L)).doesNotThrowAnyException();

        authenticateAs(otherOrganization);
        assertThatThrownBy(() -> authorizationService.requireOrganization(100L))
                .isInstanceOf(AccessDeniedException.class);

        authenticateAs(doctorPrincipal);
        assertThatThrownBy(() -> authorizationService.requireOrganization(100L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireDoctor_allowsDoctorItselfAndOwningOrganization() {
        authenticateAs(doctorPrincipal);
        assertThatCode(() -> authorizationService.requireDoctor(10L)).doesNotThrowAnyException();

        authenticateAs(organization);
        assertThatCode(() -> authorizationService.requireDoctor(10L)).doesNotThrowAnyException();
    }

    @Test
    void requireDoctor_deniesOtherDoctorsOrganizationsAndPatients() {
        authenticateAs(strangerDoctor);
        assertThatThrownBy(() -> authorizationService.requireDoctor(10L)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(otherOrganization);
        assertThatThrownBy(() -> authorizationService.requireDoctor(10L)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(patientPrincipal);
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
        authenticateAs(patientPrincipal);
        assertThatCode(() -> authorizationService.requirePatientAccess(20L)).doesNotThrowAnyException();

        authenticateAs(doctorPrincipal);
        assertThatCode(() -> authorizationService.requirePatientAccess(20L)).doesNotThrowAnyException();

        authenticateAs(organization);
        assertThatCode(() -> authorizationService.requirePatientAccess(20L)).doesNotThrowAnyException();
    }

    @Test
    void requirePatientAccess_deniesStrangers() {
        authenticateAs(strangerDoctor);
        assertThatThrownBy(() -> authorizationService.requirePatientAccess(20L)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(otherOrganization);
        assertThatThrownBy(() -> authorizationService.requirePatientAccess(20L)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(strangerPatient);
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
        authenticateAs(doctorPrincipal);
        assertThatCode(() -> authorizationService.requireProfileOwnership(null, 1L)).doesNotThrowAnyException();

        authenticateAs(organization);
        assertThatCode(() -> authorizationService.requireProfileOwnership(null, 1L)).doesNotThrowAnyException();
    }

    @Test
    void requireProfileOwnership_comparesClaimProfileIdWithProfilePk() {
        authenticateAs(doctorPrincipal);   // claim profileId = 1
        assertThatThrownBy(() -> authorizationService.requireProfileOwnership(6L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireProfileOwnership_deniesNonOwners() {
        authenticateAs(strangerDoctor);
        assertThatThrownBy(() -> authorizationService.requireProfileOwnership(1L, 2L))
                .isInstanceOf(AccessDeniedException.class);

        authenticateAs(otherOrganization);
        assertThatThrownBy(() -> authorizationService.requireProfileOwnership(1L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
