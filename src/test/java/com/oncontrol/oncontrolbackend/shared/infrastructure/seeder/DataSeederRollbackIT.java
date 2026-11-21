package com.oncontrol.oncontrolbackend.shared.infrastructure.seeder;

import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
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
 * S-A5-i: a partial failure while provisioning (auth goes down at the 4th account) rolls back
 * every clinical row, so the seeding guard (users.count() > 0) lets the next start retry.
 */
@SpringBootTest(properties = {
        "app.data.seeding.enabled=true",
        "app.data.seeding.password=Seed-Passw0rd!"
})
@AutoConfigureMockMvc   // the BDD step beans in the test classpath need a MockMvc
@ActiveProfiles("it")
@Import({PostgresContainerConfig.class, DataSeederRollbackIT.Config.class})
class DataSeederRollbackIT {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RecordingIdentityProvisioner identityProvisioner() {
            return new RecordingIdentityProvisioner(4);
        }
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private DoctorProfileRepository doctorProfileRepository;

    @Test
    void partialProvisioningFailure_rollsBackAllSeededClinicalRows() {
        assertThat(userRepository.count()).isZero();
        assertThat(profileRepository.count()).isZero();
        assertThat(doctorProfileRepository.count()).isZero();
    }
}
