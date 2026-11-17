package com.oncontrol.functions.alerts;

import com.microsoft.applicationinsights.TelemetryClient;

import java.util.Map;

/**
 * Sends custom events to Application Insights. On Azure the Java agent
 * (APPLICATIONINSIGHTS_ENABLE_AGENT=true) captures these calls and adds the
 * Function's operation context, so each alert can be traced end to end.
 */
public class ApplicationInsightsTelemetry implements AlertTelemetry {

    private final TelemetryClient client = new TelemetryClient();

    @Override
    public void trackEvent(String name, Map<String, String> properties, Map<String, Double> metrics) {
        client.trackEvent(name, properties, metrics);
    }
}
