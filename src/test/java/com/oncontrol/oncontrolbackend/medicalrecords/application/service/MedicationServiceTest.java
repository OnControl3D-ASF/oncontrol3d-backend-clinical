package com.oncontrol.oncontrolbackend.medicalrecords.application.service;

import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.CreateMedicationRequest;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.MarkDoseTakenRequest;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.MedicationResponse;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.UpcomingDoseResponse;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.UpdateMedicationRequest;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.Medication;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicationRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationServiceTest {

    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private MedicationService service;

    private static Medication medication() {
        return Medication.builder()
                .id(8L)
                .patient(Fixtures.patient())
                .prescribedBy(Fixtures.doctor())
                .medicationName("Ondansetron")
                .dosage("8 mg")
                .frequency("Cada 8 horas")
                .startDate(LocalDate.now().minusDays(3))
                .adherencePercentage(100)
                .isActive(true)
                .isPrn(false)
                .build();
    }

    private void stubFindAndSave(Medication medication) {
        when(medicationRepository.findById(8L)).thenReturn(Optional.of(medication));
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createMedication_startsActiveWithFullAdherence() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateMedicationRequest request = CreateMedicationRequest.builder()
                .name("Ondansetron").dosage("8 mg").frequency("Cada 8 horas").route("ORAL")
                .startDate(LocalDate.now()).build();

        MedicationResponse response = service.createMedication(Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID, request);

        assertThat(response.getName()).isEqualTo("Ondansetron");
        assertThat(response.getAdherencePercentage()).isEqualTo(100);
        assertThat(response.getIsPrn()).isFalse();
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getDoctorName()).isEqualTo("Carlos Garcia");
    }

    @Test
    void createMedication_failsForUnknownDoctorOrPatient() {
        when(doctorProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createMedication(99L, Fixtures.PATIENT_ID, new CreateMedicationRequest()))
                .hasMessage("Doctor not found");

        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createMedication(Fixtures.DOCTOR_ID, 99L, new CreateMedicationRequest()))
                .hasMessage("Patient not found");
    }

    @Test
    void status_isDerivedFromActiveFlagAndEndDate() {
        Medication finished = medication();
        finished.setEndDate(LocalDate.now().minusDays(1));
        Medication stopped = medication();
        stopped.setIsActive(false);
        when(medicationRepository.findById(1L)).thenReturn(Optional.of(finished));
        when(medicationRepository.findById(2L)).thenReturn(Optional.of(stopped));

        assertThat(service.getMedicationById(1L).getStatus()).isEqualTo("COMPLETED");
        assertThat(service.getMedicationById(2L).getStatus()).isEqualTo("DISCONTINUED");
        verify(authorizationService, org.mockito.Mockito.times(2)).requirePatientAccess(Fixtures.PATIENT_ID);
    }

    @Test
    void getMedicationById_failsWhenMissing() {
        when(medicationRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMedicationById(8L)).hasMessage("Medication not found");
    }

    @Test
    void listQueries_resolvePatientOrDoctor() {
        PatientProfile patient = Fixtures.patient();
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(patient));
        when(medicationRepository.findByPatient(patient)).thenReturn(List.of(medication(), medication()));
        when(medicationRepository.findActiveByPatientOrderByStartDateDesc(patient)).thenReturn(List.of(medication()));
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(medicationRepository.findByPrescribedBy(any())).thenReturn(List.of(medication()));

        assertThat(service.getMedicationsByPatient(Fixtures.PATIENT_ID)).hasSize(2);
        assertThat(service.getActiveMedicationsByPatient(Fixtures.PATIENT_ID)).hasSize(1);
        assertThat(service.getMedicationsByDoctor(Fixtures.DOCTOR_ID)).hasSize(1);
    }

    @Test
    void listQueries_failForUnknownProfiles() {
        when(patientProfileRepository.findById(99L)).thenReturn(Optional.empty());
        when(doctorProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMedicationsByPatient(99L)).hasMessage("Patient not found");
        assertThatThrownBy(() -> service.getActiveMedicationsByPatient(99L)).hasMessage("Patient not found");
        assertThatThrownBy(() -> service.getMedicationsByDoctor(99L)).hasMessage("Doctor not found");
        assertThatThrownBy(() -> service.getUpcomingDoses(99L, 7)).hasMessage("Patient not found");
    }

    @Test
    void updateMedication_appliesProvidedFields() {
        stubFindAndSave(medication());
        UpdateMedicationRequest request = UpdateMedicationRequest.builder()
                .dosage("4 mg").frequency("Cada 12 horas").instructions("Con comida")
                .endDate(LocalDate.now().plusDays(10)).nextDoseTime(LocalTime.of(20, 0))
                .adherencePercentage(85).sideEffects("Cefalea").isActive(true)
                .build();

        MedicationResponse response = service.updateMedication(8L, request);

        assertThat(response.getDosage()).isEqualTo("4 mg");
        assertThat(response.getFrequency()).isEqualTo("Cada 12 horas");
        assertThat(response.getAdherencePercentage()).isEqualTo(85);
        assertThat(response.getNextDoseTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(response.getSideEffects()).isEqualTo("Cefalea");
    }

    @Test
    void updateMedication_emptyRequestKeepsValues() {
        stubFindAndSave(medication());

        assertThat(service.updateMedication(8L, new UpdateMedicationRequest()).getDosage()).isEqualTo("8 mg");
    }

    @Test
    void discontinueMedication_deactivatesAndRecordsReason() {
        Medication medication = medication();
        medication.setSideEffects("Cefalea");
        stubFindAndSave(medication);

        MedicationResponse response = service.discontinueMedication(8L, "Reaccion alergica");

        assertThat(response.getStatus()).isEqualTo("DISCONTINUED");
        assertThat(response.getEndDate()).isEqualTo(LocalDate.now());
        assertThat(response.getSideEffects()).isEqualTo("Cefalea\nDiscontinued: Reaccion alergica");
    }

    @Test
    void discontinueMedication_withoutPreviousSideEffects() {
        stubFindAndSave(medication());

        assertThat(service.discontinueMedication(8L, "Fin").getSideEffects()).isEqualTo("Discontinued: Fin");
    }

    @Test
    void getUpcomingDoses_includesOnlyFutureDosesToday() {
        PatientProfile patient = Fixtures.patient();
        Medication later = medication();
        later.setNextDoseTime(LocalTime.MAX);
        Medication earlier = medication();
        earlier.setNextDoseTime(LocalTime.MIN);
        Medication withoutSchedule = medication();
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(patient));
        when(medicationRepository.findActiveByPatientOrderByStartDateDesc(patient))
                .thenReturn(List.of(later, earlier, withoutSchedule));

        List<UpcomingDoseResponse> doses = service.getUpcomingDoses(Fixtures.PATIENT_ID, null);

        assertThat(doses).singleElement().satisfies(dose -> {
            assertThat(dose.getMedicationName()).isEqualTo("Ondansetron");
            assertThat(dose.getTaken()).isFalse();
        });
    }

    @Test
    void markDoseAsTaken_movesNextDoseByOneDay() {
        Medication medication = medication();
        medication.setNextDoseTime(LocalTime.of(8, 0));
        stubFindAndSave(medication);

        MedicationResponse response = service.markDoseAsTaken(8L, new MarkDoseTakenRequest());

        assertThat(response.getNextDoseTime()).isEqualTo(LocalTime.of(8, 0));
        verify(authorizationService).requirePatientAccess(Fixtures.PATIENT_ID);
    }

    @Test
    void markDoseAsTaken_withoutScheduleStillSaves() {
        stubFindAndSave(medication());

        assertThat(service.markDoseAsTaken(8L, new MarkDoseTakenRequest()).getNextDoseTime()).isNull();
    }
}
