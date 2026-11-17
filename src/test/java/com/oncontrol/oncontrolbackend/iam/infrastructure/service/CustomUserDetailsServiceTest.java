package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.service.UserService;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadsOrganizationFirst() {
        User org = User.builder().email("org@h.com").build();
        when(userService.findByEmail("org@h.com")).thenReturn(org);

        assertThat(service.loadUserByUsername("org@h.com")).isSameAs(org);
    }

    @Test
    void fallsBackToProfile() {
        Profile profile = Profile.builder().email("doc@h.com").build();
        when(userService.findByEmail("doc@h.com")).thenThrow(new IllegalArgumentException("not found"));
        when(profileRepository.findByEmail("doc@h.com")).thenReturn(Optional.of(profile));

        assertThat(service.loadUserByUsername("doc@h.com")).isSameAs(profile);
    }

    @Test
    void throwsWhenNeitherExists() {
        when(userService.findByEmail("x@h.com")).thenThrow(new IllegalArgumentException("not found"));
        when(profileRepository.findByEmail("x@h.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("x@h.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
