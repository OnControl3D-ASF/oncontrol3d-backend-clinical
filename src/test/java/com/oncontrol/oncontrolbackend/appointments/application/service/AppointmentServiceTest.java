package com.oncontrol.oncontrolbackend.appointments.application.service;

import com.oncontrol.oncontrolbackend.appointments.application.dto.AppointmentResponse;
import com.oncontrol.oncontrolbackend.appointments.application.dto.CreateAppointmentRequest;
import com.oncontrol.oncontrolbackend.appointments.domain.model.Appointment;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentStatus;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentType;
import com.oncontrol.oncontrolbackend.appointments.domain.repository.AppointmentRepository;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.HistoryEntryType;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.MedicalHistoryEntry;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicalHistoryRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final LocalDateTime DATE = LocalDateTime.of(2026, 12, 1, 10, 0);

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private MedicalHistoryRepository medicalHistoryRepository;

    @InjectMocks
    private AppointmentService service;

    private static Appointment appointment() {
        return Appointment.builder()
                .id(3L)
                .doctor(Fixtures.doctorAccount())
                .patient(Fixtures.patientAccount())
                .appointmentDate(DATE)
                .durationMinutes(30)
                .type(AppointmentType.CONSULTA_SEGUIMIENTO)
                .status(AppointmentStatus.SCHEDULED)
                .build();
    }

    private void stubFindAndSave(Appointment appointment) {
        when(appointmentRepository.findById(3L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAppointment_appliesDefaultsAndScheduledStatus() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .appointmentDate(DATE).type(AppointmentType.PRIMERA_CONSULTA).build();

        AppointmentResponse response = service.createAppointment(Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID, request);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getSendReminder()).isTrue();
        assertThat(response.getDurationMinutes()).isEqualTo(30);
        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.getDoctorName()).isEqualTo("Carlos Garcia");
        assertThat(response.getPatientName()).isEqualTo("Juan Perez");
    }

    @Test
    void createAppointment_keepsExplicitDurationAndReminder() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponse response = service.createAppointment(Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID,
                CreateAppointmentRequest.builder().appointmentDate(DATE).durationMinutes(60).sendReminder(false).build());

        assertThat(response.getDurationMinutes()).isEqualTo(60);
    }

    @Test
    void createAppointment_failsForUnknownProfiles() {
        when(doctorProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createAppointment(99L, Fixtures.PATIENT_ID, new CreateAppointmentRequest()))
                .hasMessage("Doctor profile not found");

        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createAppointment(Fixtures.DOCTOR_ID, 99L, new CreateAppointmentRequest()))
                .hasMessage("Patient profile not found");
    }

    @Test
    void listByDoctorAndPatient_resolveProfileIds() {
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(appointmentRepository.findByDoctorId(Fixtures.DOCTOR_PROFILE_ACCOUNT_ID)).thenReturn(List.of(appointment()));
        when(appointmentRepository.findByPatientId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(List.of(appointment()));

        assertThat(service.getAppointmentsByDoctor(Fixtures.DOCTOR_ID)).hasSize(1);
        assertThat(service.getAppointmentsByPatient(Fixtures.PATIENT_ID)).hasSize(1);
    }

    @Test
    void getAppointmentById_checksOwnership() {
        when(appointmentRepository.findById(3L)).thenReturn(Optional.of(appointment()));

        assertThat(service.getAppointmentById(3L).getId()).isEqualTo(3L);
        verify(authorizationService).requireProfileOwnership(Fixtures.DOCTOR_PROFILE_ACCOUNT_ID, Fixtures.PATIENT_PROFILE_ACCOUNT_ID);
    }

    @Test
    void getAppointmentById_deniedOrMissing() {
        when(appointmentRepository.findById(3L)).thenReturn(Optional.of(appointment()));
        doThrow(new AccessDeniedException("no")).when(authorizationService).requireProfileOwnership(any(Long[].class));
        assertThatThrownBy(() -> service.getAppointmentById(3L)).isInstanceOf(AccessDeniedException.class);

        when(appointmentRepository.findById(4L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAppointmentById(4L)).hasMessage("Appointment not found");
    }

    @Test
    void cancel_recordsReasonAndTimestamp() {
        Appointment appointment = appointment();
        stubFindAndSave(appointment);

        AppointmentResponse response = service.updateAppointmentStatus(3L, AppointmentStatus.CANCELLED, "Fiebre");

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.getCancellationReason()).isEqualTo("Fiebre");
        assertThat(appointment.getCancelledAt()).isNotNull();
        verify(medicalHistoryRepository, never()).save(any());
    }

    @Test
    void complete_logsConsultationInMedicalHistory() {
        Appointment appointment = appointment();
        appointment.setNotes("Control post ciclo 2");
        stubFindAndSave(appointment);
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(doctorProfileRepository.findByProfileId(Fixtures.DOCTOR_PROFILE_ACCOUNT_ID)).thenReturn(Optional.of(Fixtures.doctor()));

        service.updateAppointmentStatus(3L, AppointmentStatus.COMPLETED, null);

        ArgumentCaptor<MedicalHistoryEntry> entry = ArgumentCaptor.forClass(MedicalHistoryEntry.class);
        verify(medicalHistoryRepository).save(entry.capture());
        assertThat(appointment.getCompletedAt()).isNotNull();
        assertThat(entry.getValue().getType()).isEqualTo(HistoryEntryType.CONSULTATION);
        assertThat(entry.getValue().getTitle()).isEqualTo("Consulta de seguimiento");
        assertThat(entry.getValue().getDescription())
                .isEqualTo("Cita completada con Carlos Garcia. Notas: Control post ciclo 2");
        assertThat(entry.getValue().getDate()).isEqualTo(DATE.toLocalDate());
    }

    @Test
    void complete_withoutPatientDetailSkipsHistory() {
        stubFindAndSave(appointment());
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(Optional.empty());

        AppointmentResponse response = service.updateAppointmentStatus(3L, AppointmentStatus.COMPLETED, null);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(medicalHistoryRepository, never()).save(any());
    }

    @Test
    void complete_historyFailureDoesNotBreakStatusChange() {
        stubFindAndSave(appointment());
        when(patientProfileRepository.findByProfileId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(medicalHistoryRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        AppointmentResponse response = service.updateAppointmentStatus(3L, AppointmentStatus.COMPLETED, null);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void addFollowUpNotes_updatesNotes() {
        stubFindAndSave(appointment());

        assertThat(service.addFollowUpNotes(3L, "Repetir TAC").getFollowUpNotes()).isEqualTo("Repetir TAC");
    }

    @Test
    void reschedule_movesDateAndAppendsReason() {
        Appointment appointment = appointment();
        appointment.setNotes("Traer examenes");
        stubFindAndSave(appointment);
        LocalDateTime newDate = DATE.plusDays(7);

        AppointmentResponse response = service.rescheduleAppointment(3L, newDate, "Paciente viaja");

        assertThat(response.getAppointmentDate()).isEqualTo(newDate);
        assertThat(response.getNotes()).isEqualTo("Traer examenes\nReprogramada: Paciente viaja");
    }

    @Test
    void reschedule_withoutPreviousNotesOrReason() {
        stubFindAndSave(appointment());

        assertThat(service.rescheduleAppointment(3L, DATE, "Agenda").getNotes()).isEqualTo("Reprogramada: Agenda");
        assertThat(service.rescheduleAppointment(3L, DATE, "").getNotes()).isEqualTo("Reprogramada: Agenda");
    }
}
