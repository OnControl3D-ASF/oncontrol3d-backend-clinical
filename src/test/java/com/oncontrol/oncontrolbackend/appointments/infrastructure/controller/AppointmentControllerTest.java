package com.oncontrol.oncontrolbackend.appointments.infrastructure.controller;

import com.oncontrol.oncontrolbackend.appointments.application.dto.AppointmentResponse;
import com.oncontrol.oncontrolbackend.appointments.application.service.AppointmentService;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentStatus;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.support.Fixtures;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {

    private static final String CREATE_BODY = """
            {"appointmentDate":"2026-12-01T10:00:00","type":"PRIMERA_CONSULTA","location":"Consultorio 2"}
            """;

    @Mock
    private AppointmentService appointmentService;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private AppointmentController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static AppointmentResponse appointment() {
        return AppointmentResponse.builder().id(3L).status(AppointmentStatus.SCHEDULED)
                .appointmentDate(LocalDateTime.of(2026, 12, 1, 10, 0)).build();
    }

    @Test
    void createAppointment_returns201() throws Exception {
        when(appointmentService.createAppointment(eq(Fixtures.DOCTOR_ID), eq(Fixtures.PATIENT_ID), any())).thenReturn(appointment());

        mockMvc.perform(post("/api/appointments/doctor/{d}/patient/{p}", Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointment.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.message").value("Appointment created successfully"));
    }

    @Test
    void createAppointment_serviceErrorReturns500() throws Exception {
        when(appointmentService.createAppointment(any(), any(), any())).thenThrow(new RuntimeException("Doctor profile not found"));

        mockMvc.perform(post("/api/appointments/doctor/{d}/patient/{p}", Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error creating appointment: Doctor profile not found"));
    }

    @Test
    void accessDeniedIsNeverSwallowedInto500() {
        when(appointmentService.getAppointmentById(3L)).thenThrow(new AccessDeniedException("no"));

        assertThatThrownBy(() -> mockMvc.perform(get("/api/appointments/3")))
                .hasCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createAppointment_requiresDoctorOwnership() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("no")).when(authorizationService).requireDoctor(Fixtures.DOCTOR_ID);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/appointments/doctor/{d}/patient/{p}", Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)))
                .hasCauseInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(appointmentService);
    }

    @Test
    void listings_returnAppointmentsAndCount() throws Exception {
        when(appointmentService.getAppointmentsByDoctor(Fixtures.DOCTOR_ID)).thenReturn(List.of(appointment()));
        when(appointmentService.getAppointmentsByPatient(Fixtures.PATIENT_ID)).thenReturn(List.of(appointment(), appointment()));

        mockMvc.perform(get("/api/appointments/doctor/{id}", Fixtures.DOCTOR_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/appointments/patient/{id}", Fixtures.PATIENT_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void listings_errorsReturn500() throws Exception {
        when(appointmentService.getAppointmentsByDoctor(anyLong())).thenThrow(new RuntimeException("boom"));
        when(appointmentService.getAppointmentsByPatient(anyLong())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/appointments/doctor/{id}", Fixtures.DOCTOR_ID)).andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/appointments/patient/{id}", Fixtures.PATIENT_ID)).andExpect(status().isInternalServerError());
    }

    @Test
    void getById_okOr404() throws Exception {
        when(appointmentService.getAppointmentById(3L)).thenReturn(appointment());
        when(appointmentService.getAppointmentById(4L)).thenThrow(new RuntimeException("Appointment not found"));

        mockMvc.perform(get("/api/appointments/3")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(3));
        mockMvc.perform(get("/api/appointments/4")).andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_passesEnumAndOptionalReason() throws Exception {
        when(appointmentService.updateAppointmentStatus(3L, AppointmentStatus.CANCELLED, "Fiebre")).thenReturn(appointment());
        when(appointmentService.updateAppointmentStatus(eq(4L), eq(AppointmentStatus.COMPLETED), isNull()))
                .thenThrow(new RuntimeException("Appointment not found"));

        mockMvc.perform(patch("/api/appointments/3/status").param("status", "CANCELLED").param("reason", "Fiebre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment status updated successfully"));
        mockMvc.perform(patch("/api/appointments/4/status").param("status", "COMPLETED"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void followUp_okOr500() throws Exception {
        when(appointmentService.addFollowUpNotes(3L, "Repetir TAC")).thenReturn(appointment());
        when(appointmentService.addFollowUpNotes(4L, "x")).thenThrow(new RuntimeException("Appointment not found"));

        mockMvc.perform(patch("/api/appointments/3/follow-up").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Repetir TAC\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/appointments/4/follow-up").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"x\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void reschedule_validatesDateAndDelegates() throws Exception {
        when(appointmentService.rescheduleAppointment(3L, LocalDateTime.of(2026, 12, 8, 10, 0), "Viaje")).thenReturn(appointment());

        mockMvc.perform(patch("/api/appointments/3/reschedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAppointmentDate\":\"2026-12-08T10:00:00\",\"reason\":\"Viaje\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment rescheduled successfully"));
        mockMvc.perform(patch("/api/appointments/3/reschedule").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New appointment date is required"));
        mockMvc.perform(patch("/api/appointments/3/reschedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAppointmentDate\":\"not-a-date\"}"))
                .andExpect(status().isInternalServerError());
    }
}
