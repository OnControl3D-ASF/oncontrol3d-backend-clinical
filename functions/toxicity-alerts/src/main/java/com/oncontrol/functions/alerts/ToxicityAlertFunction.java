package com.oncontrol.functions.alerts;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;

import java.time.Clock;
import java.util.logging.Logger;

/**
 * HU11 - serverless notification of severe/critical toxicity reports.
 *
 * <p>Trigger: Service Bus queue %ALERT_QUEUE_NAME%. Connection "ServiceBusConnection" is
 * identity-based (app setting ServiceBusConnection__fullyQualifiedNamespace + the Function's
 * managed identity with "Azure Service Bus Data Receiver"); no connection string exists.
 */
public class ToxicityAlertFunction {

    private static final AlertTelemetry TELEMETRY = new ApplicationInsightsTelemetry();

    @FunctionName("toxicityAlert")
    public void run(
            @ServiceBusQueueTrigger(
                    name = "message",
                    queueName = "%ALERT_QUEUE_NAME%",
                    connection = "ServiceBusConnection") String message,
            final ExecutionContext context) {
        process(message, context.getLogger(), TELEMETRY);
    }

    static ToxicityAlertProcessor.Outcome process(String message, Logger logger, AlertTelemetry telemetry) {
        ToxicityAlertProcessor processor =
                new ToxicityAlertProcessor(new LogAlertNotifier(logger), telemetry, Clock.systemUTC());
        ToxicityAlertProcessor.Outcome outcome = processor.process(message);
        logger.info("Toxicity alert processed: " + outcome);
        return outcome;
    }
}
