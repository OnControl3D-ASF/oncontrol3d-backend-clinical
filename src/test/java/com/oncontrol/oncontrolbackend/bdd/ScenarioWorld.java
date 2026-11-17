package com.oncontrol.oncontrolbackend.bdd;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

/** State shared between the steps of ONE scenario (a new instance per scenario). */
@Component
@ScenarioScope
public class ScenarioWorld {

    /** People in the scenario, by full name ("Carlos Garcia"). */
    public record Actor(String name, String email, String token, Long profileAccountId,
                        Long doctorId, Long patientId, String doctorName) {

        public boolean isDoctor() {
            return doctorId != null;
        }
    }

    private final Map<String, Actor> actors = new HashMap<>();
    private MvcResult lastResult;
    private Long treatmentId;
    private String treatingDoctor;

    public void add(Actor actor) {
        actors.put(actor.name(), actor);
    }

    public Actor actor(String name) {
        Actor actor = actors.get(name);
        if (actor == null) {
            throw new IllegalStateException("Unknown actor in scenario: " + name);
        }
        return actor;
    }

    public MvcResult lastResult() {
        return lastResult;
    }

    public void lastResult(MvcResult result) {
        this.lastResult = result;
    }

    public Long treatmentId() {
        return treatmentId;
    }

    public void treatment(Long id, String doctorName) {
        this.treatmentId = id;
        this.treatingDoctor = doctorName;
    }

    public String treatingDoctor() {
        return treatingDoctor;
    }
}
