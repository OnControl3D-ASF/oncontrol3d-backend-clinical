package com.oncontrol.oncontrolbackend.profiles.infrastructure.controller;

import com.oncontrol.oncontrolbackend.iam.application.service.OrganizationOnboardingService;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import com.oncontrol.oncontrolbackend.appointments.application.service.AppointmentService;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.symptoms.application.service.SymptomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auth service outage while creating doctors/patients surfaces as 503, duplicates as 400. */
@ExtendWith(MockitoExtension.class)
class CreateAccountEndpointsTest {

    private static final String DOCTOR_BODY = """
            {"email":"doc@h.com","password":"Secret123!","firstName":"Ana","lastName":"Rojas",
             "specialization":"Oncologia","licenseNumber":"CMP-1"}
            """;
    private static final String PATIENT_BODY = """
            {"email":"pat@h.com","password":"Secret123!","firstName":"Luis","lastName":"Soto"}
            """;

    @Mock
    private ProfileService profileService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private OrganizationOnboardingService onboardingService;
    @Mock
    private AppointmentService appointmentService;
    @Mock
    private SymptomService symptomService;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OrganizationController(profileService, authorizationService, onboardingService),
                new DoctorController(profileService, appointmentService, symptomService, profileRepository,
                        patientProfileRepository, authorizationService)).build();
    }

    @Test
    void createDoctor_authUnavailable_returns503() throws Exception {
        when(profileService.createDoctor(eq(1L), any())).thenThrow(new IdentityUnavailableException("down", null));

        mockMvc.perform(post("/api/organizations/1/doctors").contentType(MediaType.APPLICATION_JSON).content(DOCTOR_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Identity service unavailable, try again later"));
    }

    @Test
    void createPatient_authUnavailable_returns503() throws Exception {
        when(profileService.createPatient(eq(7L), any())).thenThrow(new IdentityUnavailableException("down", null));

        mockMvc.perform(post("/api/doctors/7/patients").contentType(MediaType.APPLICATION_JSON).content(PATIENT_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Identity service unavailable, try again later"));
    }

    @Test
    void createDoctorAndPatient_duplicatedEmail_returns400() throws Exception {
        when(profileService.createDoctor(eq(1L), any())).thenThrow(new IllegalArgumentException("Email already exists"));
        when(profileService.createPatient(eq(7L), any())).thenThrow(new IllegalArgumentException("Email already exists"));

        mockMvc.perform(post("/api/organizations/1/doctors").contentType(MediaType.APPLICATION_JSON).content(DOCTOR_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists"));
        mockMvc.perform(post("/api/doctors/7/patients").contentType(MediaType.APPLICATION_JSON).content(PATIENT_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }
}
