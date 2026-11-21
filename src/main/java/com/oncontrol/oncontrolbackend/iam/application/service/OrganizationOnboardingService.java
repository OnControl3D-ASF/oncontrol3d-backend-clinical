package com.oncontrol.oncontrolbackend.iam.application.service;

import com.oncontrol.oncontrolbackend.iam.application.dto.RegisterRequest;
import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.domain.model.UserRole;
import com.oncontrol.oncontrolbackend.iam.domain.repository.UserRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityConflictException;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;
import com.oncontrol.oncontrolbackend.shared.application.service.AccountProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization onboarding (HU01), owned by clinical. Protocol: save the organization locally,
 * provision its credentials in the auth service, commit. Any failure rolls the row back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrganizationOnboardingService {

    private static final int DEFAULT_MAX_DOCTORS = 10;
    private static final int DEFAULT_MAX_PATIENTS = 100;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountProvisioningService accountProvisioning;

    public User register(RegisterRequest request) {
        log.info("Registering new organization with email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail()) || profileRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User organization = userRepository.saveAndFlush(User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .organizationName(request.getOrganizationName())
                .country(request.getCountry())
                .city(request.getCity())
                .phone(request.getPhone())
                .address(request.getAddress())
                .website(request.getWebsite())
                .licenseNumber(request.getLicenseNumber())
                .taxId(request.getTaxId())
                .description(request.getDescription())
                .role(UserRole.ORGANIZATION)
                .maxDoctors(request.getMaxDoctors() != null ? request.getMaxDoctors() : DEFAULT_MAX_DOCTORS)
                .maxPatients(request.getMaxPatients() != null ? request.getMaxPatients() : DEFAULT_MAX_PATIENTS)
                .isActive(true)
                .isEmailVerified(false)
                .build());

        try {
            accountProvisioning.provision(new NewAccount(request.getEmail(), request.getPassword(),
                    "ORGANIZATION", organization.getId(), null, null, null));
        } catch (IdentityConflictException e) {
            throw new IllegalArgumentException("Email already exists", e);
        }

        log.info("Organization registered successfully with ID: {}", organization.getId());
        return organization;
    }
}
