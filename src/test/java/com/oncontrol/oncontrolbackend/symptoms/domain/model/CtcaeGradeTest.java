package com.oncontrol.oncontrolbackend.symptoms.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CtcaeGradeTest {

    @ParameterizedTest
    @CsvSource({"1, LEVE, false", "2, MODERADA, false", "3, SEVERA, true", "4, CRITICA, true"})
    void mapsGradeToSeverityAndAlertThreshold(int grade, SymptomSeverity severity, boolean alert) {
        CtcaeGrade ctcae = CtcaeGrade.of(grade);

        assertThat(ctcae.toSeverity()).isEqualTo(severity);
        assertThat(ctcae.triggersAlert()).isEqualTo(alert);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, -1, 6})
    void rejectsNonReportableGrades(int grade) {
        assertThatThrownBy(() -> CtcaeGrade.of(grade))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 4");
    }

    @Test
    void alertThresholdOnSeverityScale() {
        assertThat(CtcaeGrade.isAlertSeverity(SymptomSeverity.LEVE)).isFalse();
        assertThat(CtcaeGrade.isAlertSeverity(SymptomSeverity.MODERADA)).isFalse();
        assertThat(CtcaeGrade.isAlertSeverity(SymptomSeverity.SEVERA)).isTrue();
        assertThat(CtcaeGrade.isAlertSeverity(SymptomSeverity.CRITICA)).isTrue();
        assertThat(CtcaeGrade.isAlertSeverity(null)).isFalse();
    }
}
