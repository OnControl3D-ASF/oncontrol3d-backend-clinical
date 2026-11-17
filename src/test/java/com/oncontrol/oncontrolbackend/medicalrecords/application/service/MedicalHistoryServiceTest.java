package com.oncontrol.oncontrolbackend.medicalrecords.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.AllergyResponse;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.CreateAllergyRequest;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.CreateHistoryEntryRequest;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.HistoryEntryResponse;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.Allergy;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.AllergyType;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.HistoryEntryType;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.MedicalHistoryEntry;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.SeverityLevel;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.AllergyRepository;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicalHistoryRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalHistoryServiceTest {

    @Mock
    private MedicalHistoryRepository historyRepository;
    @Mock
    private AllergyRepository allergyRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private AuthorizationService authorizationService;

    private MedicalHistoryService service;

    @BeforeEach
    void setUp() {
        service = new MedicalHistoryService(historyRepository, allergyRepository, doctorProfileRepository,
                patientProfileRepository, authorizationService, new ObjectMapper());
    }

    private static MedicalHistoryEntry entry(boolean withDoctor) {
        return MedicalHistoryEntry.builder()
                .id(1L)
                .patient(Fixtures.patient())
                .doctor(withDoctor ? Fixtures.doctor() : null)
                .type(HistoryEntryType.DIAGNOSIS)
                .date(LocalDate.of(2026, 5, 1))
                .title("Adenocarcinoma pulmonar")
                .documents("[\"tac.pdf\"]")
                .isActive(true)
                .build();
    }

    private static CreateHistoryEntryRequest historyRequest() {
        return CreateHistoryEntryRequest.builder()
                .type(HistoryEntryType.TEST_RESULT).date(LocalDate.of(2026, 6, 1))
                .title("TAC de torax").description("Sin progresion")
                .severity(SeverityLevel.LOW).documents(List.of("tac.pdf", "informe.pdf"))
                .build();
    }

    @Test
    void createHistoryEntry_withDoctorStoresDocuments() {
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(historyRepository.save(any(MedicalHistoryEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        HistoryEntryResponse response = service.createHistoryEntry(Fixtures.PATIENT_ID, Fixtures.DOCTOR_ID, historyRequest());

        verify(authorizationService).requirePatientAccess(Fixtures.PATIENT_ID);
        assertThat(response.getDocuments()).containsExactly("tac.pdf", "informe.pdf");
        assertThat(response.getDoctorName()).isEqualTo("Carlos Garcia");
        assertThat(response.getSpecialty()).isEqualTo("Oncologia");
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    void createHistoryEntry_withoutDoctor() {
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(historyRepository.save(any(MedicalHistoryEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateHistoryEntryRequest request = historyRequest();
        request.setDocuments(null);

        HistoryEntryResponse response = service.createHistoryEntry(Fixtures.PATIENT_ID, null, request);

        assertThat(response.getDoctorId()).isNull();
        assertThat(response.getDoctorName()).isNull();
        assertThat(response.getDocuments()).isEmpty();
    }

    @Test
    void createHistoryEntry_deniedWithoutPatientAccess() {
        doThrow(new AccessDeniedException("no")).when(authorizationService).requirePatientAccess(Fixtures.PATIENT_ID);

        assertThatThrownBy(() -> service.createHistoryEntry(Fixtures.PATIENT_ID, null, historyRequest()))
                .isInstanceOf(AccessDeniedException.class);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void createHistoryEntry_failsForUnknownDoctorOrPatient() {
        when(patientProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createHistoryEntry(99L, null, historyRequest()))
                .hasMessage("Patient not found");

        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(doctorProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createHistoryEntry(Fixtures.PATIENT_ID, 99L, historyRequest()))
                .hasMessage("Doctor not found");
    }

    @Test
    void historyQueries_mapEntries() {
        PatientProfile patient = Fixtures.patient();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(patient));
        when(historyRepository.findByPatientAndIsActiveTrueOrderByDateDesc(patient)).thenReturn(List.of(entry(true), entry(false)));
        when(historyRepository.findByPatientAndTypeAndIsActiveTrueOrderByDateDesc(patient, HistoryEntryType.DIAGNOSIS))
                .thenReturn(List.of(entry(true)));
        when(historyRepository.findByPatientAndDateRange(patient, from, to)).thenReturn(List.of(entry(false)));

        assertThat(service.getPatientHistory(Fixtures.PATIENT_ID)).hasSize(2);
        assertThat(service.getPatientHistoryByType(Fixtures.PATIENT_ID, HistoryEntryType.DIAGNOSIS))
                .singleElement().satisfies(e -> assertThat(e.getDocuments()).containsExactly("tac.pdf"));
        assertThat(service.getPatientHistoryByDateRange(Fixtures.PATIENT_ID, from, to)).hasSize(1);
    }

    @Test
    void historyQueries_failForUnknownPatient() {
        when(patientProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPatientHistory(99L)).hasMessage("Patient not found");
        assertThatThrownBy(() -> service.getPatientHistoryByType(99L, HistoryEntryType.OTHER)).hasMessage("Patient not found");
        assertThatThrownBy(() -> service.getPatientHistoryByDateRange(99L, null, null)).hasMessage("Patient not found");
        assertThatThrownBy(() -> service.getPatientAllergies(99L)).hasMessage("Patient not found");
    }

    @Test
    void createAllergy_checksAccessAndPersistsActive() {
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(allergyRepository.save(any(Allergy.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateAllergyRequest request = CreateAllergyRequest.builder()
                .allergen("Penicilina").type(AllergyType.MEDICATION).severity(SeverityLevel.HIGH)
                .reaction("Urticaria").diagnosedDate(LocalDate.of(2020, 1, 1)).build();

        AllergyResponse response = service.createAllergy(Fixtures.PATIENT_ID, request);

        verify(authorizationService).requirePatientAccess(Fixtures.PATIENT_ID);
        assertThat(response.getAllergen()).isEqualTo("Penicilina");
        assertThat(response.getSeverity()).isEqualTo(SeverityLevel.HIGH);
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    void getPatientAllergies_mapsActiveAllergies() {
        PatientProfile patient = Fixtures.patient();
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(patient));
        when(allergyRepository.findByPatientAndIsActiveTrueOrderByDiagnosedDateDesc(patient))
                .thenReturn(List.of(Allergy.builder().id(1L).patient(patient).allergen("Mani").isActive(true).build()));

        assertThat(service.getPatientAllergies(Fixtures.PATIENT_ID)).extracting(AllergyResponse::getAllergen)
                .containsExactly("Mani");
    }

    @Test
    void deleteAllergy_isSoftDelete() {
        Allergy allergy = Allergy.builder().id(1L).patient(Fixtures.patient()).allergen("Mani").isActive(true).build();
        when(allergyRepository.findById(1L)).thenReturn(Optional.of(allergy));

        service.deleteAllergy(1L);

        assertThat(allergy.getIsActive()).isFalse();
        verify(authorizationService).requirePatientAccess(Fixtures.PATIENT_ID);
        verify(allergyRepository).save(allergy);
    }

    @Test
    void deleteAllergy_failsWhenMissing() {
        when(allergyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAllergy(1L)).hasMessage("Allergy not found");
    }
}
