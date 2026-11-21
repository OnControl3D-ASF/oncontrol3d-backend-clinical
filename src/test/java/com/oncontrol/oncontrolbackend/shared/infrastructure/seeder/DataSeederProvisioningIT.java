package com.oncontrol.oncontrolbackend.shared.infrastructure.seeder;

import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityProvisioner;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;
import com.oncontrol.oncontrolbackend.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeding goes through IdentityProvisioner, in one transaction (REQ-A5.8):
 * when provisioning succeeds every seeded account is provisioned; the full seed set is committed.
 */
@SpringBootTest(properties = {
        "app.data.seeding.enabled=true",
        "app.data.seeding.password=Seed-Passw0rd!"
})
@AutoConfigureMockMvc   // the BDD step beans in the test classpath need a MockMvc
@ActiveProfiles("it")
@Import({PostgresContainerConfig.class, DataSeederProvisioningIT.Config.class})
class DataSeederProvisioningIT {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RecordingIdentityProvisioner identityProvisioner() {
            return new RecordingIdentityProvisioner(0);
        }
    }

    @Autowired
    private IdentityProvisioner provisioner;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfileRepository profileRepository;

    @Test
    void seeding_provisionsOrganizationDoctorsAndPatientsWithSeedPassword() {
        RecordingIdentityProvisioner recorder = (RecordingIdentityProvisioner) provisioner;

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(profileRepository.count()).isEqualTo(6);
        assertThat(recorder.provisioned()).hasSize(7);
        assertThat(recorder.provisioned()).extracting(NewAccount::role)
                .containsExactly("ORGANIZATION", "DOCTOR", "DOCTOR", "PATIENT", "PATIENT", "PATIENT", "PATIENT");
        assertThat(recorder.provisioned()).extracting(NewAccount::password).containsOnly("Seed-Passw0rd!");

        NewAccount doctor = recorder.provisioned().get(1);
        assertThat(doctor.email()).isEqualTo("dr.garcia@hospital.com");
        assertThat(doctor.organizationId()).isEqualTo(userRepository.findAll().get(0).getId());
        assertThat(doctor.profileId()).isNotNull();
        assertThat(doctor.doctorProfileId()).isNotNull();
        assertThat(doctor.patientProfileId()).isNull();

        NewAccount patient = recorder.provisioned().get(3);
        assertThat(patient.email()).isEqualTo("juan.perez@email.com");
        assertThat(patient.doctorProfileId()).isEqualTo(doctor.doctorProfileId());
        assertThat(patient.patientProfileId()).isNotNull();
    }
}
