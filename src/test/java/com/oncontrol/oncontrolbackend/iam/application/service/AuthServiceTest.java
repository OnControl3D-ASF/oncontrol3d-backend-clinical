package com.oncontrol.oncontrolbackend.iam.application.service;

import com.oncontrol.oncontrolbackend.iam.application.dto.LoginRequest;
import com.oncontrol.oncontrolbackend.iam.application.dto.RegisterRequest;
import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private static RegisterRequest.RegisterRequestBuilder registerRequest() {
        return RegisterRequest.builder()
                .email("org@hospital.com")
                .password("Secret123!")
                .organizationName("Hospital Central")
                .country("Peru")
                .city("Lima");
    }

    @Test
    void registerOrganization_hashesPasswordAndAppliesDefaults() {
        when(userRepository.existsByEmail("org@hospital.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User saved = authService.registerOrganization(registerRequest().build());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User persisted = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(persisted.getPassword()).isEqualTo("hashed");
        assertThat(persisted.getRole()).isEqualTo(UserRole.ORGANIZATION);
        assertThat(persisted.getMaxDoctors()).isEqualTo(10);
        assertThat(persisted.getMaxPatients()).isEqualTo(100);
        assertThat(persisted.getIsActive()).isTrue();
        assertThat(persisted.getIsEmailVerified()).isFalse();
    }

    @Test
    void registerOrganization_keepsExplicitLimits() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = authService.registerOrganization(registerRequest().maxDoctors(3).maxPatients(30).build());

        assertThat(saved.getMaxDoctors()).isEqualTo(3);
        assertThat(saved.getMaxPatients()).isEqualTo(30);
    }

    @Test
    void registerOrganization_rejectsDuplicatedEmail() {
        when(userRepository.existsByEmail("org@hospital.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerOrganization(registerRequest().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_returnsUserWhenPasswordMatches() {
        User user = User.builder().email("org@hospital.com").password("hashed").build();
        when(userRepository.findByEmail("org@hospital.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123!", "hashed")).thenReturn(true);

        Object result = authService.login(new LoginRequest("org@hospital.com", "Secret123!"));

        assertThat(result).isSameAs(user);
    }

    @Test
    void login_rejectsWrongPassword() {
        User user = User.builder().email("org@hospital.com").password("hashed").build();
        when(userRepository.findByEmail("org@hospital.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("org@hospital.com", "bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(userRepository.findByEmail("ghost@hospital.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@hospital.com", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
