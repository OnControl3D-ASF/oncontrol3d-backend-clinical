package com.oncontrol.oncontrolbackend.symptoms.application.alert;

/** Outbound port: delivers a toxicity alert to the asynchronous notification channel. */
public interface ToxicityAlertPublisher {

    void publish(ToxicityAlertMessage message);
}
