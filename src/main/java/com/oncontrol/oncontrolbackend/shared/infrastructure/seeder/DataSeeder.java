package com.oncontrol.oncontrolbackend.shared.infrastructure.seeder;

import com.oncontrol.oncontrolbackend.appointments.domain.model.Appointment;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentStatus;
import com.oncontrol.oncontrolbackend.appointments.domain.model.AppointmentType;
import com.oncontrol.oncontrolbackend.appointments.domain.repository.AppointmentRepository;
import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.model.*;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.Symptom;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.SymptomSeverity;
import com.oncontrol.oncontrolbackend.symptoms.domain.repository.SymptomRepository;
import com.oncontrol.oncontrolbackend.treatments.domain.model.Treatment;
import com.oncontrol.oncontrolbackend.treatments.domain.model.TreatmentSession;
import com.oncontrol.oncontrolbackend.treatments.domain.model.TreatmentType;
import com.oncontrol.oncontrolbackend.treatments.domain.model.SessionStatus;
import com.oncontrol.oncontrolbackend.treatments.domain.repository.TreatmentRepository;
import com.oncontrol.oncontrolbackend.treatments.domain.repository.TreatmentSessionRepository;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.*;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicationRepository;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicalHistoryRepository;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.AllergyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DataSeeder - Crea datos iniciales para desarrollo y testing
 *
 * Controlado por la propiedad `app.data.seeding.enabled`.
 * Está desactivado por defecto. Para desarrollo local se activa con
 * APP_DATA_SEEDING_ENABLED=true y requiere definir SEED_USER_PASSWORD.
 */
