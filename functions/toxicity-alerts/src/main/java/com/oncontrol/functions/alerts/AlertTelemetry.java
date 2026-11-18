package com.oncontrol.functions.alerts;

import java.util.Map;

/** Telemetry port: custom events with string properties and numeric measurements. */
public interface AlertTelemetry {

    String NOTIFIED = "ToxicityAlertNotified";
    String IGNORED = "ToxicityAlertIgnored";
    String REJECTED = "ToxicityAlertRejected";

    void trackEvent(String name, Map<String, String> properties, Map<String, Double> metrics);
}
