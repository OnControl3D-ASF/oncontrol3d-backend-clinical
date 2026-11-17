package com.oncontrol.oncontrolbackend.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld.Actor;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HU06 - Prescripción y seguimiento de tratamientos oncológicos. */
public class TratamientoSteps {

    private final ScenarioWorld world;
    private final Api api;

    public TratamientoSteps(ScenarioWorld world, Api api) {
        this.world = world;
        this.api = api;
    }

    @Cuando("{string} registra un tratamiento {string} de {int} ciclos para {string}")
    public void registersTreatment(String doctorName, String type, int cycles, String patientName) {
        createTreatment(doctorName, patientName, type, cycles);
    }

    @Dado("que {string} tiene un tratamiento {string} de {int} ciclos")
    public void patientHasTreatment(String patientName, String type, int cycles) {
        MvcResult result = createTreatment(world.actor(patientName).doctorName(), patientName, type, cycles);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Cuando("{string} registra un tratamiento sin tipo para {string}")
    public void registersTreatmentWithoutType(String doctorName, String patientName) {
        Map<String, Object> body = treatmentBody(null, 6);
        api.post(world.actor(doctorName), body, "/api/treatments/doctor/{d}/patient/{p}",
                world.actor(doctorName).doctorId(), world.actor(patientName).patientId());
    }

    @Cuando("{string} registra la sesión del ciclo {int}")
    public void registersSession(String doctorName, int cycle) {
        Map<String, Object> body = Map.of(
                "sessionDate", LocalDateTime.now().minusHours(1).withNano(0).toString(),
                "cycleNumber", cycle);
        MvcResult result = api.post(world.actor(doctorName), body, "/api/treatments/{id}/sessions", world.treatmentId());
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Cuando("{string} suspende el tratamiento por {string}")
    public void suspendsTreatment(String doctorName, String reason) {
        MvcResult result = api.patch(world.actor(doctorName), Map.of("status", "SUSPENDED", "reason", reason),
                "/api/treatments/{id}/status", world.treatmentId());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Entonces("el tratamiento queda en estado {string} en el ciclo {int}")
    public void treatmentStateAndCycle(String status, int cycle) {
        JsonNode treatment = currentTreatment();
        assertThat(treatment.get("status").asText()).isEqualTo(status);
        assertThat(treatment.get("currentCycle").asInt()).isEqualTo(cycle);
    }

    @Entonces("el tratamiento queda en estado {string}")
    public void treatmentState(String status) {
        assertThat(currentTreatment().get("status").asText()).isEqualTo(status);
    }

    @Entonces("el avance del tratamiento es {int}%")
    public void treatmentProgress(int progress) {
        assertThat(currentTreatment().get("progressPercentage").asInt()).isEqualTo(progress);
    }

    @Entonces("las notas del tratamiento incluyen {string}")
    public void treatmentNotes(String text) {
        assertThat(currentTreatment().get("notes").asText()).contains(text);
    }

    @Entonces("{string} no tiene tratamientos registrados")
    public void noTreatments(String patientName) {
        Actor patient = world.actor(patientName);
        MvcResult result = api.get(world.actor(patient.doctorName()), "/api/treatments/patient/{id}", patient.patientId());
        assertThat(api.body(result).get("count").asInt()).isZero();
    }

    private MvcResult createTreatment(String doctorName, String patientName, String type, int cycles) {
        Actor doctor = world.actor(doctorName);
        MvcResult result = api.post(doctor, treatmentBody(type, cycles), "/api/treatments/doctor/{d}/patient/{p}",
                doctor.doctorId(), world.actor(patientName).patientId());
        if (result.getResponse().getStatus() == 201) {
            world.treatment(api.body(result).get("id").asLong(), doctorName);
        }
        return result;
    }

    /** Reads the persisted state back through the API (not the creation response). */
    private JsonNode currentTreatment() {
        MvcResult lastAction = world.lastResult();
        MvcResult result = api.get(world.actor(world.treatingDoctor()), "/api/treatments/{id}", world.treatmentId());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = api.body(result);
        world.lastResult(lastAction);
        return body;
    }

    private static Map<String, Object> treatmentBody(String type, int cycles) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("protocol", "Esquema estandar");
        body.put("totalCycles", cycles);
        body.put("startDate", LocalDate.now().toString());
        return body;
    }
}
