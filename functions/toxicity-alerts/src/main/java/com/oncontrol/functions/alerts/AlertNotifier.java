package com.oncontrol.functions.alerts;

/** Delivery channel for the oncologist notification (push, e-mail, ...). */
public interface AlertNotifier {

    /** @return a short identifier of the channel used, for telemetry. */
    String notify(ToxicityAlert alert);
}
