package com.oncontrol.oncontrolbackend.symptoms.domain.model;

/**
 * Toxicity grade according to CTCAE (Common Terminology Criteria for Adverse Events).
 *
 * <p>Business rule (see docs/reglas-negocio.md):
 * <pre>
 *   CTCAE grade | Severity  | Alerts the treating oncologist (HU11)
 *   ------------|-----------|--------------------------------------
 *        1      | LEVE      | no
 *        2      | MODERADA  | no
 *        3      | SEVERA    | yes
 *        4      | CRITICA   | yes
 *        5      | rejected  | CTCAE grade 5 = death related to the adverse event;
 *               |           | it is not a symptom a patient can self-report.
 * </pre>
 *
 * <p>Pure domain value object: no framework dependencies, immutable, validated on creation.
 */
public record CtcaeGrade(int value) {

    public static final int MIN_REPORTABLE = 1;
    public static final int MAX_REPORTABLE = 4;

    public CtcaeGrade {
        if (value < MIN_REPORTABLE || value > MAX_REPORTABLE) {
            throw new IllegalArgumentException(
                    "CTCAE grade must be between " + MIN_REPORTABLE + " and " + MAX_REPORTABLE
                            + " (grade 5 is not self-reportable): " + value);
        }
    }

    public static CtcaeGrade of(int value) {
        return new CtcaeGrade(value);
    }

    /** Maps the grade to the severity scale stored and exposed by the API. */
    public SymptomSeverity toSeverity() {
        return switch (value) {
            case 1 -> SymptomSeverity.LEVE;
            case 2 -> SymptomSeverity.MODERADA;
            case 3 -> SymptomSeverity.SEVERA;
            default -> SymptomSeverity.CRITICA;
        };
    }

    /** Grade 3 or 4 requires notifying the treating oncologist. */
    public boolean triggersAlert() {
        return isAlertSeverity(toSeverity());
    }

    /**
     * Alert threshold expressed on the severity scale, for reports that arrive
     * with a severity instead of a CTCAE grade (current ePRO API).
     */
    public static boolean isAlertSeverity(SymptomSeverity severity) {
        return severity == SymptomSeverity.SEVERA || severity == SymptomSeverity.CRITICA;
    }
}