@Component
@ConditionalOnProperty(name = "app.data.seeding.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final SymptomRepository symptomRepository;
    private final TreatmentRepository treatmentRepository;
    private final TreatmentSessionRepository treatmentSessionRepository;
    private final MedicationRepository medicationRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final AllergyRepository allergyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.data.seeding.password:}")
    private String seedPassword;

    @Override
    public void run(String... args) {
        // Solo ejecutar si la base de datos está vacía
        if (userRepository.count() > 0) {
            log.info("💡 Database already has data. Skipping seeder.");
            return;
        }

        if (seedPassword == null || seedPassword.isBlank()) {
            throw new IllegalStateException("SEED_USER_PASSWORD is required when data seeding is enabled");
        }

        log.info("🌱 Starting DataSeeder...");

        try {
            // 1. Crear Organización
            User organization = createOrganization();
            
            // 2. Crear Doctores
            DoctorProfile doctor1 = createDoctor(organization, "Carlos", "García", "dr.garcia@hospital.com", "Oncología Médica", "MED-12345");
            DoctorProfile doctor2 = createDoctor(organization, "María", "Rodríguez", "dr.rodriguez@hospital.com", "Oncología Quirúrgica", "MED-67890");
            
            // 3. Crear Pacientes para Doctor 1
            PatientProfile patient1 = createPatient(doctor1, "Juan", "Pérez", "juan.perez@email.com", "O+", "Pulmón", "II");
            PatientProfile patient2 = createPatient(doctor1, "Ana", "Martínez", "ana.martinez@email.com", "A+", "Mama", "I");
            
            // 4. Crear Pacientes para Doctor 2
            PatientProfile patient3 = createPatient(doctor2, "Pedro", "López", "pedro.lopez@email.com", "B+", "Colon", "III");
            PatientProfile patient4 = createPatient(doctor2, "Laura", "Sánchez", "laura.sanchez@email.com", "AB+", "Próstata", "II");
            
            // 5. Crear Citas
            createAppointment(doctor1.getProfile(), patient1.getProfile(), LocalDateTime.now().plusDays(2), AppointmentType.PRIMERA_CONSULTA);
            createAppointment(doctor1.getProfile(), patient1.getProfile(), LocalDateTime.now().plusDays(7), AppointmentType.CONSULTA_SEGUIMIENTO);
            createAppointment(doctor1.getProfile(), patient2.getProfile(), LocalDateTime.now().plusDays(3), AppointmentType.REVISION_EXAMENES);
            createAppointment(doctor2.getProfile(), patient3.getProfile(), LocalDateTime.now().plusDays(1), AppointmentType.PRIMERA_CONSULTA);
            createAppointment(doctor2.getProfile(), patient4.getProfile(), LocalDateTime.now().plusDays(5), AppointmentType.CONSULTA_SEGUIMIENTO);
            
            // 6. Crear Síntomas
            createSymptom(patient1.getProfile(), "Dolor de cabeza", SymptomSeverity.MODERADA, LocalDate.now().minusDays(1));
            createSymptom(patient1.getProfile(), "Náusea", SymptomSeverity.SEVERA, LocalDate.now().minusDays(2));
            createSymptom(patient1.getProfile(), "Fatiga", SymptomSeverity.LEVE, LocalDate.now());
            
            createSymptom(patient2.getProfile(), "Dolor en el pecho", SymptomSeverity.MODERADA, LocalDate.now().minusDays(1));
            
            createSymptom(patient3.getProfile(), "Dolor abdominal", SymptomSeverity.SEVERA, LocalDate.now().minusDays(3));
            createSymptom(patient3.getProfile(), "Pérdida de apetito", SymptomSeverity.MODERADA, LocalDate.now().minusDays(1));
            
            createSymptom(patient4.getProfile(), "Dolor de espalda", SymptomSeverity.LEVE, LocalDate.now().minusDays(2));
            
            // 7. Crear Tratamientos
            Treatment treatment1 = createTreatment(doctor1, patient1, TreatmentType.CHEMOTHERAPY, "FOLFOX", 6, LocalDate.now().minusDays(30));
            Treatment treatment2 = createTreatment(doctor1, patient2, TreatmentType.RADIOTHERAPY, "Radioterapia Externa", 25, LocalDate.now().minusDays(20));
            createTreatment(doctor2, patient3, TreatmentType.SURGERY, "Resección Quirúrgica", 1, LocalDate.now().minusDays(60));
            createTreatment(doctor2, patient4, TreatmentType.HORMONE_THERAPY, "Terapia Anti-Andrógenos", 12, LocalDate.now().minusDays(15));
            
            // 8. Crear Sesiones de Tratamiento
            createTreatmentSession(treatment1, 1, LocalDateTime.now().minusDays(25));
            createTreatmentSession(treatment1, 2, LocalDateTime.now().minusDays(18));
            createTreatmentSession(treatment2, 1, LocalDateTime.now().minusDays(15));
            
            // 9. Crear Medicamentos
            createMedication(doctor1, patient1, "Oxaliplatino", "85 mg/m²", "Cada 2 semanas", LocalDate.now().minusDays(30));
            createMedication(doctor1, patient1, "5-Fluorouracilo", "400 mg/m²", "Cada 2 semanas", LocalDate.now().minusDays(30));
            createMedication(doctor1, patient2, "Ondansetrón", "8 mg", "Cada 8 horas", LocalDate.now().minusDays(20));
            createMedication(doctor2, patient3, "Paracetamol", "500 mg", "Cada 6 horas si hay dolor", LocalDate.now().minusDays(60));
            createMedication(doctor2, patient4, "Bicalutamida", "50 mg", "Diaria", LocalDate.now().minusDays(15));
            
            // 10. Crear Historial Médico
            createHistoryEntry(doctor1, patient1, HistoryEntryType.DIAGNOSIS, "Diagnóstico de Cáncer de Pulmón", "Adenocarcinoma pulmonar etapa II", SeverityLevel.HIGH, LocalDate.now().minusDays(90));
            createHistoryEntry(doctor1, patient2, HistoryEntryType.DIAGNOSIS, "Diagnóstico de Cáncer de Mama", "Carcinoma ductal infiltrante etapa I", SeverityLevel.MEDIUM, LocalDate.now().minusDays(60));
            createHistoryEntry(doctor2, patient3, HistoryEntryType.SURGERY, "Cirugía de Resección Colónica", "Colectomía parcial exitosa", SeverityLevel.HIGH, LocalDate.now().minusDays(60));
            createHistoryEntry(doctor2, patient4, HistoryEntryType.DIAGNOSIS, "Diagnóstico de Cáncer de Próstata", "Adenocarcinoma prostático etapa II", SeverityLevel.MEDIUM, LocalDate.now().minusDays(45));
            createHistoryEntry(doctor1, patient1, HistoryEntryType.TEST_RESULT, "Resultados de TAC", "TAC de tórax muestra reducción tumoral del 30%", SeverityLevel.LOW, LocalDate.now().minusDays(10));
            
            // 11. Crear Alergias
            createAllergy(patient1, "Penicilina", AllergyType.MEDICATION, SeverityLevel.HIGH, "Erupción cutánea severa", LocalDate.of(2010, 5, 15));
            createAllergy(patient2, "Látex", AllergyType.CHEMICAL, SeverityLevel.MEDIUM, "Dermatitis de contacto", LocalDate.of(2015, 3, 20));
            createAllergy(patient3, "Mariscos", AllergyType.FOOD, SeverityLevel.MEDIUM, "Urticaria", LocalDate.of(2012, 8, 10));

            log.info("✅ DataSeeder completed successfully!");
            log.info("📊 Created:");
            log.info("   - 1 Organization");
            log.info("   - 2 Doctors");
            log.info("   - 4 Patients");
            log.info("   - 5 Appointments");
            log.info("   - 7 Symptoms");
            log.info("   - 4 Treatments");
            log.info("   - 3 Treatment Sessions");
            log.info("   - 5 Medications");
            log.info("   - 5 Medical History Entries");
            log.info("   - 3 Allergies");
            log.info("");
            log.info("🔐 Login accounts (password: SEED_USER_PASSWORD):");
            log.info("   Organization: admin@hospital.com");
            log.info("   Doctor 1: dr.garcia@hospital.com");
            log.info("   Doctor 2: dr.rodriguez@hospital.com");
            log.info("   Patient 1: juan.perez@email.com");
            log.info("   Patient 2: ana.martinez@email.com");
            log.info("   Patient 3: pedro.lopez@email.com");
            log.info("   Patient 4: laura.sanchez@email.com");
            
        } catch (Exception e) {
            log.error("❌ Error in DataSeeder: {}", e.getMessage(), e);
        }
    }

    private User createOrganization() {
        User organization = User.builder()
                .email("admin@hospital.com")
                .password(passwordEncoder.encode(seedPassword))
                .organizationName("Hospital Central")
                .country("México")
                .city("Ciudad de México")
                .phone("+52 55 1234 5678")
                .address("Av. Reforma 123, Col. Centro")
                .website("www.hospitalcentral.com")
                .licenseNumber("HOSP-2024-001")
                .taxId("HCM240101ABC")
                .description("Hospital especializado en tratamiento oncológico")
                .role(UserRole.ORGANIZATION)
                .maxDoctors(50)
                .maxPatients(500)
                .isActive(true)
                .isEmailVerified(true)
                .build();
        
        organization = userRepository.save(organization);
        log.info("✓ Created organization: {}", organization.getOrganizationName());
        return organization;
    }

    private DoctorProfile createDoctor(User organization, String firstName, String lastName, String email, 
                                      String specialization, String licenseNumber) {
        // Crear Profile
        Profile profile = Profile.builder()
                .user(organization)
                .profileType(ProfileType.DOCTOR)
                .email(email)
                .password(passwordEncoder.encode(seedPassword))
                .firstName(firstName)
                .lastName(lastName)
                .phone("+52 55 " + (int)(Math.random() * 9000000 + 1000000))
                .birthDate(LocalDate.of(1980, 1, 1).plusDays((long)(Math.random() * 3650)))
                .city("Ciudad de México")
                .address("Consultorio " + (int)(Math.random() * 500 + 100))
                .isActive(true)
                .build();
        
        profile = profileRepository.save(profile);
        
        // Crear DoctorProfile
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .profile(profile)
                .organization(organization)
                .specialization(specialization)
                .licenseNumber(licenseNumber)
                .yearsOfExperience((int)(Math.random() * 20 + 5))
                .hospitalAffiliation(organization.getOrganizationName())
                .consultationFee(new BigDecimal("1500.00"))
                .bio("Especialista en " + specialization + " con amplia experiencia en tratamientos oncológicos.")
                .isAvailable(true)
                .rating(4.5 + Math.random() * 0.5)
                .totalReviews((int)(Math.random() * 50 + 10))
                .build();
        
        doctorProfile = doctorProfileRepository.save(doctorProfile);
        log.info("✓ Created doctor: Dr. {} {}", firstName, lastName);
        return doctorProfile;
    }

    private PatientProfile createPatient(DoctorProfile doctorProfile, String firstName, String lastName, 
                                        String email, String bloodType, String cancerType, String cancerStage) {
        // Crear Profile
        Profile profile = Profile.builder()
                .user(doctorProfile.getOrganization())
                .profileType(ProfileType.PATIENT)
                .email(email)
                .password(passwordEncoder.encode(seedPassword))
                .firstName(firstName)
                .lastName(lastName)
                .phone("+52 55 " + (int)(Math.random() * 9000000 + 1000000))
                .birthDate(LocalDate.of(1950, 1, 1).plusDays((long)(Math.random() * 18250)))
                .city("Ciudad de México")
                .address("Calle " + (int)(Math.random() * 100 + 1) + " #" + (int)(Math.random() * 500 + 1))
                .isActive(true)
                .build();
        
        profile = profileRepository.save(profile);
        
        // Crear PatientProfile
        PatientProfile patientProfile = PatientProfile.builder()
                .profile(profile)
                .doctorProfile(doctorProfile)
                .bloodType(bloodType)
                .allergies("Ninguna conocida")
                .emergencyContactName(firstName + " " + lastName + " (Familiar)")
                .emergencyContactPhone("+52 55 " + (int)(Math.random() * 9000000 + 1000000))
                .emergencyContactRelationship("Familiar")
                .medicalHistory("Historial médico general")
                .currentMedications("Según prescripción médica")
                .insuranceProvider("Seguro Popular")
                .insuranceNumber("SP-" + (int)(Math.random() * 900000 + 100000))
                .cancerType(cancerType)
                .cancerStage(cancerStage)
                .diagnosisDate(LocalDate.now().minusMonths((long)(Math.random() * 12 + 1)))
                .treatmentStatus("En tratamiento")
                .lastTreatmentDate(LocalDate.now().minusDays((long)(Math.random() * 30 + 1)))
                .build();
        
        patientProfile = patientProfileRepository.save(patientProfile);
        log.info("✓ Created patient: {} {} (Cancer: {})", firstName, lastName, cancerType);
        return patientProfile;
    }

    private void createAppointment(Profile doctor, Profile patient, LocalDateTime dateTime, AppointmentType type) {
        Appointment appointment = Appointment.builder()
                .doctor(doctor)
                .patient(patient)
                .appointmentDate(dateTime)
                .durationMinutes(60)
                .type(type)
                .status(AppointmentStatus.SCHEDULED)
                .location("Consultorio " + (int)(Math.random() * 10 + 1))
                .notes("Cita programada")
                .preparationInstructions("Llegar 15 minutos antes")
                .sendReminder(true)
                .reminderSent(false)
                .build();
        
        appointmentRepository.save(appointment);
        log.info("✓ Created appointment: {} with {} on {}", type, patient.getFullName(), dateTime.toLocalDate());
    }

    private void createSymptom(Profile patient, String symptomName, SymptomSeverity severity, LocalDate date) {
        Symptom symptom = Symptom.builder()
                .profile(patient)
                .symptomName(symptomName)
                .severity(severity)
                .occurrenceDate(date)
                .occurrenceTime(LocalTime.of(10 + (int)(Math.random() * 8), (int)(Math.random() * 60)))
                .durationHours(2 + (int)(Math.random() * 6))
                .notes("Síntoma reportado por el paciente")
                .requiresMedicalAttention(severity == SymptomSeverity.SEVERA || severity == SymptomSeverity.CRITICA)
                .reportedToDoctor(false)
                .build();
        
        symptomRepository.save(symptom);
        log.info("✓ Created symptom: {} ({}) for {}", symptomName, severity, patient.getFullName());
    }

    private Treatment createTreatment(DoctorProfile doctor, PatientProfile patient, TreatmentType type, 
                                     String protocol, int totalCycles, LocalDate startDate) {
        Treatment treatment = Treatment.builder()
                .doctor(doctor)
                .patient(patient)
                .type(type)
                .protocol(protocol)
                .currentCycle(2)
                .totalCycles(totalCycles)
                .startDate(startDate)
                .nextSession(LocalDateTime.now().plusDays(7))
                .status(TreatmentStatus.ACTIVE)
                .effectiveness(new BigDecimal("85.5"))
                .adherence(new BigDecimal("92.0"))
                .sessionDurationMinutes(180)
                .location("Sala de Infusión 3")
                .medications("[\"" + protocol + "\"]")
                .sideEffects("[\"Náuseas leves\",\"Fatiga\"]")
                .notes("Tratamiento en progreso, paciente tolera bien")
                .preparationInstructions("Ayuno de 8 horas antes de la sesión")
                .isActive(true)
                .build();
        
        treatment = treatmentRepository.save(treatment);
        log.info("✓ Created treatment: {} ({}) for {}", type, protocol, patient.getProfile().getFullName());
        return treatment;
    }

    private void createTreatmentSession(Treatment treatment, int cycleNumber, LocalDateTime sessionDate) {
        TreatmentSession session = TreatmentSession.builder()
                .treatment(treatment)
                .sessionNumber(cycleNumber)
                .cycleNumber(cycleNumber)
                .sessionDate(sessionDate)
                .status(SessionStatus.COMPLETED)
                .durationMinutes(treatment.getSessionDurationMinutes())
                .location(treatment.getLocation())
                .medicationsAdministered("[\"Medicación según protocolo\"]")
                .sideEffects("[\"Náuseas leves\"]")
                .vitalSigns("{\"bloodPressure\":\"120/80\",\"heartRate\":75,\"temperature\":36.5}")
                .notes("Sesión completada sin complicaciones")
                .completedAt(sessionDate.plusMinutes(180))
                .build();
        
        treatmentSessionRepository.save(session);
        log.info("✓ Created treatment session #{} for treatment {}", cycleNumber, treatment.getId());
    }

    private void createMedication(DoctorProfile doctor, PatientProfile patient, String name, 
                                 String dosage, String frequency, LocalDate startDate) {
        Medication medication = Medication.builder()
                .patient(patient)
                .prescribedBy(doctor)
                .medicationName(name)
                .dosage(dosage)
                .frequency(frequency)
                .instructions("Tomar según indicaciones médicas")
                .startDate(startDate)
                .nextDoseTime(LocalTime.of(8, 0))
                .adherencePercentage(95)
                .isActive(true)
                .isPrn(frequency.contains("si"))
                .build();
        
        medicationRepository.save(medication);
        log.info("✓ Created medication: {} for {}", name, patient.getProfile().getFullName());
    }

    private void createHistoryEntry(DoctorProfile doctor, PatientProfile patient, HistoryEntryType type,
                                   String title, String description, SeverityLevel severity, LocalDate date) {
        MedicalHistoryEntry entry = MedicalHistoryEntry.builder()
                .patient(patient)
                .doctor(doctor)
                .type(type)
                .date(date)
                .title(title)
                .description(description)
                .category(type.name())
                .severity(severity)
                .documents("[]")
                .isActive(true)
                .build();
        
        medicalHistoryRepository.save(entry);
        log.info("✓ Created history entry: {} for {}", title, patient.getProfile().getFullName());
    }

    private void createAllergy(PatientProfile patient, String allergen, AllergyType type,
                              SeverityLevel severity, String reaction, LocalDate diagnosedDate) {
        Allergy allergy = Allergy.builder()
                .patient(patient)
                .allergen(allergen)
                .type(type)
                .severity(severity)
                .reaction(reaction)
                .diagnosedDate(diagnosedDate)
                .notes("Registrado en historial médico")
                .isActive(true)
                .build();
        
        allergyRepository.save(allergy);
        log.info("✓ Created allergy: {} for {}", allergen, patient.getProfile().getFullName());
    }
}

