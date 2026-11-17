package com.oncontrol.functions.alerts;

import com.oncontrol.functions.alerts.ToxicityAlertProcessor.InvalidAlertException;
import com.oncontrol.functions.alerts.ToxicityAlertProcessor.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToxicityAlertProcessorTest {

    private static final Instant REPORTED_AT = Instant.parse("2026-11-17T13:00:00Z");

    private final List<ToxicityAlert> notified = new ArrayList<>();
    private final RecordingTelemetry telemetry = new RecordingTelemetry();
    private ToxicityAlertProcessor processor;

    @BeforeEach
    void setUp() {
        processor = processorAt(REPORTED_AT.plusSeconds(3));
    }

    private ToxicityAlertProcessor processorAt(Instant now) {
        AlertNotifier notifier = alert -> {
            notified.add(alert);
            return "test";
        };
        return new ToxicityAlertProcessor(notifier, telemetry, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String fixture() throws IOException {
        try (InputStream in = ToxicityAlertProcessorTest.class.getResourceAsStream("/toxicity-alert-v1.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void criticalAlertNotifiesTheTreatingOncologist() throws IOException {
        Outcome outcome = processor.process(fixture());

        assertThat(outcome).isEqualTo(Outcome.NOTIFIED);
        assertThat(notified).singleElement().satisfies(alert -> {
            assertThat(alert.doctorEmail()).isEqualTo("dr.garcia@hospital.com");
            assertThat(alert.severity()).isEqualTo("CRITICA");
            assertThat(alert.patientProfileId()).isEqualTo(20L);
            assertThat(alert.occurrenceTime()).hasToString("08:00");
        });
    }

    @Test
    void notificationTelemetryMeasuresLatencyAgainstThe60sSla() throws IOException {
        processor.process(fixture());

        RecordingTelemetry.Event event = telemetry.single(AlertTelemetry.NOTIFIED);
        assertThat(event.metrics()).containsEntry("latencyMs", 3000.0);
        assertThat(event.properties())
                .containsEntry("withinSla", "true")
                .containsEntry("channel", "test")
                .containsEntry("eventId", "7b6f2b8e-0d2b-4a8e-9a55-0b1a2c3d4e5f")
                .doesNotContainKey("doctorEmail");
    }

    @Test
    void lateNotificationIsFlaggedOutsideSla() throws IOException {
        processorAt(REPORTED_AT.plusSeconds(90)).process(fixture());

        assertThat(telemetry.single(AlertTelemetry.NOTIFIED).properties()).containsEntry("withinSla", "false");
    }

    @Test
    void severeAlertAlsoNotifies() throws IOException {
        String severe = fixture().replace("\"CRITICA\"", "\"SEVERA\"");

        assertThat(processor.process(severe)).isEqualTo(Outcome.NOTIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEVE", "MODERADA"})
    void lowerGradesAreIgnoredDefensively(String severity) throws IOException {
        String low = fixture().replace("\"CRITICA\"", "\"" + severity + "\"");

        assertThat(processor.process(low)).isEqualTo(Outcome.IGNORED);
        assertThat(notified).isEmpty();
        assertThat(telemetry.single(AlertTelemetry.IGNORED).properties()).containsEntry("severity", severity);
    }

    @Test
    void unknownFieldsFromNewerProducersAreIgnored() throws IOException {
        String extended = fixture().replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"triage\": \"red\",");

        assertThat(processor.process(extended)).isEqualTo(Outcome.NOTIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-json", "{\"schemaVersion\": 1,"})
    void malformedMessagesAreRejectedForRetryAndDeadLetter(String body) {
        assertThatThrownBy(() -> processor.process(body)).isInstanceOf(InvalidAlertException.class);
        assertThat(notified).isEmpty();
        assertThat(telemetry.single(AlertTelemetry.REJECTED).properties()).containsKey("reason");
    }

    @Test
    void nullMessageIsRejected() {
        assertThatThrownBy(() -> processor.process(null)).isInstanceOf(InvalidAlertException.class);
    }

    @Test
    void unsupportedSchemaVersionIsRejected() throws IOException {
        String v2 = fixture().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        assertThatThrownBy(() -> processor.process(v2))
                .isInstanceOf(InvalidAlertException.class)
                .hasMessageContaining("unsupported schemaVersion 2");
        assertThat(telemetry.single(AlertTelemetry.REJECTED).properties())
                .containsEntry("eventId", "7b6f2b8e-0d2b-4a8e-9a55-0b1a2c3d4e5f");
    }

    @Test
    void missingRecipientIsRejected() throws IOException {
        String noDoctor = fixture().replace("\"dr.garcia@hospital.com\"", "null");

        assertThatThrownBy(() -> processor.process(noDoctor)).hasMessageContaining("doctorEmail is required");
    }

    @Test
    void invalidRecipientIsRejected() throws IOException {
        String badEmail = fixture().replace("\"dr.garcia@hospital.com\"", "\"dr.garcia\"");

        assertThatThrownBy(() -> processor.process(badEmail)).hasMessageContaining("not an e-mail address");
    }

    @Test
    void everyRequiredFieldIsValidated() {
        String empty = "{\"schemaVersion\": 1}";

        assertThatThrownBy(() -> processor.process(empty))
                .hasMessageContaining("eventId is required")
                .hasMessageContaining("reportedAt is required")
                .hasMessageContaining("symptomId is required")
                .hasMessageContaining("severity is required")
                .hasMessageContaining("patientProfileId is required");
    }

    /** In-memory telemetry for assertions. */
    static class RecordingTelemetry implements AlertTelemetry {

        record Event(String name, Map<String, String> properties, Map<String, Double> metrics) {
        }

        final List<Event> events = new ArrayList<>();

        @Override
        public void trackEvent(String name, Map<String, String> properties, Map<String, Double> metrics) {
            events.add(new Event(name, properties, metrics));
        }

        Event single(String name) {
            List<Event> matching = events.stream().filter(e -> e.name().equals(name)).toList();
            assertThat(matching).as("events named %s", name).hasSize(1);
            return matching.get(0);
        }
    }
}
