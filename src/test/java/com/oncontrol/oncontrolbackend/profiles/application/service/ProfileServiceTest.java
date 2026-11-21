package com.oncontrol.oncontrolbackend.profiles.application.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.application.dto.CreateDoctorRequest;
import com.oncontrol.oncontrolbackend.profiles.application.dto.CreatePatientRequest;
import com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.ProfileIdentity;
import com.oncontrol.oncontrolbackend.profiles.application.dto.UpdateDoctorRequest;
import com.oncontrol.oncontrolbackend.profiles.application.dto.UpdatePatientRequest;
import com.oncontrol.oncontrolbackend.profiles.domain.model.DoctorProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityConflictException;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;
import com.oncontrol.oncontrolbackend.shared.application.service.AccountProvisioningService;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccountProvisioningService accountProvisioning;

    @InjectMocks
    private ProfileService service;

    private static CreateDoctorRequest doctorRequest() {
        return CreateDoctorRequest.builder()
                .email("new.doc@hospital.com").password("Secret123!")
                .firstName("Ana").lastName("Rojas")
                .specialization("Oncologia").licenseNumber("CMP-999")
                .build();
    }

    private static CreatePatientRequest patientRequest() {
        return CreatePatientRequest.builder()
                .email("new.pat@mail.com").password("Secret123!")
                .firstName("Luis").lastName("Soto")
                .cancerType("Pulmon").cancerStage("III")
                .diagnosisDate(LocalDate.of(2026, 5, 1))
                .build();
    }

    private void stubProfileSaves() {
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> {
            Profile saved = inv.getArgument(0);
            saved.setId(5L);
            return saved;
        });
    }

    private void stubDoctorSave() {
        when(doctorProfileRepository.saveAndFlush(any(DoctorProfile.class))).thenAnswer(inv -> {
            DoctorProfile saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });
    }

    private void stubPatientSave() {
        when(patientProfileRepository.saveAndFlush(any(PatientProfile.class))).thenAnswer(inv -> {
            PatientProfile saved = inv.getArgument(0);
            saved.setId(12L);
            return saved;
        });
    }

    @Test
    void createDoctor_createsProfileAndDoctorWithDefaults() {
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(Fixtures.organization()));
        when(profileRepository.existsByEmail("new.doc@hospital.com")).thenReturn(false);
        when(doctorProfileRepository.countDoctorsByOrganizationId(Fixtures.ORG_ID)).thenReturn(2L);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        stubProfileSaves();
        stubDoctorSave();

        DoctorProfileResponse response = service.createDoctor(Fixtures.ORG_ID, doctorRequest());

        ArgumentCaptor<Profile> profile = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(profile.capture());
        assertThat(profile.getValue().getProfileType()).isEqualTo(ProfileType.DOCTOR);
        assertThat(profile.getValue().getPassword()).isEqualTo("hashed");
        assertThat(response.getIsAvailable()).isTrue();
        assertThat(response.getRating()).isZero();
        assertThat(response.getOrganizationId()).isEqualTo(Fixtures.ORG_ID);
        assertThat(response.getSpecialization()).isEqualTo("Oncologia");
    }

    @Test
    void createDoctor_rejectsWhenOrganizationIsFull() {
        User org = Fixtures.organization();
        org.setMaxDoctors(2);
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(org));
        when(profileRepository.existsByEmail(any())).thenReturn(false);
        when(doctorProfileRepository.countDoctorsByOrganizationId(Fixtures.ORG_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.createDoctor(Fixtures.ORG_ID, doctorRequest()))
                .hasMessage("Organization has reached maximum number of doctors");
        verify(profileRepository, never()).save(any());
        verifyNoInteractions(accountProvisioning);
    }

    @Test
    void createDoctor_rejectsDuplicatedEmailAndUnknownOrganization() {
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(Fixtures.organization()));
        when(profileRepository.existsByEmail("new.doc@hospital.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createDoctor(Fixtures.ORG_ID, doctorRequest()))
                .hasMessage("Email already exists");

        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createDoctor(999L, doctorRequest()))
                .hasMessage("Organization not found");
    }

    @Test
    void createDoctor_provisionsAuthAccountWithNewIds() {
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(Fixtures.organization()));
        when(doctorProfileRepository.countDoctorsByOrganizationId(Fixtures.ORG_ID)).thenReturn(0L);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        stubProfileSaves();
        stubDoctorSave();

        service.createDoctor(Fixtures.ORG_ID, doctorRequest());

        verify(accountProvisioning).provision(new NewAccount("new.doc@hospital.com", "Secret123!", "DOCTOR",
                Fixtures.ORG_ID, 5L, 7L, null));
    }

    @Test
    void createDoctor_authConflict_isReportedAsEmailAlreadyExists() {
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(Fixtures.organization()));
        when(doctorProfileRepository.countDoctorsByOrganizationId(Fixtures.ORG_ID)).thenReturn(0L);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        stubProfileSaves();
        stubDoctorSave();
        when(accountProvisioning.provision(any(NewAccount.class))).thenThrow(new IdentityConflictException("409"));

        assertThatThrownBy(() -> service.createDoctor(Fixtures.ORG_ID, doctorRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already exists");
    }

    @Test
    void createDoctor_authUnavailable_propagatesSoTheTransactionRollsBack() {
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(Fixtures.organization()));
        when(doctorProfileRepository.countDoctorsByOrganizationId(Fixtures.ORG_ID)).thenReturn(0L);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        stubProfileSaves();
        stubDoctorSave();
        when(accountProvisioning.provision(any(NewAccount.class)))
                .thenThrow(new IdentityUnavailableException("auth down", null));

        assertThatThrownBy(() -> service.createDoctor(Fixtures.ORG_ID, doctorRequest()))
                .isInstanceOf(IdentityUnavailableException.class);
    }

    @Test
    void createPatient_linksPatientToDoctorAndOrganization() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(profileRepository.existsByEmail("new.pat@mail.com")).thenReturn(false);
        stubProfileSaves();
        stubPatientSave();

        PatientProfileResponse response = service.createPatient(Fixtures.DOCTOR_ID, patientRequest());

        ArgumentCaptor<Profile> profile = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(profile.capture());
        assertThat(profile.getValue().getUser().getId()).isEqualTo(Fixtures.ORG_ID);
        assertThat(profile.getValue().getProfileType()).isEqualTo(ProfileType.PATIENT);
        assertThat(response.getDoctorProfileId()).isEqualTo(Fixtures.DOCTOR_ID);
        assertThat(response.getDoctorName()).isEqualTo("Carlos Garcia");
        assertThat(response.getCancerStage()).isEqualTo("III");
    }

    @Test
    void createPatient_provisionsAuthAccountWithDoctorAndPatientIds() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        stubProfileSaves();
        stubPatientSave();

        service.createPatient(Fixtures.DOCTOR_ID, patientRequest());

        verify(accountProvisioning).provision(new NewAccount("new.pat@mail.com", "Secret123!", "PATIENT",
                Fixtures.ORG_ID, 5L, Fixtures.DOCTOR_ID, 12L));
    }

    @Test
    void createPatient_authFailures_areMappedLikeForDoctors() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        stubProfileSaves();
        stubPatientSave();
        when(accountProvisioning.provision(any(NewAccount.class)))
                .thenThrow(new IdentityConflictException("409"))
                .thenThrow(new IdentityUnavailableException("down", null));

        assertThatThrownBy(() -> service.createPatient(Fixtures.DOCTOR_ID, patientRequest()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Email already exists");
        assertThatThrownBy(() -> service.createPatient(Fixtures.DOCTOR_ID, patientRequest()))
                .isInstanceOf(IdentityUnavailableException.class);
    }

    @Test
    void createPatient_rejectsUnknownDoctorAndDuplicatedEmail() {
        when(doctorProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createPatient(99L, patientRequest()))
                .hasMessage("Doctor profile not found");

        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(profileRepository.existsByEmail("new.pat@mail.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createPatient(Fixtures.DOCTOR_ID, patientRequest()))
                .hasMessage("Email already exists");
    }

    @Test
    void loginProfile_returnsDoctorOrPatientResponse() {
        Profile doctorAccount = Fixtures.doctorAccount();
        Profile patientAccount = Fixtures.patientAccount();
        when(profileRepository.findByEmail(doctorAccount.getEmail())).thenReturn(Optional.of(doctorAccount));
        when(profileRepository.findByEmail(patientAccount.getEmail())).thenReturn(Optional.of(patientAccount));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);
        when(doctorProfileRepository.findByProfileId(doctorAccount.getId())).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findByProfileId(patientAccount.getId())).thenReturn(Optional.of(Fixtures.patient()));

        assertThat(service.loginProfile(doctorAccount.getEmail(), "pw")).isInstanceOf(DoctorProfileResponse.class);
        assertThat(service.loginProfile(patientAccount.getEmail(), "pw")).isInstanceOf(PatientProfileResponse.class);
    }

    @Test
    void loginProfile_rejectsBadCredentials() {
        Profile doctorAccount = Fixtures.doctorAccount();
        when(profileRepository.findByEmail(doctorAccount.getEmail())).thenReturn(Optional.of(doctorAccount));
        when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);
        when(profileRepository.findByEmail("ghost@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginProfile(doctorAccount.getEmail(), "bad")).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> service.loginProfile("ghost@mail.com", "pw")).hasMessage("Invalid credentials");
    }

    @Test
    void resolveIdentity_doctor_usesProfilePkAndDoctorProfileId() {
        Profile account = Fixtures.doctorAccount();
        when(profileRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(doctorProfileRepository.findByProfileId(account.getId())).thenReturn(Optional.of(Fixtures.doctor()));

        ProfileIdentity identity = service.resolveIdentity(account.getEmail());

        assertThat(identity).isEqualTo(new ProfileIdentity(ProfileType.DOCTOR, "dr.garcia@hospital.com",
                Fixtures.ORG_ID, Fixtures.DOCTOR_PROFILE_ACCOUNT_ID, Fixtures.DOCTOR_ID, null));
    }

    @Test
    void resolveIdentity_patient_includesOwningDoctorAndPatientIds() {
        Profile account = Fixtures.patientAccount();
        when(profileRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(patientProfileRepository.findByProfileId(account.getId())).thenReturn(Optional.of(Fixtures.patient()));

        ProfileIdentity identity = service.resolveIdentity(account.getEmail());

        assertThat(identity).isEqualTo(new ProfileIdentity(ProfileType.PATIENT, "juan.perez@mail.com",
                Fixtures.ORG_ID, Fixtures.PATIENT_PROFILE_ACCOUNT_ID, Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID));
    }

    @Test
    void resolveIdentity_unknownEmail_isRejected() {
        when(profileRepository.findByEmail("ghost@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveIdentity("ghost@mail.com")).hasMessage("Invalid credentials");
    }

    @Test
    void queries_mapEntitiesToResponses() {
        when(doctorProfileRepository.findByOrganizationId(Fixtures.ORG_ID)).thenReturn(List.of(Fixtures.doctor()));
        when(patientProfileRepository.findByDoctorProfileId(Fixtures.DOCTOR_ID)).thenReturn(List.of(Fixtures.patient()));
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));

        assertThat(service.getDoctorsByOrganizationId(Fixtures.ORG_ID)).extracting(DoctorProfileResponse::getProfileId)
                .containsExactly("DOC-001");
        assertThat(service.getPatientsByDoctorId(Fixtures.DOCTOR_ID)).extracting(PatientProfileResponse::getProfileId)
                .containsExactly("PAT-001");
        assertThat(service.getDoctorProfileById(Fixtures.DOCTOR_ID).getEmail()).isEqualTo("dr.garcia@hospital.com");
        assertThat(service.getPatientProfileById(Fixtures.PATIENT_ID).getEmail()).isEqualTo("juan.perez@mail.com");
    }

    @Test
    void getById_failsWhenMissing() {
        when(doctorProfileRepository.findById(99L)).thenReturn(Optional.empty());
        when(patientProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDoctorProfileById(99L)).hasMessage("Doctor profile not found");
        assertThatThrownBy(() -> service.getPatientProfileById(99L)).hasMessage("Patient profile not found");
    }

    @Test
    void updatePatientProfile_updatesDemographicAndClinicalFields() {
        PatientProfile patient = Fixtures.patient();
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(patient));
        UpdatePatientRequest request = UpdatePatientRequest.builder()
                .firstName("Juana").lastName("Paz").phone("999").birthDate(LocalDate.of(1980, 2, 2))
                .city("Cusco").address("Av. Sol 1")
                .cancerType("Mama").cancerStage("I").diagnosisDate(LocalDate.of(2026, 1, 1))
                .treatmentStatus("ACTIVE").bloodType("O+").allergies("Penicilina")
                .emergencyContactName("Rosa").emergencyContactPhone("988").emergencyContactRelationship("Hermana")
                .build();

        PatientProfileResponse response = service.updatePatientProfile(Fixtures.PATIENT_ID, request);

        assertThat(response.getFirstName()).isEqualTo("Juana");
        assertThat(response.getCity()).isEqualTo("Cusco");
        assertThat(response.getCancerType()).isEqualTo("Mama");
        assertThat(response.getBloodType()).isEqualTo("O+");
        assertThat(response.getEmergencyContactRelationship()).isEqualTo("Hermana");
        verify(profileRepository).save(patient.getProfile());
        verify(patientProfileRepository).save(patient);
    }

    @Test
    void updatePatientProfile_emptyRequestKeepsData() {
        PatientProfile patient = Fixtures.patient();
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(patient));

        PatientProfileResponse response = service.updatePatientProfile(Fixtures.PATIENT_ID, new UpdatePatientRequest());

        assertThat(response.getFirstName()).isEqualTo("Juan");
        assertThat(response.getCancerType()).isEqualTo("Pulmon");
    }

    @Test
    void updateDoctorProfile_updatesFieldsAndLicense() {
        DoctorProfile doctor = Fixtures.doctor();
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(doctorProfileRepository.findByLicenseNumber("CMP-777")).thenReturn(Optional.empty());
        when(doctorProfileRepository.save(any(DoctorProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateDoctorRequest request = UpdateDoctorRequest.builder()
                .firstName("Carla").lastName("Gomez").phone("977").birthDate(LocalDate.of(1975, 3, 3))
                .city("Arequipa").address("Calle 2")
                .licenseNumber("CMP-777").specialization("Radioterapia").yearsOfExperience(12)
                .hospitalAffiliation("INEN").consultationFee(new BigDecimal("150")).bio("Bio")
                .isAvailable(false)
                .build();

        DoctorProfileResponse response = service.updateDoctorProfile(Fixtures.DOCTOR_ID, request);

        assertThat(response.getLicenseNumber()).isEqualTo("CMP-777");
        assertThat(response.getSpecialization()).isEqualTo("Radioterapia");
        assertThat(response.getIsAvailable()).isFalse();
        assertThat(response.getFirstName()).isEqualTo("Carla");
    }

    @Test
    void updateDoctorProfile_rejectsLicenseOwnedByAnotherDoctor() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(doctorProfileRepository.findByLicenseNumber("CMP-777"))
                .thenReturn(Optional.of(DoctorProfile.builder().id(77L).build()));

        assertThatThrownBy(() -> service.updateDoctorProfile(Fixtures.DOCTOR_ID,
                UpdateDoctorRequest.builder().licenseNumber("CMP-777").build()))
                .hasMessage("License number already in use");
    }

    @Test
    void updateDoctorProfile_failsWhenMissing() {
        when(doctorProfileRepository.findById(99L)).thenReturn(Optional.empty());
        when(patientProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDoctorProfile(99L, new UpdateDoctorRequest()))
                .hasMessage("Doctor profile not found");
        assertThatThrownBy(() -> service.updatePatientProfile(99L, new UpdatePatientRequest()))
                .hasMessage("Patient profile not found");
    }

    @Test
    void activeListings_skipProfilesWithoutDetail() {
        Profile orphan = Profile.builder().id(50L).profileType(ProfileType.DOCTOR).build();
        when(profileRepository.findAllActiveDoctors()).thenReturn(List.of(Fixtures.doctorAccount(), orphan));
        when(doctorProfileRepository.findByProfileId(Fixtures.DOCTOR_PROFILE_ACCOUNT_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(doctorProfileRepository.findByProfileId(50L)).thenReturn(Optional.empty());
        when(profileRepository.findAllActivePatients()).thenReturn(List.of(Fixtures.patientAccount()));
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(Optional.of(Fixtures.patient()));

        assertThat(service.getAllActiveDoctors()).hasSize(1);
        assertThat(service.getAllActivePatients()).hasSize(1);
    }
}
