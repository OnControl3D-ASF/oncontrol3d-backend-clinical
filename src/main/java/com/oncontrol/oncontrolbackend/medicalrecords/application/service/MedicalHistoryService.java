package com.oncontrol.oncontrolbackend.medicalrecords.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncontrol.oncontrolbackend.medicalrecords.application.dto.*;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.*;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.AllergyRepository;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicalHistoryRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.model.DoctorProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MedicalHistoryService {

    private final MedicalHistoryRepository historyRepository;
    private final AllergyRepository allergyRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    /**
     * Create medical history entry
     */
    public HistoryEntryResponse createHistoryEntry(Long patientId, Long doctorId, CreateHistoryEntryRequest request) {
        log.info("Creating medical history entry for patient {}", patientId);
        // Only the patient, their doctor, or the org may write to this history
        // (was missing: any authenticated user could write to any patient).
        authorizationService.requirePatientAccess(patientId);

        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        DoctorProfile doctor = null;
        if (doctorId != null) {
            doctor = doctorProfileRepository.findById(doctorId)
                    .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
        }

        MedicalHistoryEntry entry = MedicalHistoryEntry.builder()
                .patient(patient)
                .doctor(doctor)
                .type(request.getType())
                .date(request.getDate())
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .severity(request.getSeverity())
                .documents(serializeList(request.getDocuments()))
                .isActive(true)
                .build();

        entry = historyRepository.save(entry);
        log.info("Medical history entry created with ID: {}", entry.getId());

        return mapToResponse(entry);
    }

    /**
     * Get complete medical history for a patient
     */
    @Transactional(readOnly = true)
    public List<HistoryEntryResponse> getPatientHistory(Long patientId) {
        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        return historyRepository.findByPatientAndIsActiveTrueOrderByDateDesc(patient).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get medical history by type
     */
    @Transactional(readOnly = true)
    public List<HistoryEntryResponse> getPatientHistoryByType(Long patientId, HistoryEntryType type) {
        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        return historyRepository.findByPatientAndTypeAndIsActiveTrueOrderByDateDesc(patient, type).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get medical history by date range
     */
    @Transactional(readOnly = true)
    public List<HistoryEntryResponse> getPatientHistoryByDateRange(
            Long patientId, LocalDate startDate, LocalDate endDate) {
        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        return historyRepository.findByPatientAndDateRange(patient, startDate, endDate).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Create allergy
     */
    public AllergyResponse createAllergy(Long patientId, CreateAllergyRequest request) {
        log.info("Creating allergy for patient {}", patientId);
        // Same guard as createHistoryEntry (was missing).
        authorizationService.requirePatientAccess(patientId);

        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        Allergy allergy = Allergy.builder()
                .patient(patient)
                .allergen(request.getAllergen())
                .type(request.getType())
                .severity(request.getSeverity())
                .reaction(request.getReaction())
                .diagnosedDate(request.getDiagnosedDate())
                .notes(request.getNotes())
                .isActive(true)
                .build();

        allergy = allergyRepository.save(allergy);
        log.info("Allergy created with ID: {}", allergy.getId());

        return mapToAllergyResponse(allergy);
    }

    /**
     * Get patient allergies
     */
    @Transactional(readOnly = true)
    public List<AllergyResponse> getPatientAllergies(Long patientId) {
        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        return allergyRepository.findByPatientAndIsActiveTrueOrderByDiagnosedDateDesc(patient).stream()
                .map(this::mapToAllergyResponse)
                .collect(Collectors.toList());
    }

    /**
     * Delete allergy
     */
    public void deleteAllergy(Long allergyId) {
        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new IllegalArgumentException("Allergy not found"));
        authorizationService.requirePatientAccess(allergy.getPatient().getId());

        allergy.setIsActive(false);
        allergyRepository.save(allergy);
    }

    // Mapping methods
    private HistoryEntryResponse mapToResponse(MedicalHistoryEntry entry) {
        return HistoryEntryResponse.builder()
                .id(entry.getId())
                .patientId(entry.getPatient().getId())
                .patientName(entry.getPatient().getProfile().getFullName())
                .doctorId(entry.getDoctor() != null ? entry.getDoctor().getId() : null)
                .doctorName(entry.getDoctor() != null ? entry.getDoctor().getProfile().getFullName() : null)
                .specialty(entry.getDoctor() != null ? entry.getDoctor().getSpecialization() : null)
                .type(entry.getType())
                .date(entry.getDate())
                .title(entry.getTitle())
                .description(entry.getDescription())
                .category(entry.getCategory())
                .severity(entry.getSeverity())
                .documents(deserializeList(entry.getDocuments()))
                .isActive(entry.getIsActive())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    private AllergyResponse mapToAllergyResponse(Allergy allergy) {
        return AllergyResponse.builder()
                .id(allergy.getId())
                .patientId(allergy.getPatient().getId())
                .allergen(allergy.getAllergen())
                .type(allergy.getType())
                .severity(allergy.getSeverity())
                .reaction(allergy.getReaction())
                .diagnosedDate(allergy.getDiagnosedDate())
                .notes(allergy.getNotes())
                .isActive(allergy.getIsActive())
                .build();
    }

    // JSON helpers
    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("Error serializing list", e);
            return "[]";
        }
    }

    private List<String> deserializeList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error deserializing list", e);
            return new ArrayList<>();
        }
    }
}

