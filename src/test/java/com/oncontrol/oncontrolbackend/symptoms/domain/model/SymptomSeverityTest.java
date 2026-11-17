package com.oncontrol.oncontrolbackend.symptoms.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The frontend sends either the Spanish enum name or the English alias. */
class SymptomSeverityTest {

    @ParameterizedTest
    @CsvSource({
            "LEVE, LEVE", "leve, LEVE", "MILD, LEVE",
            "MODERADA, MODERADA", "moderate, MODERADA",
            "SEVERA, SEVERA", "SEVERE, SEVERA",
            "CRITICA, CRITICA", "critical, CRITICA"
    })
    void fromString_acceptsSpanishAndEnglishValues(String input, SymptomSeverity expected) {
        assertThat(SymptomSeverity.fromString(input)).isEqualTo(expected);
    }

    @Test
    void fromString_nullReturnsNull() {
        assertThat(SymptomSeverity.fromString(null)).isNull();
    }

    @Test
    void fromString_rejectsUnknownValues() {
        assertThatThrownBy(() -> SymptomSeverity.fromString("SEVERO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accepted values");
    }

    @Test
    void jsonValueIsTheEnumName() {
        assertThat(SymptomSeverity.CRITICA.toJson()).isEqualTo("CRITICA");
        assertThat(SymptomSeverity.CRITICA.getDisplayName()).isEqualTo("Crítica");
        assertThat(SymptomSeverity.CRITICA.getEnglishValue()).isEqualTo("CRITICAL");
    }
}
