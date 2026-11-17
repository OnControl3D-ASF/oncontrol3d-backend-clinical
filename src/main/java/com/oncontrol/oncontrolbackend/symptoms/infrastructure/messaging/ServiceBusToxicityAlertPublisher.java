package com.oncontrol.oncontrolbackend.symptoms.infrastructure.messaging;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncontrol.oncontrolbackend.symptoms.application.alert.ToxicityAlertMessage;
import com.oncontrol.oncontrolbackend.symptoms.application.alert.ToxicityAlertPublisher;

/** Sends toxicity alerts to the Service Bus queue as JSON (contract: docs/contracts/toxicity-alert-v1.md). */
public class ServiceBusToxicityAlertPublisher implements ToxicityAlertPublisher {

    static final String SUBJECT = "ToxicityAlert";

    private final ServiceBusSenderClient sender;
    private final ObjectMapper objectMapper;

    public ServiceBusToxicityAlertPublisher(ServiceBusSenderClient sender, ObjectMapper objectMapper) {
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ToxicityAlertMessage alert) {
        ServiceBusMessage message = new ServiceBusMessage(serialize(alert));
        message.setMessageId(alert.eventId()); // enables duplicate detection when the namespace supports it
        message.setContentType("application/json");
        message.setSubject(SUBJECT);
        message.getApplicationProperties().put("schemaVersion", alert.schemaVersion());
        message.getApplicationProperties().put("severity", alert.severity());
        sender.sendMessage(message);
    }

    private String serialize(ToxicityAlertMessage alert) {
        try {
            return objectMapper.writeValueAsString(alert);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Toxicity alert could not be serialized", e);
        }
    }
}
