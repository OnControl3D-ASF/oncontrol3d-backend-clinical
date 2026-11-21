package com.oncontrol.oncontrolbackend.profiles.application.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.application.dto.*;
import com.oncontrol.oncontrolbackend.profiles.domain.model.*;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ProfileService manages doctor and patient profiles
 * - Organizations create doctors
 * - Doctors create patients
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Organization creates a doctor
     */
    public DoctorProfileResponse createDoctor(Long organizationId, CreateDoctorRequest request) {
        log.info("Organization {} creating doctor: {}", organizationId, request.getEmail());

        User organization = userRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        if (profileRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Check if organization has reached max doctors
        Long currentDoctors = doctorProfileRepository.countDoctorsByOrganizationId(organizationId);
        if (organization.getMaxDoctors() != null && currentDoctors >= organization.getMaxDoctors()) {
            throw new IllegalArgumentException("Organization has reached maximum number of doctors");
        }

        // Create profile with common fields
        Profile profile = Profile.builder()
                .user(organization)
                .profileType(ProfileType.DOCTOR)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .birthDate(request.getBirthDate())
                .city(request.getCity())
                .address(request.getAddress())
                .isActive(true)
                .build();

        profile = profileRepository.save(profile);

        // Create doctor-specific profile
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .profile(profile)
                .organization(organization)
                .specialization(request.getSpecialization())
                .licenseNumber(request.getLicenseNumber())
                .yearsOfExperience(request.getYearsOfExperience())
                .hospitalAffiliation(request.getHospitalAffiliation())
                .consultationFee(request.getConsultationFee())
                .bio(request.getBio())
                .isAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true)
                .rating(0.0)
                .totalReviews(0)
                .build();

        doctorProfile = doctorProfileRepository.save(doctorProfile);

        log.info("Doctor created successfully with profile ID: {}", profile.getProfileId());
        return mapToDoctorProfileResponse(doctorProfile);
    }

    /**
     * Doctor creates a patient
     */
    public PatientProfileResponse createPatient(Long doctorProfileId, CreatePatientRequest request) {
        log.info("Doctor {} creating patient: {}", doctorProfileId, request.getEmail());

        DoctorProfile doctorProfile = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new RuntimeException("Doctor profile not found"));

        if (profileRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create profile with common fields
        Profile profile = Profile.builder()
                .user(doctorProfile.getOrganization())
                .profileType(ProfileType.PATIENT)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .birthDate(request.getBirthDate())
                .city(request.getCity())
                .address(request.getAddress())
                .isActive(true)
                .build();

        profile = profileRepository.save(profile);

        // Create patient-specific profile
        PatientProfile patientProfile = PatientProfile.builder()
                .profile(profile)
                .doctorProfile(doctorProfile)
                .bloodType(request.getBloodType())
                .allergies(request.getAllergies())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .emergencyContactRelationship(request.getEmergencyContactRelationship())
                .medicalHistory(request.getMedicalHistory())
                .currentMedications(request.getCurrentMedications())
                .insuranceProvider(request.getInsuranceProvider())
                .insuranceNumber(request.getInsuranceNumber())
                .cancerType(request.getCancerType())
                .cancerStage(request.getCancerStage())
                .diagnosisDate(request.getDiagnosisDate())
                .treatmentStatus(request.getTreatmentStatus())
                .lastTreatmentDate(request.getLastTreatmentDate())
                .build();

        patientProfile = patientProfileRepository.save(patientProfile);

        log.info("Patient created successfully with profile ID: {}", profile.getProfileId());
        return mapToPatientProfileResponse(patientProfile);
    }

    /**
     * Login for profiles (doctors and patients)
     */
    public Object loginProfile(String email, String password) {
        log.info("Profile login attempt for email: {}", email);

        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(password, profile.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (profile.getProfileType() == ProfileType.DOCTOR) {
            DoctorProfile doctorProfile = doctorProfileRepository.findByProfileId(profile.getId())
                    .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
            return mapToDoctorProfileResponse(doctorProfile);
        } else {
            PatientProfile patientProfile = patientProfileRepository.findByProfileId(profile.getId())
                    .orElseThrow(() -> new RuntimeException("Patient profile not found"));
            return mapToPatientProfileResponse(patientProfile);
        }
    }

    /**
     * Resolves the ids that go into the JWT claim contract for a doctor/patient account.
     */
    @Transactional(readOnly = true)
    public ProfileIdentity resolveIdentity(String email) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        Long organizationId = profile.getUser().getId();

        if (profile.getProfileType() == ProfileType.DOCTOR) {
            DoctorProfile doctor = doctorProfileRepository.findByProfileId(profile.getId())
                    .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
            return new ProfileIdentity(ProfileType.DOCTOR, profile.getEmail(), organizationId,
                    profile.getId(), doctor.getId(), null);
        }
        PatientProfile patient = patientProfileRepository.findByProfileId(profile.getId())
                .orElseThrow(() -> new RuntimeException("Patient profile not found"));
        return new ProfileIdentity(ProfileType.PATIENT, profile.getEmail(), organizationId,
                profile.getId(), patient.getDoctorProfile().getId(), patient.getId());
    }

    // Query methods
    @Transactional(readOnly = true)
    public List<DoctorProfileResponse> getDoctorsByOrganizationId(Long organizationId) {
        return doctorProfileRepository.findByOrganizationId(organizationId).stream()
                .map(this::mapToDoctorProfileResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PatientProfileResponse> getPatientsByDoctorId(Long doctorProfileId) {
        return patientProfileRepository.findByDoctorProfileId(doctorProfileId).stream()
                .map(this::mapToPatientProfileResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DoctorProfileResponse getDoctorProfileById(Long id) {
        DoctorProfile doctorProfile = doctorProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
        return mapToDoctorProfileResponse(doctorProfile);
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getPatientProfileById(Long id) {
        PatientProfile patientProfile = patientProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient profile not found"));
        return mapToPatientProfileResponse(patientProfile);
    }

    /**
     * Partial update of a patient's own profile (common profile fields only).
     */
    @Transactional
    public PatientProfileResponse updatePatientProfile(Long patientProfileId, UpdatePatientRequest request) {
        log.info("Updating patient profile {}", patientProfileId);

        PatientProfile patientProfile = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new RuntimeException("Patient profile not found"));
        Profile profile = patientProfile.getProfile();

        // Demographic (common profile) — patient-editable
        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getPhone() != null) profile.setPhone(request.getPhone());
        if (request.getBirthDate() != null) profile.setBirthDate(request.getBirthDate());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());
        profileRepository.save(profile);

        // Clinical (patient profile) — doctor-managed
        if (request.getCancerType() != null) patientProfile.setCancerType(request.getCancerType());
        if (request.getCancerStage() != null) patientProfile.setCancerStage(request.getCancerStage());
        if (request.getDiagnosisDate() != null) patientProfile.setDiagnosisDate(request.getDiagnosisDate());
        if (request.getTreatmentStatus() != null) patientProfile.setTreatmentStatus(request.getTreatmentStatus());
        if (request.getBloodType() != null) patientProfile.setBloodType(request.getBloodType());
        if (request.getAllergies() != null) patientProfile.setAllergies(request.getAllergies());
        if (request.getEmergencyContactName() != null) patientProfile.setEmergencyContactName(request.getEmergencyContactName());
        if (request.getEmergencyContactPhone() != null) patientProfile.setEmergencyContactPhone(request.getEmergencyContactPhone());
        if (request.getEmergencyContactRelationship() != null) patientProfile.setEmergencyContactRelationship(request.getEmergencyContactRelationship());
        patientProfileRepository.save(patientProfile);

        log.info("Patient profile {} updated successfully", patientProfileId);
        return mapToPatientProfileResponse(patientProfile);
    }

    /**
     * Partial update of a doctor's own profile. Only non-null fields are applied.
     * Email, password and organization are intentionally not editable here.
     */
    @Transactional
    public DoctorProfileResponse updateDoctorProfile(Long doctorProfileId, UpdateDoctorRequest request) {
        log.info("Updating doctor profile {}", doctorProfileId);

        DoctorProfile doctorProfile = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
        Profile profile = doctorProfile.getProfile();

        // Common profile fields
        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getPhone() != null) profile.setPhone(request.getPhone());
        if (request.getBirthDate() != null) profile.setBirthDate(request.getBirthDate());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());

        // License number is unique: guard against collisions before changing it
        if (request.getLicenseNumber() != null
                && !request.getLicenseNumber().equals(doctorProfile.getLicenseNumber())) {
            doctorProfileRepository.findByLicenseNumber(request.getLicenseNumber())
                    .filter(existing -> !existing.getId().equals(doctorProfileId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("License number already in use");
                    });
            doctorProfile.setLicenseNumber(request.getLicenseNumber());
        }

        // Doctor-specific fields
        if (request.getSpecialization() != null) doctorProfile.setSpecialization(request.getSpecialization());
        if (request.getYearsOfExperience() != null) doctorProfile.setYearsOfExperience(request.getYearsOfExperience());
        if (request.getHospitalAffiliation() != null) doctorProfile.setHospitalAffiliation(request.getHospitalAffiliation());
        if (request.getConsultationFee() != null) doctorProfile.setConsultationFee(request.getConsultationFee());
        if (request.getBio() != null) doctorProfile.setBio(request.getBio());
        if (request.getIsAvailable() != null) doctorProfile.setIsAvailable(request.getIsAvailable());

        profileRepository.save(profile);
        doctorProfile = doctorProfileRepository.save(doctorProfile);

        log.info("Doctor profile {} updated successfully", doctorProfileId);
        return mapToDoctorProfileResponse(doctorProfile);
    }

    @Transactional(readOnly = true)
    public List<DoctorProfileResponse> getAllActiveDoctors() {
        return profileRepository.findAllActiveDoctors().stream()
                .map(profile -> {
                    DoctorProfile doctorProfile = doctorProfileRepository.findByProfileId(profile.getId())
                            .orElse(null);
                    return doctorProfile != null ? mapToDoctorProfileResponse(doctorProfile) : null;
                })
                .filter(response -> response != null)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PatientProfileResponse> getAllActivePatients() {
        return profileRepository.findAllActivePatients().stream()
                .map(profile -> {
                    PatientProfile patientProfile = patientProfileRepository.findByProfileId(profile.getId())
                            .orElse(null);
                    return patientProfile != null ? mapToPatientProfileResponse(patientProfile) : null;
                })
                .filter(response -> response != null)
                .collect(Collectors.toList());
    }

    // Mapping methods
    private DoctorProfileResponse mapToDoctorProfileResponse(DoctorProfile doctorProfile) {
        Profile profile = doctorProfile.getProfile();
        
        return DoctorProfileResponse.builder()
                .id(doctorProfile.getId())
                .profileId(profile.getProfileId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phone(profile.getPhone())
                .birthDate(profile.getBirthDate())
                .city(profile.getCity())
                .address(profile.getAddress())
                .organizationId(doctorProfile.getOrganization().getId())
                .organizationName(doctorProfile.getOrganization().getOrganizationName())
                .specialization(doctorProfile.getSpecialization())
                .licenseNumber(doctorProfile.getLicenseNumber())
                .yearsOfExperience(doctorProfile.getYearsOfExperience())
                .hospitalAffiliation(doctorProfile.getHospitalAffiliation())
                .consultationFee(doctorProfile.getConsultationFee())
                .bio(doctorProfile.getBio())
                .isAvailable(doctorProfile.getIsAvailable())
                .rating(doctorProfile.getRating())
                .totalReviews(doctorProfile.getTotalReviews())
                .isActive(profile.getIsActive())
                .build();
    }

    private PatientProfileResponse mapToPatientProfileResponse(PatientProfile patientProfile) {
        Profile profile = patientProfile.getProfile();
        DoctorProfile doctorProfile = patientProfile.getDoctorProfile();
        Profile doctorProfileInfo = doctorProfile.getProfile();
        
        return PatientProfileResponse.builder()
                .id(patientProfile.getId())
                .profileId(profile.getProfileId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phone(profile.getPhone())
                .birthDate(profile.getBirthDate())
                .city(profile.getCity())
                .address(profile.getAddress())
                .doctorProfileId(doctorProfile.getId())
                .doctorName(doctorProfileInfo.getFullName())
                .bloodType(patientProfile.getBloodType())
                .allergies(patientProfile.getAllergies())
                .emergencyContactName(patientProfile.getEmergencyContactName())
                .emergencyContactPhone(patientProfile.getEmergencyContactPhone())
                .emergencyContactRelationship(patientProfile.getEmergencyContactRelationship())
                .medicalHistory(patientProfile.getMedicalHistory())
                .currentMedications(patientProfile.getCurrentMedications())
                .insuranceProvider(patientProfile.getInsuranceProvider())
                .insuranceNumber(patientProfile.getInsuranceNumber())
                .cancerType(patientProfile.getCancerType())
                .cancerStage(patientProfile.getCancerStage())
                .diagnosisDate(patientProfile.getDiagnosisDate())
                .treatmentStatus(patientProfile.getTreatmentStatus())
                .lastTreatmentDate(patientProfile.getLastTreatmentDate())
                .isActive(profile.getIsActive())
                .build();
    }
}
