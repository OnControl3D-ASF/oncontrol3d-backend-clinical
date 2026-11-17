package com.oncontrol.oncontrolbackend.symptoms.infrastructure.controller;

import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import com.oncontrol.oncontrolbackend.symptoms.application.dto.SymptomResponse;
import com.oncontrol.oncontrolbackend.symptoms.application.service.SymptomService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SymptomControllerTest {

    private static final String BODY = """
            {"symptomName":"Nauseas","severity":"SEVERE","occurrenceDate":"2026-11-10",
             "occurrenceTime":"08:30:00","requiresMedicalAttention":true}
            """;

    @Mock
    private SymptomService symptomService;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private SymptomController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void patientExists() {
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
    }

    private static SymptomResponse symptom() {
        return SymptomResponse.builder().id(1L).symptomName("Nauseas").severity("SEVERA").build();
    }

    @Test
    void reportSymptom_returns201AndAcceptsEnglishSeverityAlias() throws Exception {
        patientExists();
        when(symptomService.reportSymptom(any(), any(Profile.class))).thenReturn(symptom());

        mockMvc.perform(post("/api/symptoms/patient/{id}", Fixtures.PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symptom.severity").value("SEVERA"))
                .andExpect(jsonPath("$.message").value("Symptom reported successfully"));
    }

    @Test
    void reportSymptom_rejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/symptoms/patient/{id}", Fixtures.PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"symptomName\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(symptomService);
    }

    @Test
    void reportSymptom_unknownPatientReturns500WithMessage() throws Exception {
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/symptoms/patient/{id}", Fixtures.PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error reporting symptom: Patient profile not found"));
    }

    @Test
    void reportSymptom_deniedWithoutPatientAccess() {
        doThrow(new AccessDeniedException("no")).when(authorizationService).requirePatientAccess(Fixtures.PATIENT_ID);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/symptoms/patient/{id}", Fixtures.PATIENT_ID)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)))
                .hasCauseInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(symptomService);
    }

    @Test
    void getPatientSymptoms_returnsListAndCount() throws Exception {
        patientExists();
        when(symptomService.getPatientSymptoms(any(Profile.class), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30))))
                .thenReturn(List.of(symptom(), symptom()));

        mockMvc.perform(get("/api/symptoms/patient/{id}", Fixtures.PATIENT_ID)
                        .param("startDate", "2026-11-01").param("endDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.symptoms[0].symptomName").value("Nauseas"));
    }

    @Test
    void getPatientSymptoms_errorReturns500() throws Exception {
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/symptoms/patient/{id}", Fixtures.PATIENT_ID))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getRecentSymptoms_defaultsToSevenDays() throws Exception {
        patientExists();
        when(symptomService.getRecentSymptoms(any(Profile.class), eq(7))).thenReturn(List.of(symptom()));

        mockMvc.perform(get("/api/symptoms/patient/{id}/recent", Fixtures.PATIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void getRecentSymptoms_errorReturns500() throws Exception {
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/symptoms/patient/{id}/recent", Fixtures.PATIENT_ID))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getSymptomStats_returnsStats() throws Exception {
        patientExists();
        when(symptomService.getSymptomStats(any(Profile.class)))
                .thenReturn(Map.of("totalSymptoms", 5L, "period", "30 days"));

        mockMvc.perform(get("/api/symptoms/patient/{id}/stats", Fixtures.PATIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSymptoms").value(5))
                .andExpect(jsonPath("$.period").value("30 days"));
    }

    @Test
    void getSymptomStats_errorReturns500() throws Exception {
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/symptoms/patient/{id}/stats", Fixtures.PATIENT_ID))
                .andExpect(status().isInternalServerError());
    }
}
