package com.oncontrol.oncontrolbackend.bdd;

import com.oncontrol.oncontrolbackend.symptoms.application.alert.ToxicityAlertMessage;
import com.oncontrol.oncontrolbackend.symptoms.application.alert.ToxicityAlertPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replaces the real publisher in the BDD context: records what would be sent to
 * Service Bus so scenarios can assert the alert end to end (API -> commit ->
 * async listener -> message) without an Azure dependency.
 */
@Primary
@Component
public class RecordingToxicityAlertPublisher implements ToxicityAlertPublisher {

    private final List<ToxicityAlertMessage> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(ToxicityAlertMessage message) {
        published.add(message);
    }

    public List<ToxicityAlertMessage> published() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }
}
