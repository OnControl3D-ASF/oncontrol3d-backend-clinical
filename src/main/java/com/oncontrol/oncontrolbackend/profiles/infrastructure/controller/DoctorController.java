package com.oncontrol.oncontrolbackend.profiles.infrastructure.controller;

import com.oncontrol.oncontrolbackend.profiles.application.dto.CreatePatientRequest;
import com.oncontrol.oncontrolbackend.profiles.application.dto.DoctorProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.PatientProfileResponse;
import com.oncontrol.oncontrolbackend.profiles.application.dto.UpdateDoctorRequest;
import com.oncontrol.oncontrolbackend.profiles.application.service.ProfileService;
import com.oncontrol.oncontrolbackend.appointments.application.service.AppointmentService;
import com.oncontrol.oncontrolbackend.symptoms.application.service.SymptomService;
import com.oncontrol.oncontrolbackend.symptoms.application.dto.SymptomResponse;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DoctorController - Doctors manage patients
 */
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Doctors", description = "Doctor management endpoints")
public class DoctorController {

    private final ProfileService profileService;
    private final AppointmentService appointmentService;
    private final SymptomService symptomService;
    private final ProfileRepository profileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AuthorizationService authorizationService;

    @PostMapping("/{doctorProfileId}/patients")
    @Operation(summary = "Create patient", description = "Doctor creates a new patient")
    public ResponseEntity<?> createPatient(
            @PathVariable Long doctorProfileId,
            @Valid @RequestBody CreatePatientRequest request) {
        authorizationService.requireDoctor(doctorProfileId);
        try {
            PatientProfileResponse patient = profileService.createPatient(doctorProfileId, request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("patient", patient);
            response.put("message", "Patient created successfully");
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (IdentityUnavailableException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Identity service unavailable, try again later");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        } catch (Exception e) {
            log.error("Error creating patient", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error creating patient: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PutMapping("/{doctorProfileId}")
    @Operation(summary = "Update doctor profile", description = "Doctor updates their own profile (partial update)")
    public ResponseEntity<?> updateDoctorProfile(
            @PathVariable Long doctorProfileId,
            @Valid @RequestBody UpdateDoctorRequest request) {
        authorizationService.requireDoctor(doctorProfileId);
        try {
            DoctorProfileResponse doctor = profileService.updateDoctorProfile(doctorProfileId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("doctor", doctor);
            response.put("message", "Profile updated successfully");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error updating doctor profile", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error updating profile: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{doctorProfileId}/patients")
    @Operation(summary = "Get doctor patients", description = "Get all patients belonging to a doctor")
    public ResponseEntity<?> getPatients(@PathVariable Long doctorProfileId) {
        authorizationService.requireDoctor(doctorProfileId);
        try {
            List<PatientProfileResponse> patients = profileService.getPatientsByDoctorId(doctorProfileId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("patients", patients);
            response.put("count", patients.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving patients", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error retrieving patients: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{doctorProfileId}/patients/{patientId}")
    @Operation(summary = "Get patient by ID", description = "Get a specific patient by ID")
    public ResponseEntity<?> getPatientById(@PathVariable Long doctorProfileId, @PathVariable Long patientId) {
        authorizationService.requireDoctor(doctorProfileId);
        try {
            PatientProfileResponse patient = profileService.getPatientProfileById(patientId);
            
            // Verify patient belongs to doctor
            if (!patient.getDoctorProfileId().equals(doctorProfileId)) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "Patient does not belong to this doctor");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }
            
            return ResponseEntity.ok(patient);
        } catch (Exception e) {
            log.error("Error retrieving patient", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error retrieving patient: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{doctorProfileId}/patients/{patientId}/symptoms")
    @Operation(summary = "Get patient symptoms", description = "Get all symptoms for a specific patient")
    public ResponseEntity<?> getPatientSymptoms(
            @PathVariable Long doctorProfileId,
            @PathVariable Long patientId) {
        authorizationService.requireDoctor(doctorProfileId);
        authorizationService.requirePatientAccess(patientId);
        try {
            Profile patientProfile = patientProfileRepository.findById(patientId)
                    .orElseThrow(() -> new RuntimeException("Patient profile not found"))
                    .getProfile();

            List<SymptomResponse> symptoms = symptomService.getPatientSymptoms(patientProfile, null, null);
            
            Map<String, Object> response = new HashMap<>();
            response.put("symptoms", symptoms);
            response.put("count", symptoms.size());
            response.put("patientId", patientId);
            response.put("patientName", patientProfile.getFullName());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving patient symptoms", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error retrieving symptoms: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{doctorProfileId}/dashboard")
    @Operation(summary = "Get doctor dashboard", description = "Get doctor dashboard with all patients and appointments")
    public ResponseEntity<?> getDoctorDashboard(@PathVariable Long doctorProfileId) {
        authorizationService.requireDoctor(doctorProfileId);
        try {
            // Get all patients
            List<PatientProfileResponse> patients = profileService.getPatientsByDoctorId(doctorProfileId);
            
            // Get all appointments
            var appointments = appointmentService.getAppointmentsByDoctor(doctorProfileId);

            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("patients", Map.of(
                    "total", patients.size(),
                    "list", patients
            ));
            dashboard.put("appointments", Map.of(
                    "total", appointments.size(),
                    "list", appointments
            ));

            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Error retrieving doctor dashboard", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error retrieving dashboard: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
