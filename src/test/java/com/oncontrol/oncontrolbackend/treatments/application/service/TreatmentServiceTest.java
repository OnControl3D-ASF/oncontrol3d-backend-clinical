package com.oncontrol.oncontrolbackend.treatments.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.TreatmentStatus;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import com.oncontrol.oncontrolbackend.treatments.application.dto.CreateSessionRequest;
import com.oncontrol.oncontrolbackend.treatments.application.dto.CreateTreatmentRequest;
import com.oncontrol.oncontrolbackend.treatments.application.dto.TreatmentResponse;
import com.oncontrol.oncontrolbackend.treatments.application.dto.TreatmentSessionResponse;
import com.oncontrol.oncontrolbackend.treatments.application.dto.TreatmentStatsResponse;
import com.oncontrol.oncontrolbackend.treatments.application.dto.UpdateTreatmentRequest;
import com.oncontrol.oncontrolbackend.treatments.application.dto.UpdateTreatmentStatusRequest;
import com.oncontrol.oncontrolbackend.treatments.domain.model.SessionStatus;
import com.oncontrol.oncontrolbackend.treatments.domain.model.Treatment;
import com.oncontrol.oncontrolbackend.treatments.domain.model.TreatmentSession;
import com.oncontrol.oncontrolbackend.treatments.domain.model.TreatmentType;
import com.oncontrol.oncontrolbackend.treatments.domain.repository.TreatmentRepository;
import com.oncontrol.oncontrolbackend.treatments.domain.repository.TreatmentSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentServiceTest {

    @Mock
    private TreatmentRepository treatmentRepository;
    @Mock
    private TreatmentSessionRepository sessionRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private AuthorizationService authorizationService;

    private TreatmentService service;

    @BeforeEach
    void setUp() {
        service = new TreatmentService(treatmentRepository, sessionRepository, doctorProfileRepository,
                patientProfileRepository, authorizationService, new ObjectMapper());
    }

    private static Treatment treatment(int currentCycle, int totalCycles) {
        return Treatment.builder()
                .id(5L)
                .doctor(Fixtures.doctor())
                .patient(Fixtures.patient())
                .type(TreatmentType.CHEMOTHERAPY)
                .protocol("FOLFOX")
                .currentCycle(currentCycle)
                .totalCycles(totalCycles)
                .startDate(LocalDate.of(2026, 1, 10))
                .status(TreatmentStatus.ACTIVE)
                .sessionDurationMinutes(120)
                .location("Sala 3")
                .medications("[\"Oxaliplatino\"]")
                .sideEffects("[\"Nauseas\"]")
                .isActive(true)
                .build();
    }

    private void stubSaveReturnsArgument() {
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createTreatment_startsActiveAtCycleOne() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        stubSaveReturnsArgument();
        CreateTreatmentRequest request = CreateTreatmentRequest.builder()
                .type(TreatmentType.CHEMOTHERAPY).protocol("FOLFOX").totalCycles(6)
                .startDate(LocalDate.of(2026, 1, 10)).medications(List.of("Oxaliplatino", "5-FU")).build();

        TreatmentResponse response = service.createTreatment(Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID, request);

        assertThat(response.getStatus()).isEqualTo(TreatmentStatus.ACTIVE);
        assertThat(response.getCurrentCycle()).isEqualTo(1);
        assertThat(response.getProgressPercentage()).isEqualTo(16);
        assertThat(response.getMedications()).containsExactly("Oxaliplatino", "5-FU");
        assertThat(response.getSideEffects()).isEmpty();
        assertThat(response.getPatientName()).isEqualTo("Juan Perez");
    }

    @Test
    void createTreatment_failsForUnknownDoctorOrPatient() {
        when(doctorProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createTreatment(99L, Fixtures.PATIENT_ID, new CreateTreatmentRequest()))
                .hasMessage("Doctor not found");

        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createTreatment(Fixtures.DOCTOR_ID, 99L, new CreateTreatmentRequest()))
                .hasMessage("Patient not found");
    }

    @Test
    void getTreatmentById_checksPatientAccess() {
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(treatment(2, 6)));

        TreatmentResponse response = service.getTreatmentById(5L);

        verify(authorizationService).requirePatientAccess(Fixtures.PATIENT_ID);
        assertThat(response.getMedications()).containsExactly("Oxaliplatino");
    }

    @Test
    void getTreatmentById_propagatesAccessDenied() {
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(treatment(2, 6)));
        doThrow(new AccessDeniedException("no")).when(authorizationService).requirePatientAccess(Fixtures.PATIENT_ID);

        assertThatThrownBy(() -> service.getTreatmentById(5L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getTreatmentById_failsWhenMissing() {
        when(treatmentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTreatmentById(5L)).hasMessage("Treatment not found");
    }

    @Test
    void listQueries_mapAllResults() {
        when(treatmentRepository.findByDoctorIdAndIsActiveTrue(Fixtures.DOCTOR_ID)).thenReturn(List.of(treatment(1, 6)));
        when(treatmentRepository.findByPatientIdAndIsActiveTrue(Fixtures.PATIENT_ID))
                .thenReturn(List.of(treatment(1, 6), treatment(2, 6)));

        assertThat(service.getTreatmentsByDoctor(Fixtures.DOCTOR_ID)).hasSize(1);
        assertThat(service.getTreatmentsByPatient(Fixtures.PATIENT_ID)).hasSize(2);
    }

    @Test
    void getCurrentTreatmentByPatient_returnsFirstOrFails() {
        when(treatmentRepository.findCurrentTreatmentsByPatientId(Fixtures.PATIENT_ID)).thenReturn(List.of(treatment(3, 6)));
        assertThat(service.getCurrentTreatmentByPatient(Fixtures.PATIENT_ID).getCurrentCycle()).isEqualTo(3);

        when(treatmentRepository.findCurrentTreatmentsByPatientId(Fixtures.PATIENT_ID)).thenReturn(List.of());
        assertThatThrownBy(() -> service.getCurrentTreatmentByPatient(Fixtures.PATIENT_ID))
                .hasMessage("No active treatment found for patient");
    }

    @Test
    void updateTreatment_appliesOnlyProvidedFields() {
        Treatment existing = treatment(2, 6);
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        stubSaveReturnsArgument();
        UpdateTreatmentRequest request = UpdateTreatmentRequest.builder()
                .currentCycle(3)
                .endDate(LocalDate.of(2026, 6, 1))
                .nextSession(LocalDateTime.of(2026, 2, 1, 9, 0))
                .status(TreatmentStatus.SUSPENDED)
                .effectiveness(new BigDecimal("80"))
                .adherence(new BigDecimal("95"))
                .location("Sala 4")
                .medications(List.of("Capecitabina"))
                .sideEffects(List.of("Fatiga"))
                .notes("Ajuste de dosis")
                .preparationInstructions("Ayuno")
                .build();

        TreatmentResponse response = service.updateTreatment(5L, request);

        assertThat(response.getCurrentCycle()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo(TreatmentStatus.SUSPENDED);
        assertThat(response.getLocation()).isEqualTo("Sala 4");
        assertThat(response.getMedications()).containsExactly("Capecitabina");
        assertThat(response.getSideEffects()).containsExactly("Fatiga");
        assertThat(response.getProtocol()).isEqualTo("FOLFOX");
    }

    @Test
    void updateTreatment_withEmptyRequestKeepsValues() {
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(treatment(2, 6)));
        stubSaveReturnsArgument();

        TreatmentResponse response = service.updateTreatment(5L, new UpdateTreatmentRequest());

        assertThat(response.getCurrentCycle()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(TreatmentStatus.ACTIVE);
    }

    @Test
    void updateTreatmentStatus_appendsReasonToNotes() {
        Treatment existing = treatment(2, 6);
        existing.setNotes("Inicio");
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        stubSaveReturnsArgument();

        TreatmentResponse response = service.updateTreatmentStatus(5L,
                new UpdateTreatmentStatusRequest(TreatmentStatus.SUSPENDED, "Neutropenia"));

        assertThat(response.getStatus()).isEqualTo(TreatmentStatus.SUSPENDED);
        assertThat(response.getNotes()).isEqualTo("Inicio\nStatus changed to SUSPENDED: Neutropenia");
    }

    @Test
    void updateTreatmentStatus_withoutReasonKeepsNotes() {
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(treatment(2, 6)));
        stubSaveReturnsArgument();

        TreatmentResponse response = service.updateTreatmentStatus(5L,
                new UpdateTreatmentStatusRequest(TreatmentStatus.COMPLETED, null));

        assertThat(response.getNotes()).isNull();
    }

    @Test
    void registerSession_numbersSessionAndAdvancesCycle() {
        Treatment existing = treatment(2, 6);
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(sessionRepository.countByTreatmentIdAndStatus(5L, SessionStatus.COMPLETED)).thenReturn(4L);
        when(sessionRepository.save(any(TreatmentSession.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateSessionRequest request = CreateSessionRequest.builder()
                .sessionDate(LocalDateTime.of(2026, 2, 1, 9, 0))
                .cycleNumber(3)
                .medicationsAdministered(List.of("Oxaliplatino"))
                .sideEffects(List.of("Fatiga", "Nauseas"))
                .vitalSigns(Map.of("pa", "120/80"))
                .build();

        TreatmentSessionResponse session = service.registerSession(5L, request);

        assertThat(session.getSessionNumber()).isEqualTo(5);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getDurationMinutes()).isEqualTo(120);
        assertThat(session.getVitalSigns()).containsEntry("pa", "120/80");
        assertThat(existing.getCurrentCycle()).isEqualTo(3);
        assertThat(existing.getStatus()).isEqualTo(TreatmentStatus.ACTIVE);
        assertThat(existing.getSideEffects()).contains("Fatiga").contains("Nauseas");
    }

    @Test
    void registerSession_completesTreatmentOnLastCycle() {
        Treatment existing = treatment(5, 6);
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(sessionRepository.countByTreatmentIdAndStatus(5L, SessionStatus.COMPLETED)).thenReturn(10L);
        when(sessionRepository.save(any(TreatmentSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registerSession(5L, CreateSessionRequest.builder()
                .sessionDate(LocalDateTime.of(2026, 3, 1, 9, 0)).cycleNumber(6).build());

        assertThat(existing.getStatus()).isEqualTo(TreatmentStatus.COMPLETED);
        verify(treatmentRepository).save(existing);
    }

    @Test
    void registerSession_sameCycleDoesNotTouchTreatment() {
        Treatment existing = treatment(3, 6);
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(sessionRepository.countByTreatmentIdAndStatus(5L, SessionStatus.COMPLETED)).thenReturn(0L);
        when(sessionRepository.save(any(TreatmentSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registerSession(5L, CreateSessionRequest.builder()
                .sessionDate(LocalDateTime.of(2026, 3, 1, 9, 0)).cycleNumber(3).build());

        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void sessionQueries_mapResults() {
        TreatmentSession session = TreatmentSession.builder().id(1L).treatment(treatment(1, 6))
                .sessionNumber(1).cycleNumber(1).status(SessionStatus.SCHEDULED)
                .vitalSigns("{\"pa\":\"110/70\"}").build();
        when(treatmentRepository.findById(5L)).thenReturn(Optional.of(treatment(1, 6)));
        when(sessionRepository.findByTreatmentIdOrderBySessionDateDesc(5L)).thenReturn(List.of(session));
        when(sessionRepository.findUpcomingSessionsByPatientId(eq(Fixtures.PATIENT_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(session));

        assertThat(service.getSessionsByTreatment(5L)).singleElement()
                .satisfies(s -> assertThat(s.getVitalSigns()).containsEntry("pa", "110/70"));
        assertThat(service.getUpcomingSessionsByPatient(Fixtures.PATIENT_ID)).hasSize(1);
    }

    @Test
    void getTreatmentStats_averagesAndGroupsByType() {
        Treatment a = treatment(1, 6);
        a.setEffectiveness(new BigDecimal("80"));
        a.setAdherence(new BigDecimal("90"));
        Treatment b = treatment(1, 6);
        b.setEffectiveness(new BigDecimal("60"));
        when(treatmentRepository.countByDoctorIdAndStatus(eq(Fixtures.DOCTOR_ID), any())).thenReturn(1L);
        when(treatmentRepository.findByDoctorIdAndIsActiveTrue(Fixtures.DOCTOR_ID)).thenReturn(List.of(a, b));
        when(treatmentRepository.countByDoctorIdAndType(eq(Fixtures.DOCTOR_ID), any())).thenReturn(0L);
        when(treatmentRepository.countByDoctorIdAndType(Fixtures.DOCTOR_ID, TreatmentType.CHEMOTHERAPY)).thenReturn(2L);

        TreatmentStatsResponse stats = service.getTreatmentStats(Fixtures.DOCTOR_ID);

        assertThat(stats.getAverageEffectiveness()).isEqualByComparingTo("70.00");
        assertThat(stats.getAverageAdherence()).isEqualByComparingTo("45.00");
        assertThat(stats.getByType()).containsExactly(Map.entry("CHEMOTHERAPY", 2L));
    }

    @Test
    void getTreatmentStats_withoutTreatmentsReturnsZeroAverages() {
        when(treatmentRepository.countByDoctorIdAndStatus(eq(Fixtures.DOCTOR_ID), any())).thenReturn(0L);
        when(treatmentRepository.findByDoctorIdAndIsActiveTrue(Fixtures.DOCTOR_ID)).thenReturn(List.of());
        when(treatmentRepository.countByDoctorIdAndType(eq(Fixtures.DOCTOR_ID), any())).thenReturn(0L);

        TreatmentStatsResponse stats = service.getTreatmentStats(Fixtures.DOCTOR_ID);

        assertThat(stats.getAverageEffectiveness()).isEqualByComparingTo("0");
        assertThat(stats.getByType()).isEmpty();
    }

    @Test
    void createTreatment_persistsSerializedMedications() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        stubSaveReturnsArgument();

        service.createTreatment(Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID,
                CreateTreatmentRequest.builder().type(TreatmentType.RADIOTHERAPY).totalCycles(0).build());

        ArgumentCaptor<Treatment> captor = ArgumentCaptor.forClass(Treatment.class);
        verify(treatmentRepository).save(captor.capture());
        assertThat(captor.getValue().getMedications()).isEqualTo("[]");
        assertThat(captor.getValue().getProgressPercentage()).isZero();
    }
}
