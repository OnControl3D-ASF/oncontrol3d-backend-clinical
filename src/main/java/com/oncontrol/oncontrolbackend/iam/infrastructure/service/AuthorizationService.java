package com.oncontrol.oncontrolbackend.iam.infrastructure.service;

import com.oncontrol.oncontrolbackend.iam.infrastructure.security.AuthenticatedPrincipal;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.PrincipalRole;
import com.oncontrol.oncontrolbackend.profiles.domain.model.DoctorProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.PatientProfile;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.DoctorProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Centralizes resource-ownership (IDOR) checks on the JWT claims of the caller.
 *
 * The principal is an {@link AuthenticatedPrincipal} (claims only, no entity). The ids used in
 * URLs are a different id space from Profile PKs: doctorProfileId = DoctorProfile.id,
 * patientProfileId = PatientProfile.id, organizationId = organization (users) PK.
 * Target entities are still loaded (they are clinical data) and access is allowed up the
 * ownership chain (org -> doctor -> patient).
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final ProfileRepository profileRepository;

    private AuthenticatedPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        if (auth.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal;
        }
        throw new AccessDeniedException("Not authenticated");
    }

    /**
     * Allows only the organization itself.
     */
    public void requireOrganization(Long organizationId) {
        AuthenticatedPrincipal principal = currentPrincipal();
        if (principal.role() == PrincipalRole.ORGANIZATION && Objects.equals(principal.organizationId(), organizationId)) {
            return;
        }
        throw new AccessDeniedException("You are not allowed to access this organization's data");
    }

    /**
     * Allows the doctor themselves, or the organization that owns the doctor.
     */
    public void requireDoctor(Long doctorProfileId) {
        AuthenticatedPrincipal principal = currentPrincipal();
        DoctorProfile target = doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new AccessDeniedException("Doctor not found"));

        if (principal.role() == PrincipalRole.DOCTOR
                && Objects.equals(principal.doctorProfileId(), target.getId())) {
            return;
        }
        if (principal.role() == PrincipalRole.ORGANIZATION && ownsDoctor(principal, target)) {
            return;
        }
        throw new AccessDeniedException("You are not allowed to access this doctor's data");
    }

    /**
     * Allows the patient themselves, the patient's doctor, or the owning organization.
     */
    public void requirePatientAccess(Long patientProfileId) {
        AuthenticatedPrincipal principal = currentPrincipal();
        PatientProfile target = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new AccessDeniedException("Patient not found"));
        DoctorProfile doctor = target.getDoctorProfile();

        switch (principal.role()) {
            case PATIENT -> {
                if (Objects.equals(principal.patientProfileId(), target.getId())) {
                    return;
                }
            }
            case DOCTOR -> {
                if (doctor != null && Objects.equals(principal.doctorProfileId(), doctor.getId())) {
                    return;
                }
            }
            case ORGANIZATION -> {
                if (doctor != null && ownsDoctor(principal, doctor)) {
                    return;
                }
            }
        }
        throw new AccessDeniedException("You are not allowed to access this patient's data");
    }

    /**
     * Resource-level ownership check by the underlying Profile ids that own a resource
     * (e.g. an appointment's doctor/patient Profiles). Allows the owning profile itself
     * (claim profileId == Profile PK), or the organization that owns any of those profiles.
     */
    public void requireProfileOwnership(Long... ownerProfileIds) {
        AuthenticatedPrincipal principal = currentPrincipal();
        for (Long id : ownerProfileIds) {
            if (id == null) {
                continue;
            }
            if (principal.role() == PrincipalRole.ORGANIZATION) {
                Profile owner = profileRepository.findById(id).orElse(null);
                if (owner != null && owner.getUser() != null
                        && Objects.equals(owner.getUser().getId(), principal.organizationId())) {
                    return;
                }
            } else if (id.equals(principal.profileId())) {
                return;
            }
        }
        throw new AccessDeniedException("You are not allowed to access this resource");
    }

    private static boolean ownsDoctor(AuthenticatedPrincipal org, DoctorProfile doctor) {
        return doctor.getOrganization() != null
                && Objects.equals(doctor.getOrganization().getId(), org.organizationId());
    }
}
