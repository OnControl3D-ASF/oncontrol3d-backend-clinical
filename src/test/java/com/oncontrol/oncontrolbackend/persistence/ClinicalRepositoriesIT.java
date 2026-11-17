package com.oncontrol.oncontrolbackend.persistence;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.HistoryEntryType;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.MedicalHistoryEntry;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.TreatmentStatus;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.repository.MedicalHistoryRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.model.DoctorProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.support.PostgresContainerConfig;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.Symptom;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.SymptomSeverity;
import com.oncontrol.oncontrolbackend.symptoms.domain.repository.SymptomRepository;
import com.oncontrol.oncontrolbackend.treatments.domain.model.SessionStatus;
import com.oncontrol.oncontrolbackend.treatments.domain.model.Treatment;
import com.oncontrol.oncontrolbackend.treatments.domain.model.TreatmentSession;
import com.oncontrol.oncontrolbackend.treatments.domain.model.TreatmentType;
import com.oncontrol.oncontrolbackend.treatments.domain.repository.TreatmentRepository;
import com.oncontrol.oncontrolbackend.treatments.domain.repository.TreatmentSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests of the custom JPQL queries against a real PostgreSQL 16
 * (Testcontainers). These queries cannot be verified with mocks: they depend on
 * the SQL dialect, enum mapping and date comparisons of the real database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
@ActiveProfiles("it")
class ClinicalRepositoriesIT {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private UserRepository userRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private DoctorProfileRepository doctorProfileRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private TreatmentRepository treatmentRepository;
    @Autowired private TreatmentSessionRepository sessionRepository;
    @Autowired private MedicalHistoryRepository historyRepository;

    private Profile patientAccount;
    private PatientProfile patient;
    private DoctorProfile doctor;

    @BeforeEach
    void setUp() {
        User org = userRepository.save(User.builder().email("org@it.test").password("x")
                .organizationName("Clinica IT").country("Peru").city("Lima")
                .role(UserRole.ORGANIZATION).isActive(true).isEmailVerified(true).build());
        Profile doctorAccount = profileRepository.save(profile("doc@it.test", ProfileType.DOCTOR, org));
        doctor = doctorProfileRepository.save(DoctorProfile.builder().profile(doctorAccount).organization(org)
                .specialization("Oncologia").licenseNumber("CMP-IT").isAvailable(true).rating(0.0).totalReviews(0).build());
        patientAccount = profileRepository.save(profile("pat@it.test", ProfileType.PATIENT, org));
        patient = patientProfileRepository.save(PatientProfile.builder().profile(patientAccount).doctorProfile(doctor).build());
    }

    private static Profile profile(String email, ProfileType type, User org) {
        return Profile.builder().user(org).profileId(type.name() + "-" + UUID.randomUUID()).profileType(type)
                .email(email).password("x").firstName("Test").lastName(type.name()).isActive(true).build();
    }

    private Symptom symptom(String name, SymptomSeverity severity, LocalDate date) {
        return symptomRepository.save(Symptom.builder().profile(patientAccount).symptomName(name).severity(severity)
                .occurrenceDate(date).occurrenceTime(LocalTime.NOON)
                .requiresMedicalAttention(false).reportedToDoctor(false).build());
    }

    private Treatment treatment(TreatmentStatus status, LocalDate start, boolean active) {
        return treatmentRepository.save(Treatment.builder().doctor(doctor).patient(patient)
                .type(TreatmentType.CHEMOTHERAPY).protocol("FOLFOX").currentCycle(1).totalCycles(6)
                .startDate(start).status(status).isActive(active).build());
    }

    @Test
    void recentSymptoms_includeTheBoundaryDayAndSortNewestFirst() {
        symptom("old", SymptomSeverity.LEVE, TODAY.minusDays(10));
        symptom("boundary", SymptomSeverity.LEVE, TODAY.minusDays(7));
        symptom("today", SymptomSeverity.LEVE, TODAY);

        List<Symptom> recent = symptomRepository.findRecentByProfile(patientAccount, TODAY.minusDays(7));

        assertThat(recent).extracting(Symptom::getSymptomName).containsExactly("today", "boundary");
        assertThat(symptomRepository.countByProfileAndOccurrenceDateAfter(patientAccount, TODAY.minusDays(7))).isEqualTo(2);
    }

