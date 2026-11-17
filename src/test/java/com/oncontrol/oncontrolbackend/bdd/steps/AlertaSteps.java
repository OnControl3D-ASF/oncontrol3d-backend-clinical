package com.oncontrol.oncontrolbackend.bdd.steps;

import com.oncontrol.oncontrolbackend.bdd.RecordingToxicityAlertPublisher;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld;
import com.oncontrol.oncontrolbackend.symptoms.application.alert.ToxicityAlertMessage;
import io.cucumber.java.Before;
import io.cucumber.java.es.Entonces;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** HU11 - Alert delivery to the treating oncologist (asynchronous, after commit). */
public class AlertaSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration QUIET_PERIOD = Duration.ofMillis(1500);

    private final ScenarioWorld world;
    private final RecordingToxicityAlertPublisher alerts;

    public AlertaSteps(ScenarioWorld world, RecordingToxicityAlertPublisher alerts) {
        this.world = world;
        this.alerts = alerts;
    }

    @Before
    public void clearAlerts() {
        alerts.clear();
    }

    @Entonces("{string} recibe una alerta de toxicidad {string} por el síntoma {string}")
    public void doctorReceivesAlert(String doctorName, String severity, String symptom) throws InterruptedException {
        String email = world.actor(doctorName).email();
        Predicate<ToxicityAlertMessage> expected = m -> email.equals(m.doctorEmail())
                && severity.equals(m.severity()) && symptom.equals(m.symptomName());

        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (alerts.published().stream().noneMatch(expected) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }

        ToxicityAlertMessage message = alerts.published().stream().filter(expected).findFirst()
                .orElseThrow(() -> new AssertionError("No alert for " + doctorName + " within " + TIMEOUT
                        + ". Published: " + alerts.published()));
        assertThat(message.patientProfileId()).isEqualTo(world.actor(patientOf(doctorName)).patientId());
        assertThat(message.schemaVersion()).isEqualTo(ToxicityAlertMessage.SCHEMA_VERSION);
    }

    @Entonces("{string} no recibe alertas de toxicidad")
    public void doctorReceivesNoAlert(String doctorName) throws InterruptedException {
        String email = world.actor(doctorName).email();
        Thread.sleep(QUIET_PERIOD.toMillis()); // the listener is asynchronous: give it time to (not) publish

        List<ToxicityAlertMessage> forDoctor = alerts.published().stream()
                .filter(m -> email.equals(m.doctorEmail())).toList();
        assertThat(forDoctor).isEmpty();
    }

    /** The scenarios use a single patient per treating doctor. */
    private String patientOf(String doctorName) {
        return world.patientOf(doctorName);
    }
}
