package com.oncontrol.functions.alerts;

import java.util.logging.Logger;

/**
 * Current delivery channel: a high-priority structured log entry that Application
 * Insights stores as a trace (queryable and alertable through Azure Monitor alert
 * rules). Real e-mail/push delivery (e.g. Azure Communication Services) plugs in
 * by implementing {@link AlertNotifier}; see docs/function-toxicity-alerts.md.
 */
public class LogAlertNotifier implements AlertNotifier {

    static final String CHANNEL = "log";

    private final Logger logger;

    public LogAlertNotifier(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String notify(ToxicityAlert alert) {
        logger.warning(String.format(
                "[TOXICITY ALERT] %s %s - patient %d - notify %s <%s>%s (event %s)",
                alert.severity(),
                alert.symptomName(),
                alert.patientProfileId(),
                alert.doctorName(),
                alert.doctorEmail(),
                alert.caseUrl() == null ? "" : " - " + alert.caseUrl(),
                alert.eventId()));
        return CHANNEL;
    }
}
