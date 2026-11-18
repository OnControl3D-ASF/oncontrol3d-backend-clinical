package com.oncontrol.oncontrolbackend.symptoms.infrastructure.messaging;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncontrol.oncontrolbackend.symptoms.application.alert.ToxicityAlertPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the toxicity alert publisher.
 *
 * <ul>
 *   <li>SERVICEBUS_NAMESPACE set (e.g. sb-oc3d-dev-xxxx.servicebus.windows.net): alerts go to
 *       Service Bus, authenticated with DefaultAzureCredential (AKS workload identity in Azure;
 *       no connection string or SAS key).</li>
 *   <li>Not set (local development, tests): a no-op publisher keeps the service working unchanged.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class ToxicityAlertMessagingConfig {

    private static final String ENABLED = "'${app.alerts.servicebus.namespace:}'.trim() != ''";

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression(ENABLED)
    ServiceBusSenderClient toxicityAlertSender(@Value("${app.alerts.servicebus.namespace}") String namespace,
                                               @Value("${app.alerts.servicebus.queue}") String queue) {
        log.info("Toxicity alerts will be published to queue '{}' on {}", queue, namespace);
        return new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(namespace)
                .credential(new DefaultAzureCredentialBuilder().build())
                .sender()
                .queueName(queue)
                .buildClient();
    }

    @Bean
    @ConditionalOnExpression(ENABLED)
    ToxicityAlertPublisher serviceBusToxicityAlertPublisher(ServiceBusSenderClient toxicityAlertSender,
                                                           ObjectMapper objectMapper) {
        return new ServiceBusToxicityAlertPublisher(toxicityAlertSender, objectMapper);
    }

    @Bean
    @ConditionalOnExpression("!(" + ENABLED + ")")
    ToxicityAlertPublisher noOpToxicityAlertPublisher() {
        log.info("SERVICEBUS_NAMESPACE not set: toxicity alerts are logged but not published");
        return message -> log.info("Toxicity alert {} not published (messaging disabled): symptom {} {}",
                message.eventId(), message.symptomId(), message.severity());
    }
}
