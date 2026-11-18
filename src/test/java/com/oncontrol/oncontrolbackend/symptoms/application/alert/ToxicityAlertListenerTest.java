package com.oncontrol.oncontrolbackend.symptoms.application.alert;

import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import com.oncontrol.oncontrolbackend.symptoms.domain.event.ToxicityAlertRaised;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.SymptomSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToxicityAlertListenerTest {

    private static final Instant REPORTED_AT = Instant.parse("2026-11-17T13:00:00Z");

    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private ToxicityAlertPublisher publisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ToxicityAlertListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        listener = new ToxicityAlertListener(patientProfileRepository, publisher, transactionManager,
                "https://app.example/medico/pacientes/{patientProfileId}");
    }

    private static ToxicityAlertRaised event() {
        return new ToxicityAlertRaised(77L, Fixtures.PATIENT_PROFILE_ACCOUNT_ID, "Fiebre neutropenica",
                SymptomSeverity.CRITICA, LocalDate.of(2026, 11, 17), LocalTime.of(8, 0), REPORTED_AT);
    }

    @Test
    void publishesMessageAddressedToTheAssignedDoctor() {
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID))
                .thenReturn(Optional.of(Fixtures.patient()));

        listener.onToxicityAlert(event());

        ArgumentCaptor<ToxicityAlertMessage> captor = ArgumentCaptor.forClass(ToxicityAlertMessage.class);
        verify(publisher).publish(captor.capture());
        ToxicityAlertMessage message = captor.getValue();
        assertThat(message.schemaVersion()).isEqualTo(1);
        assertThat(message.eventId()).hasSize(36);
        assertThat(message.severity()).isEqualTo("CRITICA");
        assertThat(message.symptomId()).isEqualTo(77L);
        assertThat(message.reportedAt()).isEqualTo(REPORTED_AT);
        assertThat(message.patientProfileId()).isEqualTo(Fixtures.PATIENT_ID);
        assertThat(message.doctorProfileId()).isEqualTo(Fixtures.DOCTOR_ID);
        assertThat(message.doctorEmail()).isEqualTo("dr.garcia@hospital.com");
        assertThat(message.doctorName()).isEqualTo("Carlos Garcia");
        assertThat(message.caseUrl()).isEqualTo("https://app.example/medico/pacientes/" + Fixtures.PATIENT_ID);
    }

    @Test
    void caseUrlIsOmittedWhenNoTemplateIsConfigured() {
        ToxicityAlertListener withoutUrl = new ToxicityAlertListener(patientProfileRepository, publisher, transactionManager, "");
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID))
                .thenReturn(Optional.of(Fixtures.patient()));

        assertThat(withoutUrl.buildMessage(event())).get().extracting(ToxicityAlertMessage::caseUrl).isNull();
    }

    @Test
    void skipsPatientsWithoutAssignedDoctor() {
        PatientProfile orphan = Fixtures.patient(null);
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(Optional.of(orphan));

        listener.onToxicityAlert(event());

        verify(publisher, never()).publish(any());
    }

    @Test
    void skipsUnknownPatients() {
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(Optional.empty());

        listener.onToxicityAlert(event());

        verify(publisher, never()).publish(any());
    }

    @Test
    void publishingFailureNeverPropagates() {
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID))
                .thenReturn(Optional.of(Fixtures.patient()));
        doThrow(new IllegalStateException("Service Bus unavailable")).when(publisher).publish(any());

        assertThatCode(() -> listener.onToxicityAlert(event())).doesNotThrowAnyException();
    }
}
