package com.oncontrol.oncontrolbackend.iam.application.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.shared.infrastructure.service.HashingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HashingService hashingService;

    @InjectMocks
    private UserServiceImpl userService;

    private static User activeUser() {
        return User.builder().id(1L).email("org@hospital.com").password("hashed").isActive(true).build();
    }

    @Test
    void createUser_hashesPasswordAndDefaultsRole() {
        when(userRepository.existsByEmail("org@hospital.com")).thenReturn(false);
        when(hashingService.hash("pw")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.createUser("org@hospital.com", "pw", "Ana", "Diaz",
                "999", LocalDate.of(1990, 1, 1), "Lima", null);

        assertThat(user.getPassword()).isEqualTo("hashed");
        assertThat(user.getOrganizationName()).isEqualTo("Ana Diaz");
        assertThat(user.getRole()).isEqualTo(UserRole.ORGANIZATION);
    }

    @Test
    void createUser_rejectsDuplicatedEmail() {
        when(userRepository.existsByEmail("org@hospital.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("org@hospital.com", "pw", "A", "B",
                null, null, null, UserRole.ORGANIZATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authenticateUser_returnsActiveUserWithValidPassword() {
        User user = activeUser();
        when(userRepository.findByEmail("org@hospital.com")).thenReturn(Optional.of(user));
        when(hashingService.verify("pw", "hashed")).thenReturn(true);

        assertThat(userService.authenticateUser("org@hospital.com", "pw")).isSameAs(user);
    }

    @Test
    void authenticateUser_rejectsInvalidPassword() {
        when(userRepository.findByEmail("org@hospital.com")).thenReturn(Optional.of(activeUser()));
        when(hashingService.verify("bad", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.authenticateUser("org@hospital.com", "bad"))
                .hasMessage("Invalid email or password");
    }

    @Test
    void authenticateUser_rejectsDeactivatedAccount() {
        User user = activeUser();
        user.setIsActive(false);
        when(userRepository.findByEmail("org@hospital.com")).thenReturn(Optional.of(user));
        when(hashingService.verify("pw", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> userService.authenticateUser("org@hospital.com", "pw"))
                .hasMessage("User account is deactivated");
    }

    @Test
    void authenticateUser_rejectsUnknownEmail() {
        when(userRepository.findByEmail("x@y.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.authenticateUser("x@y.com", "pw"))
                .hasMessage("Invalid email or password");
    }

    @Test
    void findById_throwsWhenMissing() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(9L)).hasMessageContaining("9");
    }

    @Test
    void existsByEmail_delegatesToRepository() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThat(userService.existsByEmail("a@b.com")).isTrue();
    }

    @Test
    void verifyEmail_marksUserAsVerified() {
        User user = activeUser();
        when(userRepository.findByEmail("org@hospital.com")).thenReturn(Optional.of(user));

        userService.verifyEmail("org@hospital.com");

        assertThat(user.getIsEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_storesNewHash() {
        User user = activeUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(hashingService.hash("new")).thenReturn("new-hash");

        userService.changePassword(1L, "new");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void deactivateAndActivateUser_toggleFlag() {
        User user = activeUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deactivateUser(1L);
        assertThat(user.getIsActive()).isFalse();

        userService.activateUser(1L);
        assertThat(user.getIsActive()).isTrue();
    }
}
