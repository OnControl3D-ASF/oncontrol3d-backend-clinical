package com.oncontrol.oncontrolbackend.dashboard.application.service;

import com.oncontrol.oncontrolbackend.appointments.application.dto.AppointmentResponse;
import com.oncontrol.oncontrolbackend.appointments.domain.model.Appointment;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentStatus;
import com.oncontrol.oncontrolbackend.appointments.domain.repository.AppointmentRepository;
import com.oncontrol.oncontrolbackend.dashboard.application.dto.*;
import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.Symptom;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.SymptomSeverity;
import com.oncontrol.oncontrolbackend.symptoms.domain.repository.SymptomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DashboardService - Aggregates data from multiple sources for dashboards
 * Provides statistics and filtered views for Organizations, Doctors, and Patients
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardService {

    private final ProfileService profileService;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final SymptomRepository symptomRepository;

    /** Resolve a DoctorProfile.id (external/API id) to its underlying Profile, or null. */
    private Profile doctorProfileToProfile(Long doctorProfileId) {
        return doctorProfileRepository.findById(doctorProfileId).map(dp -> dp.getProfile()).orElse(null);
    }

    /** Resolve a PatientProfile.id (external/API id) to its underlying Profile, or null. */
    private Profile patientProfileToProfile(Long patientProfileId) {
        return patientProfileRepository.findById(patientProfileId).map(pp -> pp.getProfile()).orElse(null);
    }

    /**
     * Get Organization Dashboard (general or filtered by doctor)
     */
    public OrganizationDashboardResponse getOrganizationDashboard(Long organizationId, Long filterByDoctorId) {
        log.info("Getting dashboard for organization: {}, filter by doctor: {}", organizationId, filterByDoctorId);

        User organization = userRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        List<DoctorProfileResponse> doctors = profileService.getDoctorsByOrganizationId(organizationId);
        
        // If filtering by specific doctor
        if (filterByDoctorId != null) {
            doctors = doctors.stream()
                    .filter(d -> d.getId().equals(filterByDoctorId))
                    .collect(Collectors.toList());
        }

        int totalDoctors = doctors.size();
        int activeDoctors = (int) doctors.stream().filter(DoctorProfileResponse::getIsAvailable).count();
        
        // Calculate patient statistics
        int totalPatients = 0;
        int activePatients = 0;
        Map<Long, OrganizationDashboardResponse.DoctorStatistics> doctorStats = new HashMap<>();
        
        for (DoctorProfileResponse doctor : doctors) {
            List<PatientProfileResponse> patients = profileService.getPatientsByDoctorId(doctor.getId());
            int doctorTotalPatients = patients.size();
            int doctorActivePatients = (int) patients.stream().filter(PatientProfileResponse::getIsActive).count();
            
            totalPatients += doctorTotalPatients;
            activePatients += doctorActivePatients;
            
            // Get appointments for this doctor. Appointment.doctor references the
            // underlying Profile, not the DoctorProfile, so resolve the profile pk
            // first (doctor.getId() is the doctor_profile id and never matches).
            Long doctorProfilePk = doctorProfileRepository.findById(doctor.getId())
                    .map(dp -> dp.getProfile().getId())
                    .orElse(doctor.getId());
            List<Appointment> doctorAppointments = appointmentRepository.findByDoctorId(doctorProfilePk);
            int totalAppts = doctorAppointments.size();
            int upcomingAppts = (int) doctorAppointments.stream()
                    .filter(a -> a.getAppointmentDate().isAfter(LocalDateTime.now()))
                    .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED || a.getStatus() == AppointmentStatus.CONFIRMED)
                    .count();
            
            doctorStats.put(doctor.getId(), OrganizationDashboardResponse.DoctorStatistics.builder()
                    .doctorId(doctor.getId())
                    .doctorName(doctor.getFullName())
                    .totalPatients(doctorTotalPatients)
                    .totalAppointments(totalAppts)
                    .upcomingAppointments(upcomingAppts)
                    .averageRating(doctor.getRating())
                    .build());
        }
        
        // Get total appointments for organization
        int totalAppointments = doctorStats.values().stream()
                .mapToInt(OrganizationDashboardResponse.DoctorStatistics::getTotalAppointments)
                .sum();
        int upcomingAppointments = doctorStats.values().stream()
                .mapToInt(OrganizationDashboardResponse.DoctorStatistics::getUpcomingAppointments)
                .sum();

        return OrganizationDashboardResponse.builder()
                .organizationId(organizationId)
                .organizationName(organization.getOrganizationName())
                .country(organization.getCountry())
                .city(organization.getCity())
                .totalDoctors(totalDoctors)
                .activeDoctors(activeDoctors)
                .totalPatients(totalPatients)
                .activePatients(activePatients)
                .totalAppointments(totalAppointments)
                .upcomingAppointments(upcomingAppointments)
                .doctors(doctors)
                .doctorStatistics(doctorStats)
                .build();
    }

    /**
     * Get Doctor Dashboard (general or filtered by patient)
     */
    public DoctorDashboardResponse getDoctorDashboard(Long doctorProfileId, Long filterByPatientId) {
        log.info("Getting dashboard for doctor: {}, filter by patient: {}", doctorProfileId, filterByPatientId);

        DoctorProfileResponse doctor = profileService.getDoctorProfileById(doctorProfileId);
        
        List<PatientProfileResponse> patients = profileService.getPatientsByDoctorId(doctorProfileId);
        
        // If filtering by specific patient
        if (filterByPatientId != null) {
            patients = patients.stream()
                    .filter(p -> p.getId().equals(filterByPatientId))
                    .collect(Collectors.toList());
        }

        int totalPatients = patients.size();
        int activePatients = (int) patients.stream().filter(PatientProfileResponse::getIsActive).count();
        
        // Get appointments (resolve external DoctorProfile.id/PatientProfile.id to the underlying Profile.id)
        List<Appointment> allAppointments;
        if (filterByPatientId != null) {
            Profile fp = patientProfileToProfile(filterByPatientId);
            allAppointments = fp != null ? appointmentRepository.findByPatientId(fp.getId()) : new ArrayList<>();
        } else {
            Profile dp = doctorProfileToProfile(doctorProfileId);
            allAppointments = dp != null ? appointmentRepository.findByDoctorId(dp.getId()) : new ArrayList<>();
        }
        
        int totalAppointments = allAppointments.size();
        List<Appointment> upcoming = allAppointments.stream()
                .filter(a -> a.getAppointmentDate().isAfter(LocalDateTime.now()))
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED || a.getStatus() == AppointmentStatus.CONFIRMED)
                .sorted(Comparator.comparing(Appointment::getAppointmentDate))
                .collect(Collectors.toList());
        
        int completedAppointments = (int) allAppointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .count();
        
        // Get symptoms
        List<Profile> patientProfiles = patients.stream()
                .map(p -> patientProfileToProfile(p.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        List<Symptom> allSymptoms = new ArrayList<>();
        int criticalSymptoms = 0;
        
        for (Profile patientProfile : patientProfiles) {
            List<Symptom> patientSymptoms = symptomRepository.findByProfileOrderByOccurrenceDateDesc(patientProfile);
            allSymptoms.addAll(patientSymptoms);
            criticalSymptoms += patientSymptoms.stream()
                    .filter(s -> s.getSeverity() == SymptomSeverity.CRITICA || s.getSeverity() == SymptomSeverity.SEVERA)
                    .count();
        }
        
        // Recent symptoms (last 7 days)
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        List<Symptom> recentSymptoms = allSymptoms.stream()
                .filter(s -> s.getOccurrenceDate().isAfter(sevenDaysAgo) || s.getOccurrenceDate().isEqual(sevenDaysAgo))
                .sorted(Comparator.comparing(Symptom::getOccurrenceDate).reversed())
                .limit(20)
                .collect(Collectors.toList());
        
        // Build patient statistics
        Map<Long, DoctorDashboardResponse.PatientStatistics> patientStats = new HashMap<>();
        for (PatientProfileResponse patient : patients) {
            Profile patientProfile = patientProfileToProfile(patient.getId());
            List<Appointment> patientAppts = patientProfile != null
                    ? appointmentRepository.findByPatientId(patientProfile.getId())
                    : new ArrayList<>();
            List<Appointment> patientUpcoming = patientAppts.stream()
                    .filter(a -> a.getAppointmentDate().isAfter(LocalDateTime.now()))
                    .sorted(Comparator.comparing(Appointment::getAppointmentDate))
                    .collect(Collectors.toList());

            int symptomsLastMonth = 0;
            int criticalSymptomsCount = 0;
            
            if (patientProfile != null) {
                LocalDate monthAgo = LocalDate.now().minusDays(30);
                List<Symptom> symptoms = symptomRepository.findByProfileAndOccurrenceDateBetween(
                        patientProfile, monthAgo, LocalDate.now());
                symptomsLastMonth = symptoms.size();
                criticalSymptomsCount = (int) symptoms.stream()
                        .filter(s -> s.getSeverity() == SymptomSeverity.CRITICA || s.getSeverity() == SymptomSeverity.SEVERA)
                        .count();
            }
            
            String lastApptDate = patientAppts.stream()
                    .filter(a -> a.getCompletedAt() != null)
                    .max(Comparator.comparing(Appointment::getCompletedAt))
                    .map(a -> a.getAppointmentDate().toString())
                    .orElse(null);
            
            String nextApptDate = patientUpcoming.isEmpty() ? null 
                    : patientUpcoming.get(0).getAppointmentDate().toString();
            
            patientStats.put(patient.getId(), DoctorDashboardResponse.PatientStatistics.builder()
                    .patientId(patient.getId())
                    .patientName(patient.getFullName())
                    .totalAppointments(patientAppts.size())
                    .upcomingAppointments(patientUpcoming.size())
                    .symptomsLastMonth(symptomsLastMonth)
                    .criticalSymptoms(criticalSymptomsCount)
                    .lastAppointmentDate(lastApptDate)
                    .nextAppointmentDate(nextApptDate)
                    .build());
        }
        
        // Map upcoming appointments to DTOs
        List<AppointmentResponse> upcomingAppointmentsList = upcoming.stream()
                .limit(10)
                .map(this::mapToAppointmentResponse)
                .collect(Collectors.toList());

        return DoctorDashboardResponse.builder()
                .doctorId(doctorProfileId)
                .doctorName(doctor.getFullName())
                .specialization(doctor.getSpecialization())
                .organizationId(doctor.getOrganizationId())
                .organizationName(doctor.getOrganizationName())
                .totalPatients(totalPatients)
                .activePatients(activePatients)
                .totalAppointments(totalAppointments)
                .upcomingAppointments(upcoming.size())
                .completedAppointments(completedAppointments)
                .totalSymptomsReported(allSymptoms.size())
                .criticalSymptoms(criticalSymptoms)
                .patients(patients)
                .upcomingAppointmentsList(upcomingAppointmentsList)
                .recentSymptoms(recentSymptoms)
                .patientStatistics(patientStats)
                .build();
    }

    /**
     * Get Patient Dashboard
     */
    public PatientDashboardResponse getPatientDashboard(Long patientProfileId) {
        log.info("Getting dashboard for patient: {}", patientProfileId);

        PatientProfileResponse patient = profileService.getPatientProfileById(patientProfileId);
        DoctorProfileResponse doctor = profileService.getDoctorProfileById(patient.getDoctorProfileId());
        
        Profile patientProfile = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new RuntimeException("Patient profile not found"))
                .getProfile();

        // Get appointments
        List<Appointment> allAppointments = appointmentRepository.findByPatientId(patientProfile.getId());
        List<Appointment> upcoming = allAppointments.stream()
                .filter(a -> a.getAppointmentDate().isAfter(LocalDateTime.now()))
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED || a.getStatus() == AppointmentStatus.CONFIRMED)
                .sorted(Comparator.comparing(Appointment::getAppointmentDate))
                .collect(Collectors.toList());
        
        int completedAppointments = (int) allAppointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .count();
        
        AppointmentResponse nextAppointment = upcoming.isEmpty() ? null 
                : mapToAppointmentResponse(upcoming.get(0));
        
        // Get symptoms
        List<Symptom> allSymptoms = symptomRepository.findByProfileOrderByOccurrenceDateDesc(patientProfile);
        
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        LocalDate monthAgo = LocalDate.now().minusDays(30);
        
        int symptomsLastWeek = (int) allSymptoms.stream()
                .filter(s -> s.getOccurrenceDate().isAfter(weekAgo) || s.getOccurrenceDate().isEqual(weekAgo))
                .count();
        
        int symptomsLastMonth = (int) allSymptoms.stream()
                .filter(s -> s.getOccurrenceDate().isAfter(monthAgo) || s.getOccurrenceDate().isEqual(monthAgo))
                .count();
        
        int criticalSymptoms = (int) allSymptoms.stream()
                .filter(s -> s.getSeverity() == SymptomSeverity.CRITICA || s.getSeverity() == SymptomSeverity.SEVERA)
                .count();
        
        List<Symptom> recentSymptoms = allSymptoms.stream()
                .limit(10)
                .collect(Collectors.toList());
        
        // Symptoms by severity
        Map<String, Integer> symptomsBySeverity = allSymptoms.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getSeverity().name(),
                        Collectors.summingInt(s -> 1)
                ));
        
        // Map upcoming appointments
        List<AppointmentResponse> upcomingAppointmentsList = upcoming.stream()
                .limit(5)
                .map(this::mapToAppointmentResponse)
                .collect(Collectors.toList());

        return PatientDashboardResponse.builder()
                .patientId(patientProfileId)
                .patientName(patient.getFullName())
                .email(patient.getEmail())
                .bloodType(patient.getBloodType())
                .cancerType(patient.getCancerType())
                .cancerStage(patient.getCancerStage())
                .doctorId(doctor.getId())
                .doctorName(doctor.getFullName())
                .doctorSpecialization(doctor.getSpecialization())
                .organizationName(doctor.getOrganizationName())
                .totalAppointments(allAppointments.size())
                .upcomingAppointments(upcoming.size())
                .completedAppointments(completedAppointments)
                .upcomingAppointmentsList(upcomingAppointmentsList)
                .nextAppointment(nextAppointment)
                .totalSymptoms(allSymptoms.size())
                .symptomsLastWeek(symptomsLastWeek)
                .symptomsLastMonth(symptomsLastMonth)
                .criticalSymptoms(criticalSymptoms)
                .recentSymptoms(recentSymptoms)
                .symptomsBySeverity(symptomsBySeverity)
                .treatmentStatus(patient.getTreatmentStatus())
                .diagnosisDate(patient.getDiagnosisDate() != null ? patient.getDiagnosisDate().toString() : null)
                .build();
    }

    // Helper method
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
                .build();
    }
}

