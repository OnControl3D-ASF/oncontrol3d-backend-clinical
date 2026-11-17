package com.oncontrol.oncontrolbackend.bdd.steps;

import com.oncontrol.oncontrolbackend.symptoms.domain.model.CtcaeGrade;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;

import static org.assertj.core.api.Assertions.assertThat;

/** HU11 - Regla CTCAE: clasificación de toxicidad y umbral de alerta. */
public class ToxicidadSteps {

    private CtcaeGrade grade;
    private IllegalArgumentException rejection;

    @Cuando("se clasifica un evento de toxicidad de grado {int}")
    public void classify(int value) {
        try {
            grade = CtcaeGrade.of(value);
        } catch (IllegalArgumentException e) {
            rejection = e;
        }
    }

    @Entonces("la severidad resultante es {string}")
    public void severity(String expected) {
        assertThat(grade.toSeverity().name()).isEqualTo(expected);
    }

    @Entonces("el evento genera alerta al médico")
    public void triggersAlert() {
        assertThat(grade.triggersAlert()).isTrue();
    }

    @Entonces("el evento no genera alerta al médico")
    public void doesNotTriggerAlert() {
        assertThat(grade.triggersAlert()).isFalse();
    }

    @Entonces("la clasificación es rechazada")
    public void rejected() {
        assertThat(grade).isNull();
        assertThat(rejection).isNotNull();
    }
}
