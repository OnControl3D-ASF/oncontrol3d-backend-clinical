package com.oncontrol.functions.alerts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Framework-free core of the Function: parse -> validate -> threshold -> notify -> telemetry.
 *
 * <p>Invalid messages throw {@link InvalidAlertException}: the Service Bus trigger then
 * retries up to the queue's maxDeliveryCount and finally dead-letters the message, so a
 * malformed alert is never silently lost.
 */
public class ToxicityAlertProcessor {

    /** HU11 acceptance criterion: the oncologist is notified within 60 seconds. */
    static final Duration NOTIFICATION_SLA = Duration.ofSeconds(60);

    public enum Outcome { NOTIFIED, IGNORED }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

    private final AlertNotifier notifier;
    private final AlertTelemetry telemetry;
    private final Clock clock;

    public ToxicityAlertProcessor(AlertNotifier notifier, AlertTelemetry telemetry, Clock clock) {
        this.notifier = notifier;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    public Outcome process(String body) {
        ToxicityAlert alert = parse(body);

        List<String> violations = alert.violations();
        if (!violations.isEmpty()) {
            reject(String.join("; ", violations), alert.eventId());
        }

        if (!alert.requiresNotification()) {
            // Defensive: the producer only publishes SEVERA/CRITICA, but the consumer
            // must not page an oncologist for a lower grade if that ever changes.
            telemetry.trackEvent(AlertTelemetry.IGNORED, properties(alert), Map.of());
            return Outcome.IGNORED;
        }

        String channel = notifier.notify(alert);

        long latencyMs = Math.max(0, Duration.between(alert.reportedAt(), clock.instant()).toMillis());
        Map<String, String> properties = properties(alert);
        properties.put("channel", channel);
        properties.put("withinSla", String.valueOf(latencyMs <= NOTIFICATION_SLA.toMillis()));
        telemetry.trackEvent(AlertTelemetry.NOTIFIED, properties, Map.of("latencyMs", (double) latencyMs));
        return Outcome.NOTIFIED;
    }

    private ToxicityAlert parse(String body) {
        if (body == null || body.isBlank()) {
            reject("empty message", null);
        }
        try {
            return objectMapper.readValue(body, ToxicityAlert.class);
        } catch (JsonProcessingException e) {
            reject("malformed JSON: " + e.getOriginalMessage(), null);
            return null; // unreachable
        }
    }

    private void reject(String reason, String eventId) {
        Map<String, String> properties = new HashMap<>();
        properties.put("reason", reason);
        if (eventId != null) {
            properties.put("eventId", eventId);
        }
        telemetry.trackEvent(AlertTelemetry.REJECTED, properties, Map.of());
        throw new InvalidAlertException(reason);
    }

    /** Identifiers only: no patient name or clinical free text in telemetry. */
    private static Map<String, String> properties(ToxicityAlert alert) {
        Map<String, String> properties = new HashMap<>();
        properties.put("eventId", alert.eventId());
        properties.put("severity", alert.severity());
        properties.put("symptomId", String.valueOf(alert.symptomId()));
        properties.put("patientProfileId", String.valueOf(alert.patientProfileId()));
        properties.put("doctorProfileId", String.valueOf(alert.doctorProfileId()));
        return properties;
    }

    public static class InvalidAlertException extends RuntimeException {
        public InvalidAlertException(String message) {
            super("Invalid toxicity alert: " + message);
        }
    }
}
