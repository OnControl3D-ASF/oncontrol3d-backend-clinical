package com.oncontrol.oncontrolbackend.bdd.steps;

import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld;
import com.oncontrol.oncontrolbackend.bdd.ScenarioWorld.Actor;
import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.JwtService;
import com.oncontrol.oncontrolbackend.profiles.application.dto.ProfileIdentity;
import com.oncontrol.oncontrolbackend.profiles.domain.model.DoctorProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.ProfileType;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import io.cucumber.java.Before;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test data is created directly through repositories (arrange); behaviour is then
 * exercised through the REST API with real JWT authentication (act/assert).
 * Profile ids are set explicitly: the entity derives them from the current
 * millisecond, which collides when two profiles are created in the same ms.
 */
public class CommonSteps {

    private final ScenarioWorld world;
    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;

    public CommonSteps(ScenarioWorld world, JdbcTemplate jdbc, JwtService jwtService,
                       UserRepository userRepository, ProfileRepository profileRepository,
                       DoctorProfileRepository doctorProfileRepository,
                       PatientProfileRepository patientProfileRepository) {
        this.world = world;
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.patientProfileRepository = patientProfileRepository;
    }

    /** Every scenario starts from an empty database. */
    @Before
    public void cleanDatabase() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }

    @Dado("que existe el médico {string} con el paciente asignado {string}")
    public void doctorWithPatient(String doctorName, String patientName) {
        createDoctor(doctorName);
        createPatient(patientName, doctorName);
    }

    @Dado("que existe el médico {string} sin pacientes asignados")
    public void doctorWithoutPatients(String doctorName) {
        createDoctor(doctorName);
    }

    @Dado("que existe el paciente {string} del médico {string}")
    public void patientOfDoctor(String patientName, String doctorName) {
        createPatient(patientName, doctorName);
    }

    @Entonces("la respuesta tiene estado {int}")
    public void responseStatus(int expected) {
        assertThat(world.lastResult().getResponse().getStatus())
                .as("HTTP status. Body: %s", body())
                .isEqualTo(expected);
    }

    private String body() {
        try {
            return world.lastResult().getResponse().getContentAsString();
        } catch (Exception e) {
            return "<unreadable>";
        }
    }

    private void createDoctor(String name) {
        String slug = slug(name);
        User organization = userRepository.save(User.builder()
                .email("org." + slug + "@bdd.test")
                .password("not-used")
                .organizationName("Clinica de " + name)
                .country("Peru")
                .city("Lima")
                .role(UserRole.ORGANIZATION)
                .isActive(true)
                .isEmailVerified(true)
                .build());
        Profile account = profileRepository.save(profile(name, ProfileType.DOCTOR, organization));
        DoctorProfile doctor = doctorProfileRepository.save(DoctorProfile.builder()
                .profile(account)
                .organization(organization)
                .specialization("Oncologia")
                .licenseNumber("CMP-" + UUID.randomUUID().toString().substring(0, 8))
                .isAvailable(true)
                .rating(0.0)
                .totalReviews(0)
                .build());
        world.add(new Actor(name, account.getEmail(),
                jwtService.generateTokenForProfile(new ProfileIdentity(ProfileType.DOCTOR, account.getEmail(),
                        organization.getId(), account.getId(), doctor.getId(), null)),
                account.getId(), doctor.getId(), null, null));
    }

    private void createPatient(String name, String doctorName) {
        Actor doctorActor = world.actor(doctorName);
        DoctorProfile doctor = doctorProfileRepository.findById(doctorActor.doctorId()).orElseThrow();
        Profile account = profileRepository.save(profile(name, ProfileType.PATIENT, doctor.getOrganization()));
        PatientProfile patient = patientProfileRepository.save(PatientProfile.builder()
                .profile(account)
                .doctorProfile(doctor)
                .cancerType("Pulmon")
                .cancerStage("II")
                .build());
        world.add(new Actor(name, account.getEmail(),
                jwtService.generateTokenForProfile(new ProfileIdentity(ProfileType.PATIENT, account.getEmail(),
                        doctor.getOrganization().getId(), account.getId(), doctor.getId(), patient.getId())),
                account.getId(), null, patient.getId(), doctorName));
    }

    private static Profile profile(String fullName, ProfileType type, User organization) {
        String[] parts = fullName.split(" ", 2);
        return Profile.builder()
                .user(organization)
                .profileId((type == ProfileType.DOCTOR ? "DOC-" : "PAT-") + UUID.randomUUID())
                .profileType(type)
                .email(slug(fullName) + "@bdd.test")
                .password("not-used")
                .firstName(parts[0])
                .lastName(parts.length > 1 ? parts[1] : "-")
                .isActive(true)
                .build();
    }

    private static String slug(String name) {
        return name.toLowerCase().replace(' ', '.');
    }
}
