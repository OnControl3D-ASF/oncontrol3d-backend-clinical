package com.oncontrol.oncontrolbackend.iam.application.service;

import com.oncontrol.oncontrolbackend.iam.application.dto.RegisterRequest;
import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityConflictException;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;
import com.oncontrol.oncontrolbackend.shared.application.service.AccountProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Local save -> provision in auth -> commit (A2.4). Any failure rolls the local row back. */
@ExtendWith(MockitoExtension.class)
class OrganizationOnboardingServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccountProvisioningService accountProvisioning;

    @InjectMocks
    private OrganizationOnboardingService service;

    private static RegisterRequest request() {
        return RegisterRequest.builder().email("org@hospital.com").password("Secret123!")
                .organizationName("Hospital Central").country("Peru").city("Lima").build();
    }

    private void stubSave() {
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(3L);
            return saved;
        });
    }

    @Test
    void register_savesOrganizationWithDefaultsThenProvisionsAccount() {
        stubSave();

        User organization = service.register(request());

        assertThat(organization.getId()).isEqualTo(3L);
        assertThat(organization.getRole()).isEqualTo(UserRole.ORGANIZATION);
        assertThat(organization.getMaxDoctors()).isEqualTo(10);
        assertThat(organization.getMaxPatients()).isEqualTo(100);
        assertThat(organization.getPassword()).isEqualTo("hashed");
        ArgumentCaptor<NewAccount> account = ArgumentCaptor.forClass(NewAccount.class);
        verify(accountProvisioning).provision(account.capture());
        assertThat(account.getValue()).isEqualTo(new NewAccount("org@hospital.com", "Secret123!", "ORGANIZATION",
                3L, null, null, null));
    }

    @Test
    void register_honoursRequestedLimits() {
        stubSave();
        RegisterRequest custom = request();
        custom.setMaxDoctors(2);
        custom.setMaxPatients(5);

        User organization = service.register(custom);

        assertThat(organization.getMaxDoctors()).isEqualTo(2);
        assertThat(organization.getMaxPatients()).isEqualTo(5);
    }

    @Test
    void register_emailAlreadyUsedLocally_isRejectedBeforeAnyWrite() {
        when(userRepository.existsByEmail("org@hospital.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Email already exists");
        verifyNoInteractions(accountProvisioning);
    }

    @Test
    void register_emailUsedByAProfile_isRejected() {
        when(profileRepository.existsByEmail("org@hospital.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Email already exists");
    }

    @Test
    void register_authConflict_isEmailAlreadyExists() {
        stubSave();
        when(accountProvisioning.provision(any(NewAccount.class))).thenThrow(new IdentityConflictException("409"));

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Email already exists");
    }

    @Test
    void register_authDown_propagatesSoTheOrganizationRowRollsBack() {
        stubSave();
        when(accountProvisioning.provision(any(NewAccount.class)))
                .thenThrow(new IdentityUnavailableException("down", null));

        assertThatThrownBy(() -> service.register(request())).isInstanceOf(IdentityUnavailableException.class);
    }
}