    @Test
    void symptomsBySeverity_filterEnumValuesStoredAsText() {
        symptom("leve", SymptomSeverity.LEVE, TODAY.minusDays(1));
        symptom("severa", SymptomSeverity.SEVERA, TODAY.minusDays(2));
        symptom("critica", SymptomSeverity.CRITICA, TODAY);

        List<Symptom> alarming = symptomRepository.findByProfileAndSeverityInOrderByOccurrenceDateDesc(
                patientAccount, List.of(SymptomSeverity.SEVERA, SymptomSeverity.CRITICA));

        assertThat(alarming).extracting(Symptom::getSymptomName).containsExactly("critica", "severa");
    }

    /**
     * Characterizes defect #1 (docs/calidad-clinical.md): the JPQL literal 'SEVERO'
     * does not match the stored enum value SEVERA, so severe symptoms are never counted.
     * When the query is fixed, this test must be updated to expect 1.
     */
    @Test
    void countSevereSymptoms_currentlyNeverMatches_defect1() {
        symptom("severa", SymptomSeverity.SEVERA, TODAY);

        assertThat(symptomRepository.countSevereSymptomsByProfile(patientAccount, TODAY.minusDays(30))).isZero();
    }

    @Test
    void currentTreatments_onlyActiveStatusAndNotDeleted_newestFirst() {
        treatment(TreatmentStatus.ACTIVE, TODAY.minusMonths(2), true);
        Treatment newest = treatment(TreatmentStatus.ACTIVE, TODAY.minusDays(3), true);
        treatment(TreatmentStatus.COMPLETED, TODAY.minusDays(1), true);
        treatment(TreatmentStatus.ACTIVE, TODAY, false);

        List<Treatment> current = treatmentRepository.findCurrentTreatmentsByPatientId(patient.getId());

        assertThat(current).hasSize(2);
        assertThat(current.get(0).getId()).isEqualTo(newest.getId());
        assertThat(treatmentRepository.countByDoctorIdAndStatus(doctor.getId(), TreatmentStatus.ACTIVE)).isEqualTo(3);
    }

    @Test
    void upcomingSessions_onlyFutureScheduledInAscendingOrder() {
        Treatment treatment = treatment(TreatmentStatus.ACTIVE, TODAY, true);
        LocalDateTime now = LocalDateTime.now();
        session(treatment, now.plusDays(7), SessionStatus.SCHEDULED, 3);
        session(treatment, now.plusDays(1), SessionStatus.SCHEDULED, 2);
        session(treatment, now.minusDays(1), SessionStatus.SCHEDULED, 1);
        session(treatment, now.plusDays(2), SessionStatus.CANCELLED, 4);

        List<TreatmentSession> upcoming = sessionRepository.findUpcomingSessionsByPatientId(patient.getId(), now);

        assertThat(upcoming).extracting(TreatmentSession::getSessionNumber).containsExactly(2, 3);
    }

    @Test
    void historyByDateRange_isInclusiveAndSkipsInactiveEntries() {
        history("antes", TODAY.minusDays(40), true);
        history("inicio", TODAY.minusDays(30), true);
        history("fin", TODAY, true);
        history("borrada", TODAY.minusDays(10), false);

        List<MedicalHistoryEntry> entries = historyRepository.findByPatientAndDateRange(patient, TODAY.minusDays(30), TODAY);

        assertThat(entries).extracting(MedicalHistoryEntry::getTitle).containsExactly("fin", "inicio");
    }

    private void session(Treatment treatment, LocalDateTime date, SessionStatus status, int number) {
        sessionRepository.save(TreatmentSession.builder().treatment(treatment).sessionNumber(number)
                .cycleNumber(1).sessionDate(date).status(status).build());
    }

    private void history(String title, LocalDate date, boolean active) {
        historyRepository.save(MedicalHistoryEntry.builder().patient(patient).doctor(doctor)
                .type(HistoryEntryType.CONSULTATION).date(date).title(title).isActive(active).build());
    }
}
