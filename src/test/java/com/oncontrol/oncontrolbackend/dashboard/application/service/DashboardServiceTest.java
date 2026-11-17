package com.oncontrol.oncontrolbackend.dashboard.application.service;

import com.oncontrol.oncontrolbackend.appointments.domain.model.Appointment;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentStatus;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentType;
import com.oncontrol.oncontrolbackend.appointments.domain.repository.AppointmentRepository;
import com.oncontrol.oncontrolbackend.dashboard.application.dto.DoctorDashboardResponse;
import com.oncontrol.oncontrolbackend.dashboard.application.dto.OrganizationDashboardResponse;
import com.oncontrol.oncontrolbackend.dashboard.application.dto.PatientDashboardResponse;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.Symptom;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.SymptomSeverity;
import com.oncontrol.oncontrolbackend.symptoms.domain.repository.SymptomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProfileService profileService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private SymptomRepository symptomRepository;

    @InjectMocks
    private DashboardService service;

    private final Profile doctorAccount = Fixtures.doctorAccount();
    private final Profile patientAccount = Fixtures.patientAccount();

    private static DoctorProfileResponse doctorResponse(Long id, boolean available) {
        return DoctorProfileResponse.builder().id(id).firstName("Carlos").lastName("Garcia")
                .specialization("Oncologia").organizationId(Fixtures.ORG_ID).organizationName("Hospital Central")
                .isAvailable(available).rating(4.5).isActive(true).build();
    }

    private static PatientProfileResponse patientResponse(Long id, boolean active) {
        return PatientProfileResponse.builder().id(id).firstName("Juan").lastName("Perez")
                .email("juan.perez@mail.com").doctorProfileId(Fixtures.DOCTOR_ID)
                .cancerType("Pulmon").cancerStage("II").treatmentStatus("ACTIVE")
                .diagnosisDate(LocalDate.of(2026, 3, 1)).isActive(active).build();
    }

    private Appointment appointment(LocalDateTime date, AppointmentStatus status) {
        return Appointment.builder().id((long) date.getDayOfYear()).doctor(doctorAccount).patient(patientAccount)
                .appointmentDate(date).status(status).type(AppointmentType.CONSULTA_SEGUIMIENTO).durationMinutes(30)
                .build();
    }

    private static Symptom symptom(SymptomSeverity severity, LocalDate date) {
        return Symptom.builder().symptomName("Fatiga").severity(severity)
                .occurrenceDate(date).occurrenceTime(LocalTime.NOON).build();
    }

    private List<Appointment> mixedAppointments() {
        LocalDateTime now = LocalDateTime.now();
        Appointment completed = appointment(now.minusDays(10), AppointmentStatus.COMPLETED);
        completed.setCompletedAt(now.minusDays(10));
        return List.of(
                appointment(now.plusDays(2), AppointmentStatus.SCHEDULED),
                appointment(now.plusDays(1), AppointmentStatus.CONFIRMED),
                appointment(now.plusDays(3), AppointmentStatus.CANCELLED),
                completed);
    }

    @Test
    void organizationDashboard_aggregatesDoctorsPatientsAndAppointments() {
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(Fixtures.organization()));
        when(profileService.getDoctorsByOrganizationId(Fixtures.ORG_ID))
                .thenReturn(List.of(doctorResponse(Fixtures.DOCTOR_ID, true), doctorResponse(11L, false)));
        when(profileService.getPatientsByDoctorId(Fixtures.DOCTOR_ID))
                .thenReturn(List.of(patientResponse(20L, true), patientResponse(21L, false)));
        when(profileService.getPatientsByDoctorId(11L)).thenReturn(List.of());
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(doctorProfileRepository.findById(11L)).thenReturn(Optional.empty());
        when(appointmentRepository.findByDoctorId(Fixtures.DOCTOR_PROFILE_ACCOUNT_ID)).thenReturn(mixedAppointments());
        when(appointmentRepository.findByDoctorId(11L)).thenReturn(List.of());

        OrganizationDashboardResponse dashboard = service.getOrganizationDashboard(Fixtures.ORG_ID, null);

        assertThat(dashboard.getOrganizationName()).isEqualTo("Hospital Central");
        assertThat(dashboard.getTotalDoctors()).isEqualTo(2);
        assertThat(dashboard.getActiveDoctors()).isEqualTo(1);
        assertThat(dashboard.getTotalPatients()).isEqualTo(2);
        assertThat(dashboard.getActivePatients()).isEqualTo(1);
        assertThat(dashboard.getTotalAppointments()).isEqualTo(4);
        assertThat(dashboard.getUpcomingAppointments()).isEqualTo(2);
        assertThat(dashboard.getDoctorStatistics().get(Fixtures.DOCTOR_ID).getAverageRating()).isEqualTo(4.5);
    }

    @Test
    void organizationDashboard_filtersByDoctor() {
        when(userRepository.findById(Fixtures.ORG_ID)).thenReturn(Optional.of(Fixtures.organization()));
        when(profileService.getDoctorsByOrganizationId(Fixtures.ORG_ID))
                .thenReturn(List.of(doctorResponse(Fixtures.DOCTOR_ID, true), doctorResponse(11L, true)));
        when(profileService.getPatientsByDoctorId(11L)).thenReturn(List.of());
        when(doctorProfileRepository.findById(11L)).thenReturn(Optional.empty());
        when(appointmentRepository.findByDoctorId(11L)).thenReturn(List.of());

        OrganizationDashboardResponse dashboard = service.getOrganizationDashboard(Fixtures.ORG_ID, 11L);

        assertThat(dashboard.getTotalDoctors()).isEqualTo(1);
        verify(profileService, never()).getPatientsByDoctorId(Fixtures.DOCTOR_ID);
    }

    @Test
    void organizationDashboard_failsForUnknownOrganization() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrganizationDashboard(999L, null)).hasMessage("Organization not found");
    }

    @Test
    void doctorDashboard_countsAppointmentsAndSevereSymptoms() {
        LocalDate today = LocalDate.now();
        when(profileService.getDoctorProfileById(Fixtures.DOCTOR_ID)).thenReturn(doctorResponse(Fixtures.DOCTOR_ID, true));
        when(profileService.getPatientsByDoctorId(Fixtures.DOCTOR_ID)).thenReturn(List.of(patientResponse(Fixtures.PATIENT_ID, true)));
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.of(Fixtures.doctor()));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(appointmentRepository.findByDoctorId(Fixtures.DOCTOR_PROFILE_ACCOUNT_ID)).thenReturn(mixedAppointments());
        when(appointmentRepository.findByPatientId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(mixedAppointments());
        when(symptomRepository.findByProfileOrderByOccurrenceDateDesc(any(Profile.class))).thenReturn(List.of(
                symptom(SymptomSeverity.CRITICA, today),
                symptom(SymptomSeverity.SEVERA, today.minusDays(2)),
                symptom(SymptomSeverity.LEVE, today.minusDays(20))));
        when(symptomRepository.findByProfileAndOccurrenceDateBetween(any(Profile.class), any(), eq(today)))
                .thenReturn(List.of(symptom(SymptomSeverity.SEVERA, today), symptom(SymptomSeverity.LEVE, today)));

        DoctorDashboardResponse dashboard = service.getDoctorDashboard(Fixtures.DOCTOR_ID, null);

        assertThat(dashboard.getDoctorName()).isEqualTo("Carlos Garcia");
        assertThat(dashboard.getTotalAppointments()).isEqualTo(4);
        assertThat(dashboard.getUpcomingAppointments()).isEqualTo(2);
        assertThat(dashboard.getCompletedAppointments()).isEqualTo(1);
        assertThat(dashboard.getTotalSymptomsReported()).isEqualTo(3);
        assertThat(dashboard.getCriticalSymptoms()).isEqualTo(2);
        assertThat(dashboard.getRecentSymptoms()).hasSize(2);
        assertThat(dashboard.getUpcomingAppointmentsList()).first()
                .satisfies(a -> assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED));
        DoctorDashboardResponse.PatientStatistics stats = dashboard.getPatientStatistics().get(Fixtures.PATIENT_ID);
        assertThat(stats.getSymptomsLastMonth()).isEqualTo(2);
        assertThat(stats.getCriticalSymptoms()).isEqualTo(1);
        assertThat(stats.getLastAppointmentDate()).isNotNull();
        assertThat(stats.getNextAppointmentDate()).isNotNull();
    }

    @Test
    void doctorDashboard_filteredByPatientUsesPatientAppointments() {
        when(profileService.getDoctorProfileById(Fixtures.DOCTOR_ID)).thenReturn(doctorResponse(Fixtures.DOCTOR_ID, true));
        when(profileService.getPatientsByDoctorId(Fixtures.DOCTOR_ID))
                .thenReturn(List.of(patientResponse(Fixtures.PATIENT_ID, true), patientResponse(21L, true)));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(appointmentRepository.findByPatientId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(List.of());
        when(symptomRepository.findByProfileOrderByOccurrenceDateDesc(any(Profile.class))).thenReturn(List.of());
        when(symptomRepository.findByProfileAndOccurrenceDateBetween(any(Profile.class), any(), any())).thenReturn(List.of());

        DoctorDashboardResponse dashboard = service.getDoctorDashboard(Fixtures.DOCTOR_ID, Fixtures.PATIENT_ID);

        assertThat(dashboard.getTotalPatients()).isEqualTo(1);
        assertThat(dashboard.getTotalAppointments()).isZero();
        verify(appointmentRepository, never()).findByDoctorId(any());
    }

    @Test
    void doctorDashboard_withoutResolvableProfilesIsEmpty() {
        when(profileService.getDoctorProfileById(Fixtures.DOCTOR_ID)).thenReturn(doctorResponse(Fixtures.DOCTOR_ID, true));
        when(profileService.getPatientsByDoctorId(Fixtures.DOCTOR_ID)).thenReturn(List.of(patientResponse(Fixtures.PATIENT_ID, true)));
        when(doctorProfileRepository.findById(Fixtures.DOCTOR_ID)).thenReturn(Optional.empty());
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.empty());

        DoctorDashboardResponse dashboard = service.getDoctorDashboard(Fixtures.DOCTOR_ID, null);

        assertThat(dashboard.getTotalAppointments()).isZero();
        assertThat(dashboard.getPatientStatistics().get(Fixtures.PATIENT_ID).getSymptomsLastMonth()).isZero();
    }

    @Test
    void patientDashboard_summarizesAppointmentsAndSymptoms() {
        LocalDate today = LocalDate.now();
        when(profileService.getPatientProfileById(Fixtures.PATIENT_ID)).thenReturn(patientResponse(Fixtures.PATIENT_ID, true));
        when(profileService.getDoctorProfileById(Fixtures.DOCTOR_ID)).thenReturn(doctorResponse(Fixtures.DOCTOR_ID, true));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(appointmentRepository.findByPatientId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(mixedAppointments());
        when(symptomRepository.findByProfileOrderByOccurrenceDateDesc(any(Profile.class))).thenReturn(List.of(
                symptom(SymptomSeverity.CRITICA, today),
                symptom(SymptomSeverity.LEVE, today.minusDays(10)),
                symptom(SymptomSeverity.LEVE, today.minusDays(60))));

        PatientDashboardResponse dashboard = service.getPatientDashboard(Fixtures.PATIENT_ID);

        assertThat(dashboard.getPatientName()).isEqualTo("Juan Perez");
        assertThat(dashboard.getDoctorName()).isEqualTo("Carlos Garcia");
        assertThat(dashboard.getUpcomingAppointments()).isEqualTo(2);
        assertThat(dashboard.getCompletedAppointments()).isEqualTo(1);
        assertThat(dashboard.getNextAppointment().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(dashboard.getSymptomsLastWeek()).isEqualTo(1);
        assertThat(dashboard.getSymptomsLastMonth()).isEqualTo(2);
        assertThat(dashboard.getCriticalSymptoms()).isEqualTo(1);
        assertThat(dashboard.getSymptomsBySeverity()).containsEntry("LEVE", 2).containsEntry("CRITICA", 1);
        assertThat(dashboard.getDiagnosisDate()).isEqualTo("2026-03-01");
    }

    @Test
    void patientDashboard_withoutUpcomingAppointments() {
        when(profileService.getPatientProfileById(Fixtures.PATIENT_ID)).thenReturn(patientResponse(Fixtures.PATIENT_ID, true));
        when(profileService.getDoctorProfileById(Fixtures.DOCTOR_ID)).thenReturn(doctorResponse(Fixtures.DOCTOR_ID, true));
        when(patientProfileRepository.findById(Fixtures.PATIENT_ID)).thenReturn(Optional.of(Fixtures.patient()));
        when(appointmentRepository.findByPatientId(Fixtures.PATIENT_PROFILE_ACCOUNT_ID)).thenReturn(List.of());
        when(symptomRepository.findByProfileOrderByOccurrenceDateDesc(any(Profile.class))).thenReturn(List.of());

        PatientDashboardResponse dashboard = service.getPatientDashboard(Fixtures.PATIENT_ID);

        assertThat(dashboard.getNextAppointment()).isNull();
        assertThat(dashboard.getSymptomsBySeverity()).isEmpty();
    }
}
