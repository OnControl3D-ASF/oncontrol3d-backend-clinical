package com.oncontrol.oncontrolbackend.appointments.application.service;

import com.oncontrol.oncontrolbackend.appointments.application.dto.CreateAppointmentRequest;
import com.oncontrol.oncontrolbackend.appointments.application.dto.AppointmentResponse;
import com.oncontrol.oncontrolbackend.appointments.domain.model.Appointment;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentStatus;
import com.oncontrol.oncontrolbackend.appointments.domain.repository.AppointmentRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.HistoryEntryType;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.MedicalHistoryEntry;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicalHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ProfileRepository profileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AuthorizationService authorizationService;
    private final MedicalHistoryRepository medicalHistoryRepository;

    /** Resolve a DoctorProfile.id (used by the API/frontend) to its underlying Profile. */
    private Profile resolveDoctorProfile(Long doctorProfileId) {
        return doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new RuntimeException("Doctor profile not found"))
                .getProfile();
    }

    /** Resolve a PatientProfile.id (used by the API/frontend) to its underlying Profile. */
    private Profile resolvePatientProfile(Long patientProfileId) {
        return patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new RuntimeException("Patient profile not found"))
                .getProfile();
    }

    /**
     * Create appointment (can be called by doctor or patient)
     */
    public AppointmentResponse createAppointment(Long doctorProfileId, Long patientProfileId, CreateAppointmentRequest request) {
        log.info("Creating appointment between doctor {} and patient {}", doctorProfileId, patientProfileId);

        Profile doctorProfile = resolveDoctorProfile(doctorProfileId);
        Profile patientProfile = resolvePatientProfile(patientProfileId);

        Appointment appointment = Appointment.builder()
                .doctor(doctorProfile)
                .patient(patientProfile)
                .appointmentDate(request.getAppointmentDate())
                .durationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 30)
                .type(request.getType())
                .status(AppointmentStatus.SCHEDULED)
                .location(request.getLocation())
                .notes(request.getNotes())
                .preparationInstructions(request.getPreparationInstructions())
                .sendReminder(request.getSendReminder() != null ? request.getSendReminder() : true)
                .build();

        appointment = appointmentRepository.save(appointment);

        log.info("Appointment created with ID: {}", appointment.getId());
        return mapToAppointmentResponse(appointment);
    }

    /**
     * Get all appointments for a doctor
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByDoctor(Long doctorProfileId) {
        Long doctorProfilePk = resolveDoctorProfile(doctorProfileId).getId();
        return appointmentRepository.findByDoctorId(doctorProfilePk).stream()
                .map(this::mapToAppointmentResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all appointments for a patient
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByPatient(Long patientProfileId) {
        Long patientProfilePk = resolvePatientProfile(patientProfileId).getId();
        return appointmentRepository.findByPatientId(patientProfilePk).stream()
                .map(this::mapToAppointmentResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get appointment by ID
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        authorizationService.requireProfileOwnership(appointment.getDoctor().getId(), appointment.getPatient().getId());
        return mapToAppointmentResponse(appointment);
    }

    /**
     * Update appointment status
     */
    public AppointmentResponse updateAppointmentStatus(Long id, AppointmentStatus status, String reason) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        authorizationService.requireProfileOwnership(appointment.getDoctor().getId(), appointment.getPatient().getId());

        appointment.setStatus(status);
        
        if (status == AppointmentStatus.CANCELLED && reason != null) {
            appointment.setCancellationReason(reason);
            appointment.setCancelledAt(java.time.LocalDateTime.now());
        } else if (status == AppointmentStatus.COMPLETED) {
            appointment.setCompletedAt(java.time.LocalDateTime.now());
            recordConsultationInHistory(appointment);
        }

        appointment = appointmentRepository.save(appointment);
        return mapToAppointmentResponse(appointment);
    }

    /**
     * When an appointment is completed, log it in the patient's medical history
     * automatically so the timeline grows with real activity. Best-effort: a
     * history failure must never block completing the appointment.
     */
    private void recordConsultationInHistory(Appointment appointment) {
        try {
            var patientProfile = patientProfileRepository
                    .findByProfileId(appointment.getPatient().getId()).orElse(null);
            var doctorProfile = doctorProfileRepository
                    .findByProfileId(appointment.getDoctor().getId()).orElse(null);
            if (patientProfile == null) {
                log.warn("No PatientProfile for profile {}, skipping auto history entry",
                        appointment.getPatient().getId());
                return;
            }

            String typeName = appointment.getType() != null
                    ? appointment.getType().getDisplayName()
                    : "Consulta";
            String description = "Cita completada con "
                    + appointment.getDoctor().getFullName()
                    + (appointment.getNotes() != null && !appointment.getNotes().isBlank()
                        ? ". Notas: " + appointment.getNotes()
                        : ".");

            medicalHistoryRepository.save(MedicalHistoryEntry.builder()
                    .patient(patientProfile)
                    .doctor(doctorProfile)
                    .type(HistoryEntryType.CONSULTATION)
                    .date(appointment.getAppointmentDate().toLocalDate())
                    .title(typeName)
                    .description(description)
                    .category("Cita")
                    .isActive(true)
                    .build());
        } catch (Exception e) {
            log.warn("Could not auto-create history entry for appointment {}: {}",
                    appointment.getId(), e.getMessage());
        }
    }

    /**
     * Add follow-up notes to completed appointment
     */
    public AppointmentResponse addFollowUpNotes(Long id, String notes) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        authorizationService.requireProfileOwnership(appointment.getDoctor().getId(), appointment.getPatient().getId());

        appointment.setFollowUpNotes(notes);
        appointment = appointmentRepository.save(appointment);
        return mapToAppointmentResponse(appointment);
    }

    /**
     * Reschedule an appointment to a new date
     */
    public AppointmentResponse rescheduleAppointment(Long id, java.time.LocalDateTime newAppointmentDate, String reason) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        authorizationService.requireProfileOwnership(appointment.getDoctor().getId(), appointment.getPatient().getId());

        log.info("Rescheduling appointment {} from {} to {}", id, appointment.getAppointmentDate(), newAppointmentDate);

        appointment.setAppointmentDate(newAppointmentDate);
        
        if (reason != null && !reason.isEmpty()) {
            String currentNotes = appointment.getNotes();
            String rescheduleNote = "Reprogramada: " + reason;
            appointment.setNotes(currentNotes != null ? currentNotes + "\n" + rescheduleNote : rescheduleNote);
        }

        appointment = appointmentRepository.save(appointment);
        return mapToAppointmentResponse(appointment);
    }

    private AppointmentResponse mapToAppointmentResponse(Appointment appointment) {
        Profile doctor = appointment.getDoctor();
        Profile patient = appointment.getPatient();

        return AppointmentResponse.builder()
                .id(appointment.getId())
                .doctorId(doctor.getId())
                .doctorName(doctor.getFullName())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .appointmentDate(appointment.getAppointmentDate())
                .durationMinutes(appointment.getDurationMinutes())
                .type(appointment.getType())
                .status(appointment.getStatus())
                .location(appointment.getLocation())
                .notes(appointment.getNotes())
                .preparationInstructions(appointment.getPreparationInstructions())
                .followUpNotes(appointment.getFollowUpNotes())
                .cancellationReason(appointment.getCancellationReason())
                .build();
    }
}

