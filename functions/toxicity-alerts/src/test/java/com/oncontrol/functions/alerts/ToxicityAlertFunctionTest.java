package com.oncontrol.functions.alerts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/** Wiring of the Function entry point with the real log notifier. */
class ToxicityAlertFunctionTest {

    private static String fixture() throws IOException {
        try (InputStream in = ToxicityAlertFunctionTest.class.getResourceAsStream("/toxicity-alert-v1.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void criticalAlertProducesHighPriorityLogForTheDoctor() throws IOException {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        ToxicityAlertProcessorTest.RecordingTelemetry telemetry = new ToxicityAlertProcessorTest.RecordingTelemetry();

        ToxicityAlertProcessor.Outcome outcome = ToxicityAlertFunction.process(fixture(), logger, telemetry);

        assertThat(outcome).isEqualTo(ToxicityAlertProcessor.Outcome.NOTIFIED);
        assertThat(records).anySatisfy(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.WARNING);
            assertThat(record.getMessage())
                    .contains("[TOXICITY ALERT] CRITICA Fiebre neutropenica")
                    .contains("dr.garcia@hospital.com")
                    .contains("https://app.example/medico/pacientes/20");
        });
        assertThat(telemetry.single(AlertTelemetry.NOTIFIED).properties()).containsEntry("channel", "log");
    }
}
