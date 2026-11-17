package com.oncontrol.oncontrolbackend.symptoms.application.alert;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Integration contract sent to the Service Bus queue and consumed by the
 * toxicity-alerts Azure Function (docs/contracts/toxicity-alert-v1.md).
 *
 * <p>Data minimization: the patient is identified by id only (no name or
 * clinical history); the doctor's e-mail is needed to deliver the alert.
 */
public record ToxicityAlertMessage(
        int schemaVersion,
        String eventId,
        Instant reportedAt,
        Long symptomId,
        String symptomName,
        String severity,
        LocalDate occurrenceDate,
        LocalTime occurrenceTime,
        Long patientProfileId,
        Long doctorProfileId,
        String doctorName,
        String doctorEmail,
        String caseUrl
) {

    public static final int SCHEMA_VERSION = 1;
}
