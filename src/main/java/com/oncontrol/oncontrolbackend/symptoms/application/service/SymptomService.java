package com.oncontrol.oncontrolbackend.symptoms.application.service;

import com.oncontrol.oncontrolbackend.symptoms.application.dto.SymptomRequest;
import com.oncontrol.oncontrolbackend.symptoms.application.dto.SymptomResponse;
import com.oncontrol.oncontrolbackend.symptoms.domain.event.ToxicityAlertRaised;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.CtcaeGrade;
import com.oncontrol.oncontrolbackend.symptoms.domain.model.Symptom;
import com.oncontrol.oncontrolbackend.symptoms.domain.repository.SymptomRepository;
import com.oncontrol.oncontrolbackend.profiles.domain.model.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SymptomService {

    private final SymptomRepository symptomRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SymptomResponse reportSymptom(SymptomRequest request, Profile profile) {
        Symptom symptom = Symptom.builder()
                .profile(profile)
                .symptomName(request.getSymptomName())
                .severity(request.getSeverity())
                .occurrenceDate(request.getOccurrenceDate())
                .occurrenceTime(request.getOccurrenceTime())
                .durationHours(request.getDurationHours())
                .notes(request.getNotes())
                .triggers(request.getTriggers())
                .managementActions(request.getManagementActions())
                .impactOnDailyLife(request.getImpactOnDailyLife())
                .requiresMedicalAttention(request.getRequiresMedicalAttention())
                .reportedToDoctor(false)
                .build();

        Symptom savedSymptom = symptomRepository.save(symptom);
        raiseAlertIfNeeded(savedSymptom, profile);
        return mapToResponse(savedSymptom);
    }

    /** HU11: CTCAE grade 3-4 (SEVERA/CRITICA) notifies the treating oncologist asynchronously. */
    private void raiseAlertIfNeeded(Symptom symptom, Profile profile) {
        if (CtcaeGrade.isAlertSeverity(symptom.getSeverity())) {
            eventPublisher.publishEvent(new ToxicityAlertRaised(
                    symptom.getId(),
                    profile.getId(),
                    symptom.getSymptomName(),
                    symptom.getSeverity(),
                    symptom.getOccurrenceDate(),
                    symptom.getOccurrenceTime(),
                    Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public List<SymptomResponse> getPatientSymptoms(Profile profile, LocalDate startDate, LocalDate endDate) {
        List<Symptom> symptoms;
        if (startDate != null && endDate != null) {
            symptoms = symptomRepository.findByProfileAndOccurrenceDateBetween(profile, startDate, endDate);
        } else {
            symptoms = symptomRepository.findByProfileOrderByOccurrenceDateDesc(profile);
        }
        return symptoms.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SymptomResponse> getRecentSymptoms(Profile profile, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        List<Symptom> symptoms = symptomRepository.findRecentByProfile(profile, startDate);
        return symptoms.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSymptomStats(Profile profile) {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        
        Long totalSymptoms = symptomRepository.countByProfileAndOccurrenceDateAfter(profile, thirtyDaysAgo);
        Long severeSymptoms = symptomRepository.countSevereSymptomsByProfile(profile, thirtyDaysAgo);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSymptoms", totalSymptoms);
        stats.put("severeSymptoms", severeSymptoms);
        stats.put("criticalSymptoms", symptomRepository.countByProfileAndOccurrenceDateAfter(profile, thirtyDaysAgo));
        stats.put("period", "30 days");
        
        return stats;
    }

    private SymptomResponse mapToResponse(Symptom symptom) {
        return SymptomResponse.builder()
                .id(symptom.getId())
                .symptomName(symptom.getSymptomName())
                .severity(symptom.getSeverity().name())
                .occurrenceDate(symptom.getOccurrenceDate())
                .occurrenceTime(symptom.getOccurrenceTime())
                .durationHours(symptom.getDurationHours())
                .notes(symptom.getNotes())
                .triggers(symptom.getTriggers())
                .managementActions(symptom.getManagementActions())
                .impactOnDailyLife(symptom.getImpactOnDailyLife())
                .requiresMedicalAttention(symptom.getRequiresMedicalAttention())
                .reportedToDoctor(symptom.getReportedToDoctor())
                .build();
    }
}
