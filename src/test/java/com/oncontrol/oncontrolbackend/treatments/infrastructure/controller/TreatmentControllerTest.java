package com.oncontrol.oncontrolbackend.treatments.infrastructure.controller;

import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.TreatmentStatus;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import com.oncontrol.oncontrolbackend.treatments.application.dto.TreatmentResponse;
import com.oncontrol.oncontrolbackend.treatments.application.dto.TreatmentSessionResponse;
import com.oncontrol.oncontrolbackend.treatments.application.dto.TreatmentStatsResponse;
import com.oncontrol.oncontrolbackend.treatments.application.service.TreatmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TreatmentControllerTest {

    private static final String CREATE_BODY = """
            {"type":"CHEMOTHERAPY","protocol":"FOLFOX","totalCycles":6,"startDate":"2026-12-01"}
            """;
    private static final String SESSION_BODY = """
            {"sessionDate":"2026-12-05T09:00:00","cycleNumber":1}
            """;

    @Mock
    private TreatmentService treatmentService;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private TreatmentController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static TreatmentResponse treatment() {
        return TreatmentResponse.builder().id(5L).protocol("FOLFOX").status(TreatmentStatus.ACTIVE).currentCycle(1).build();
    }

    @Test
    void createTreatment_returns201() throws Exception {
        when(treatmentService.createTreatment(eq(Fixtures.DOCTOR_ID), eq(Fixtures.PATIENT_ID), any())).thenReturn(treatment());

        mockMvc.perform(post("/api/treatments/doctor/{d}/patient/{p}", Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocol").value("FOLFOX"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createTreatment_returns400OnDomainError() throws Exception {
        when(treatmentService.createTreatment(any(), any(), any())).thenThrow(new IllegalArgumentException("Patient not found"));

        mockMvc.perform(post("/api/treatments/doctor/{d}/patient/{p}", Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Patient not found"));
    }

    @Test
    void createTreatment_requiresDoctorOwnership() {
        doThrow(new AccessDeniedException("no")).when(authorizationService).requireDoctor(Fixtures.DOCTOR_ID);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/treatments/doctor/{d}/patient/{p}", Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)))
                .hasCauseInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(treatmentService);
    }

    @Test
    void getTreatment_okOr404() throws Exception {
        when(treatmentService.getTreatmentById(5L)).thenReturn(treatment());
        when(treatmentService.getTreatmentById(6L)).thenThrow(new IllegalArgumentException("Treatment not found"));

        mockMvc.perform(get("/api/treatments/5")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
        mockMvc.perform(get("/api/treatments/6")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Treatment not found"));
    }

    @Test
    void listEndpoints_returnTreatmentsAndCount() throws Exception {
        when(treatmentService.getTreatmentsByDoctor(Fixtures.DOCTOR_ID)).thenReturn(List.of(treatment()));
        when(treatmentService.getTreatmentsByPatient(Fixtures.PATIENT_ID)).thenReturn(List.of(treatment(), treatment()));

        mockMvc.perform(get("/api/treatments/doctor/{id}", Fixtures.DOCTOR_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/treatments/patient/{id}", Fixtures.PATIENT_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void getCurrentTreatment_okOr404() throws Exception {
        when(treatmentService.getCurrentTreatmentByPatient(Fixtures.PATIENT_ID)).thenReturn(treatment());
        mockMvc.perform(get("/api/treatments/patient/{id}/current", Fixtures.PATIENT_ID)).andExpect(status().isOk());

        when(treatmentService.getCurrentTreatmentByPatient(Fixtures.PATIENT_ID))
                .thenThrow(new IllegalArgumentException("No active treatment found for patient"));
        mockMvc.perform(get("/api/treatments/patient/{id}/current", Fixtures.PATIENT_ID)).andExpect(status().isNotFound());
    }

    @Test
    void updateTreatment_okOr400() throws Exception {
        when(treatmentService.updateTreatment(eq(5L), any())).thenReturn(treatment());
        when(treatmentService.updateTreatment(eq(6L), any())).thenThrow(new IllegalArgumentException("Treatment not found"));

        mockMvc.perform(put("/api/treatments/5").contentType(MediaType.APPLICATION_JSON).content("{\"currentCycle\":2}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/treatments/6").contentType(MediaType.APPLICATION_JSON).content("{\"currentCycle\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTreatmentStatus_okOr400() throws Exception {
        String body = "{\"status\":\"SUSPENDED\",\"reason\":\"Neutropenia\"}";
        when(treatmentService.updateTreatmentStatus(eq(5L), any())).thenReturn(treatment());
        when(treatmentService.updateTreatmentStatus(eq(6L), any())).thenThrow(new IllegalArgumentException("Treatment not found"));

        mockMvc.perform(patch("/api/treatments/5/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/treatments/6/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerSession_returns201Or400() throws Exception {
        when(treatmentService.registerSession(eq(5L), any()))
                .thenReturn(TreatmentSessionResponse.builder().id(1L).sessionNumber(1).build());
        when(treatmentService.registerSession(eq(6L), any())).thenThrow(new IllegalArgumentException("Treatment not found"));

        mockMvc.perform(post("/api/treatments/5/sessions").contentType(MediaType.APPLICATION_JSON).content(SESSION_BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sessionNumber").value(1));
        mockMvc.perform(post("/api/treatments/6/sessions").contentType(MediaType.APPLICATION_JSON).content(SESSION_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sessionListings_returnSessionsAndCount() throws Exception {
        TreatmentSessionResponse session = TreatmentSessionResponse.builder().id(1L).build();
        when(treatmentService.getSessionsByTreatment(5L)).thenReturn(List.of(session));
        when(treatmentService.getUpcomingSessionsByPatient(Fixtures.PATIENT_ID)).thenReturn(List.of(session, session));

        mockMvc.perform(get("/api/treatments/5/sessions")).andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/treatments/patient/{id}/sessions/upcoming", Fixtures.PATIENT_ID))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void getTreatmentStats_returnsStats() throws Exception {
        when(treatmentService.getTreatmentStats(Fixtures.DOCTOR_ID))
                .thenReturn(TreatmentStatsResponse.builder().active(3L).completed(1L).build());

        mockMvc.perform(get("/api/treatments/doctor/{id}/stats", Fixtures.DOCTOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(3));
    }
}
