package com.oncontrol.oncontrolbackend.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld.Actor;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HU07 - Reporte ePRO de síntomas y toxicidad. */
public class SintomaSteps {

    private final ScenarioWorld world;
    private final Api api;

    public SintomaSteps(ScenarioWorld world, Api api) {
        this.world = world;
        this.api = api;
    }

    @Cuando("{string} reporta el síntoma {string} con severidad {string}")
    public void reportsSymptom(String patientName, String symptom, String severity) {
        report(patientName, patientName, symptom, severity, LocalDate.now());
    }

    @Cuando("{string} reporta el síntoma {string} con severidad {string} en el expediente de {string}")
    public void reportsSymptomInOtherRecord(String actorName, String symptom, String severity, String patientName) {
        report(actorName, patientName, symptom, severity, LocalDate.now());
    }

    @Cuando("{string} reporta el síntoma {string} con fecha de mañana")
    public void reportsFutureSymptom(String patientName, String symptom) {
        report(patientName, patientName, symptom, "LEVE", LocalDate.now().plusDays(1));
    }

    @Entonces("el síntoma queda registrado con severidad {string}")
    public void symptomSeverity(String severity) {
        assertThat(api.body(world.lastResult()).at("/symptom/severity").asText()).isEqualTo(severity);
    }

    @Entonces("{string} ve el síntoma {string} con fecha y hora en el expediente de {string}")
    public void doctorSeesSymptom(String doctorName, String symptom, String patientName) {
        MvcResult result = api.get(world.actor(doctorName), "/api/symptoms/patient/{id}", world.actor(patientName).patientId());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode reported = null;
        for (JsonNode node : api.body(result).get("symptoms")) {
            if (symptom.equals(node.get("symptomName").asText())) {
                reported = node;
            }
        }
        assertThat(reported).as("symptom '%s' visible to the doctor", symptom).isNotNull();
        assertThat(reported.get("occurrenceDate").isNull()).isFalse();
        assertThat(reported.get("occurrenceTime").isNull()).isFalse();
    }

    private void report(String actorName, String patientName, String symptom, String severity, LocalDate date) {
        Actor actor = world.actor(actorName);
        Map<String, Object> body = Map.of(
                "symptomName", symptom,
                "severity", severity,
                "occurrenceDate", date.toString(),
                "occurrenceTime", LocalTime.of(8, 30).toString(),
                "requiresMedicalAttention", false);
        api.post(actor, body, "/api/symptoms/patient/{id}", world.actor(patientName).patientId());
    }
}
