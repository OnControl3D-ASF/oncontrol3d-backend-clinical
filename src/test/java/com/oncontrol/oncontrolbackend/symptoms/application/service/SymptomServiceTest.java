package com.oncontrol.oncontrolbackend.symptoms.application.service;

import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import com.oncontrol.oncontrolbackend.symptoms.application.dto.SymptomRequest;
import com.oncontrol.oncontrolbackend.symptoms.application.dto.SymptomResponse;
import com.oncontrol.oncontrolbackend.symptoms.domain.event.ToxicityAlertRaised;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.Symptom;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.SymptomSeverity;
import com.oncontrol.oncontrolbackend.symptoms.domain.repository.SymptomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SymptomServiceTest {

    @Mock
    private SymptomRepository symptomRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SymptomService service;

    private final Profile patient = Fixtures.patientAccount();

    private static Symptom symptom(SymptomSeverity severity) {
        return Symptom.builder()
                .id(1L)
                .symptomName("Nauseas")
                .severity(severity)
                .occurrenceDate(LocalDate.of(2026, 11, 10))
                .occurrenceTime(LocalTime.of(8, 30))
                .requiresMedicalAttention(false)
                .reportedToDoctor(false)
                .build();
    }

    @Test
    void reportSymptom_persistsAsNotYetReported() {
        when(symptomRepository.save(any(Symptom.class))).thenAnswer(inv -> inv.getArgument(0));
        SymptomRequest request = SymptomRequest.builder()
                .symptomName("Nauseas").severity(SymptomSeverity.SEVERA)
                .occurrenceDate(LocalDate.of(2026, 11, 10)).occurrenceTime(LocalTime.of(8, 30))
                .durationHours(4).notes("Tras quimio").requiresMedicalAttention(true)
                .build();

        SymptomResponse response = service.reportSymptom(request, patient);

        ArgumentCaptor<Symptom> captor = ArgumentCaptor.forClass(Symptom.class);
        verify(symptomRepository).save(captor.capture());
        assertThat(captor.getValue().getProfile()).isSameAs(patient);
        assertThat(response.getSeverity()).isEqualTo("SEVERA");
        assertThat(response.getReportedToDoctor()).isFalse();
        assertThat(response.getRequiresMedicalAttention()).isTrue();
    }

    @Test
    void getPatientSymptoms_usesDateRangeWhenComplete() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);
        when(symptomRepository.findByProfileAndOccurrenceDateBetween(patient, from, to))
                .thenReturn(List.of(symptom(SymptomSeverity.LEVE)));

        assertThat(service.getPatientSymptoms(patient, from, to)).hasSize(1);
    }

    @Test
    void getPatientSymptoms_returnsAllWhenRangeIncomplete() {
        when(symptomRepository.findByProfileOrderByOccurrenceDateDesc(patient))
                .thenReturn(List.of(symptom(SymptomSeverity.LEVE), symptom(SymptomSeverity.CRITICA)));

        assertThat(service.getPatientSymptoms(patient, LocalDate.of(2026, 11, 1), null))
                .extracting(SymptomResponse::getSeverity)
                .containsExactly("LEVE", "CRITICA");
    }

    @Test
    void getRecentSymptoms_queriesFromNDaysAgo() {
        when(symptomRepository.findRecentByProfile(patient, LocalDate.now().minusDays(7)))
                .thenReturn(List.of(symptom(SymptomSeverity.MODERADA)));

        assertThat(service.getRecentSymptoms(patient, 7)).hasSize(1);
    }

    @Test
    void getSymptomStats_reportsLast30Days() {
        when(symptomRepository.countByProfileAndOccurrenceDateAfter(eq(patient), any())).thenReturn(5L);
        when(symptomRepository.countSevereSymptomsByProfile(eq(patient), any())).thenReturn(2L);

        Map<String, Object> stats = service.getSymptomStats(patient);

        assertThat(stats).containsEntry("totalSymptoms", 5L)
                .containsEntry("severeSymptoms", 2L)
                .containsEntry("period", "30 days");
    }

    private SymptomRequest request(SymptomSeverity severity) {
        return SymptomRequest.builder()
                .symptomName("Fiebre").severity(severity)
                .occurrenceDate(LocalDate.of(2026, 11, 10)).occurrenceTime(LocalTime.of(8, 30))
                .build();
    }

    @ParameterizedTest
    @EnumSource(value = SymptomSeverity.class, names = {"SEVERA", "CRITICA"})
    void reportSymptom_raisesToxicityAlertForGrade3And4(SymptomSeverity severity) {
        when(symptomRepository.save(any(Symptom.class))).thenAnswer(inv -> {
            Symptom s = inv.getArgument(0);
            s.setId(77L);
            return s;
        });

        service.reportSymptom(request(severity), patient);

        ArgumentCaptor<ToxicityAlertRaised> event = ArgumentCaptor.forClass(ToxicityAlertRaised.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().symptomId()).isEqualTo(77L);
        assertThat(event.getValue().patientAccountId()).isEqualTo(patient.getId());
        assertThat(event.getValue().severity()).isEqualTo(severity);
        assertThat(event.getValue().reportedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = SymptomSeverity.class, names = {"LEVE", "MODERADA"})
    void reportSymptom_doesNotAlertBelowThreshold(SymptomSeverity severity) {
        when(symptomRepository.save(any(Symptom.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reportSymptom(request(severity), patient);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
