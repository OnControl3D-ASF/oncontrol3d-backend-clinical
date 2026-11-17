package com.oncontrol.oncontrolbackend.symptoms.application.alert;

import com.oncontrol.oncontrolbackend.profiles.domain.model.DoctorProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.symptoms.domain.event.ToxicityAlertRaised;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Turns a committed {@link ToxicityAlertRaised} into a {@link ToxicityAlertMessage}.
 *
 * <ul>
 *   <li>AFTER_COMMIT: an alert is never sent for a report that was rolled back.</li>
 *   <li>@Async: the patient's request does not wait for Service Bus, and a messaging
 *       failure never turns a successfully saved report into an error.</li>
 * </ul>
 */
@Slf4j
@Component
public class ToxicityAlertListener {

    private final PatientProfileRepository patientProfileRepository;
    private final ToxicityAlertPublisher publisher;
    private final TransactionTemplate readOnlyTransaction;
    private final String caseUrlTemplate;

    public ToxicityAlertListener(PatientProfileRepository patientProfileRepository,
                                 ToxicityAlertPublisher publisher,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${app.alerts.case-url-template:}") String caseUrlTemplate) {
        this.patientProfileRepository = patientProfileRepository;
        this.publisher = publisher;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.caseUrlTemplate = caseUrlTemplate;
    }

    @Async("alertExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onToxicityAlert(ToxicityAlertRaised event) {
        try {
            Optional<ToxicityAlertMessage> message = readOnlyTransaction.execute(status -> buildMessage(event));
            if (message == null || message.isEmpty()) {
                log.warn("Toxicity alert for symptom {} skipped: patient account {} has no assigned doctor",
                        event.symptomId(), event.patientAccountId());
                return;
            }
            publisher.publish(message.get());
            log.info("Toxicity alert {} published for symptom {} ({})",
                    message.get().eventId(), event.symptomId(), event.severity());
        } catch (RuntimeException e) {
            log.error("Toxicity alert for symptom {} could not be published: {}", event.symptomId(), e.getMessage(), e);
        }
    }

    Optional<ToxicityAlertMessage> buildMessage(ToxicityAlertRaised event) {
        return patientProfileRepository.findByProfileId(event.patientAccountId())
                .filter(patient -> patient.getDoctorProfile() != null)
                .map(patient -> toMessage(event, patient, patient.getDoctorProfile()));
    }

    private ToxicityAlertMessage toMessage(ToxicityAlertRaised event, PatientProfile patient, DoctorProfile doctor) {
        return new ToxicityAlertMessage(
                ToxicityAlertMessage.SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                event.reportedAt(),
                event.symptomId(),
                event.symptomName(),
                event.severity().name(),
                event.occurrenceDate(),
                event.occurrenceTime(),
                patient.getId(),
                doctor.getId(),
                doctor.getProfile().getFullName(),
                doctor.getProfile().getEmail(),
                caseUrl(patient.getId()));
    }

    private String caseUrl(Long patientProfileId) {
        if (caseUrlTemplate == null || caseUrlTemplate.isBlank()) {
            return null;
        }
        return caseUrlTemplate.replace("{patientProfileId}", String.valueOf(patientProfileId));
    }
}
