package com.oncontrol.oncontrolbackend.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld.Actor;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.HistoryEntryType;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.MedicalHistoryEntry;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicalHistoryRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HU05 - Consulta de historial clínico oncológico. */
public class HistorialSteps {

    private final ScenarioWorld world;
    private final Api api;
    private final MedicalHistoryRepository historyRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public HistorialSteps(ScenarioWorld world, Api api, MedicalHistoryRepository historyRepository,
                          PatientProfileRepository patientProfileRepository,
                          DoctorProfileRepository doctorProfileRepository) {
        this.world = world;
        this.api = api;
        this.historyRepository = historyRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @Dado("el historial de {string} contiene las entradas:")
    public void historyContains(String patientName, DataTable table) {
        Actor patient = world.actor(patientName);
        Actor doctor = world.actor(patient.doctorName());
        for (Map<String, String> row : table.asMaps()) {
            historyRepository.save(MedicalHistoryEntry.builder()
                    .patient(patientProfileRepository.findById(patient.patientId()).orElseThrow())
                    .doctor(doctorProfileRepository.findById(doctor.doctorId()).orElseThrow())
                    .type(HistoryEntryType.valueOf(row.get("tipo")))
                    .date(LocalDate.parse(row.get("fecha")))
                    .title(row.get("título"))
                    .isActive(true)
                    .build());
        }
    }

    @Cuando("{string} consulta el historial de {string}")
    public void consultsHistory(String actorName, String patientName) {
        api.get(world.actor(actorName), "/api/medical-history/patient/{id}", world.actor(patientName).patientId());
    }

    @Entonces("el historial se muestra del más reciente al más antiguo:")
    public void historyOrder(DataTable table) {
        List<String> expected = table.asMaps().stream().map(row -> row.get("título")).toList();

        assertThat(titles()).containsExactlyElementsOf(expected);
    }

    @Entonces("el historial tiene {int} entradas")
    public void historySize(int size) {
        assertThat(api.body(world.lastResult()).get("count").asInt()).isEqualTo(size);
        assertThat(titles()).hasSize(size);
    }

    private List<String> titles() {
        List<String> titles = new ArrayList<>();
        for (JsonNode entry : api.body(world.lastResult()).get("entries")) {
            titles.add(entry.get("title").asText());
        }
        return titles;
    }
}
