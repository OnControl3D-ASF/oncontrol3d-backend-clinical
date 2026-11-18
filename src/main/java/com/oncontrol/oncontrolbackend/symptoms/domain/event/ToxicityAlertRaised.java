package com.oncontrol.oncontrolbackend.symptoms.domain.event;

import com.oncontrol.oncontrolbackend.symptoms.domain.model.SymptomSeverity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Domain event: a patient reported a symptom whose severity reaches the alert
 * threshold (CTCAE grade 3-4, i.e. SEVERA or CRITICA). Published inside the
 * reporting transaction and handled only after it commits.
 *
 * @param patientAccountId id of the patient's {@code Profile} (account), not of {@code PatientProfile}
 */
public record ToxicityAlertRaised(
        Long symptomId,
        Long patientAccountId,
        String symptomName,
        SymptomSeverity severity,
        LocalDate occurrenceDate,
        LocalTime occurrenceTime,
        Instant reportedAt
) {
}
