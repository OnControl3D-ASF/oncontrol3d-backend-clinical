package com.oncontrol.oncontrolbackend.symptoms.infrastructure.messaging;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oncontrol.oncontrolbackend.symptoms.application.alert.ToxicityAlertMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Locks the wire format consumed by the toxicity-alerts Function (docs/contracts/toxicity-alert-v1.md). */
class ServiceBusToxicityAlertPublisherTest {

    // Same date handling as Spring Boot's auto-configured ObjectMapper (ISO-8601 strings).
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static ToxicityAlertMessage alert() {
        return new ToxicityAlertMessage(1, "7b6f2b8e-0d2b-4a8e-9a55-0b1a2c3d4e5f",
                Instant.parse("2026-11-17T13:00:00Z"), 77L, "Fiebre neutropenica", "CRITICA",
                LocalDate.of(2026, 11, 17), LocalTime.of(8, 0),
                20L, 10L, "Carlos Garcia", "dr.garcia@hospital.com", null);
    }

    @Test
    void sendsJsonMessageWithIdentifiersAndRoutingProperties() throws Exception {
        ServiceBusSenderClient sender = mock(ServiceBusSenderClient.class);

        new ServiceBusToxicityAlertPublisher(sender, objectMapper).publish(alert());

        ArgumentCaptor<ServiceBusMessage> captor = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(sender).sendMessage(captor.capture());
        ServiceBusMessage message = captor.getValue();
        assertThat(message.getMessageId()).isEqualTo("7b6f2b8e-0d2b-4a8e-9a55-0b1a2c3d4e5f");
        assertThat(message.getContentType()).isEqualTo("application/json");
        assertThat(message.getSubject()).isEqualTo("ToxicityAlert");
        assertThat(message.getApplicationProperties()).containsEntry("schemaVersion", 1).containsEntry("severity", "CRITICA");

        JsonNode body = objectMapper.readTree(message.getBody().toString());
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("reportedAt").asText()).isEqualTo("2026-11-17T13:00:00Z");
        assertThat(body.get("occurrenceDate").asText()).isEqualTo("2026-11-17");
        assertThat(body.get("occurrenceTime").asText()).isEqualTo("08:00:00");
        assertThat(body.get("doctorEmail").asText()).isEqualTo("dr.garcia@hospital.com");
        assertThat(body.has("patientName")).as("no patient name on the wire (data minimization)").isFalse();
    }
}
