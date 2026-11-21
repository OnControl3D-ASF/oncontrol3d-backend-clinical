package com.oncontrol.oncontrolbackend.profiles.infrastructure.controller;

import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.AuthenticatedPrincipal;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.PrincipalRole;
import com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/me = the legacy login body minus the token, resolved from claims (either issuer). */
@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileService profileService;

    @InjectMocks
    private MeController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static UsernamePasswordAuthenticationToken authenticated(AuthenticatedPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void organization_getsFlatOrganizationBody() throws Exception {
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(Fixtures.organization()));
        var principal = new AuthenticatedPrincipal(PrincipalRole.ORGANIZATION, "org@hospital.com", Fixtures.ORG_ID, null, null, null);

        mockMvc.perform(get("/api/me").principal(authenticated(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORGANIZATION"))
                .andExpect(jsonPath("$.id").value(Fixtures.ORG_ID))
                .andExpect(jsonPath("$.email").value("org@hospital.com"))
                .andExpect(jsonPath("$.organizationName").value("Hospital Central"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void doctor_getsProfileWithSpecializationAndLicense() throws Exception {
        when(profileService.getDoctorProfileById(7L)).thenReturn(DoctorProfileResponse.builder()
                .id(7L).email("doc@h.com").specialization("Oncologia").licenseNumber("CMP-1").build());
        var principal = new AuthenticatedPrincipal(PrincipalRole.DOCTOR, "doc@h.com", 1L, 5L, 7L, null);

        mockMvc.perform(get("/api/me").principal(authenticated(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.specialization").value("Oncologia"))
                .andExpect(jsonPath("$.profile.licenseNumber").value("CMP-1"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void patient_getsPatientProfileWithDoctorId() throws Exception {
        when(profileService.getPatientProfileById(12L)).thenReturn(PatientProfileResponse.builder()
                .id(12L).email("pat@h.com").doctorProfileId(7L).build());
        var principal = new AuthenticatedPrincipal(PrincipalRole.PATIENT, "pat@h.com", 1L, 9L, 7L, 12L);

        mockMvc.perform(get("/api/me").principal(authenticated(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.doctorProfileId").value(7))
                .andExpect(jsonPath("$.profile.specialization").doesNotExist());
    }

    @Test
    void withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedAccount_returns404() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        var principal = new AuthenticatedPrincipal(PrincipalRole.ORGANIZATION, "gone@h.com", 99L, null, null, null);

        mockMvc.perform(get("/api/me").principal(authenticated(principal)))
                .andExpect(status().isNotFound());
    }
}
