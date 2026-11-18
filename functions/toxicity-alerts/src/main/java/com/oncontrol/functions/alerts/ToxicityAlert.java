package com.oncontrol.functions.alerts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ToxicityAlertMessage v1 as published by the Clinical Service
 * (contract: docs/contracts/toxicity-alert-v1.md). Unknown fields are ignored so
 * the producer can add optional fields without breaking this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToxicityAlert(
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

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** CTCAE grade 3-4 on the severity scale: the only severities that notify the oncologist. */
    static final Set<String> ALERT_SEVERITIES = Set.of("SEVERA", "CRITICA");

    /** @return contract violations; empty when the message is valid. */
    public List<String> violations() {
        List<String> errors = new ArrayList<>();
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            errors.add("unsupported schemaVersion " + schemaVersion);
        }
        require(errors, eventId, "eventId");
        require(errors, reportedAt, "reportedAt");
        require(errors, symptomId, "symptomId");
        require(errors, severity, "severity");
        require(errors, patientProfileId, "patientProfileId");
        require(errors, doctorEmail, "doctorEmail");
        if (doctorEmail != null && !doctorEmail.contains("@")) {
            errors.add("doctorEmail is not an e-mail address");
        }
        return errors;
    }

    public boolean requiresNotification() {
        return severity != null && ALERT_SEVERITIES.contains(severity);
    }

    private static void require(List<String> errors, Object value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            errors.add(field + " is required");
        }
    }
}
