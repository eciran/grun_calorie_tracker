package com.grun.calorietracker.service.push;

public record PushProviderSendResult(
        boolean sent,
        String providerMessageId,
        String errorMessage,
        boolean invalidToken,
        boolean uncertain
) {
    public static PushProviderSendResult sent(String providerMessageId) {
        return new PushProviderSendResult(true, providerMessageId, null, false, false);
    }

    public static PushProviderSendResult failed(String errorMessage) {
        return new PushProviderSendResult(false, null, errorMessage, false, false);
    }

    public static PushProviderSendResult invalidToken(String errorMessage) {
        return new PushProviderSendResult(false, null, errorMessage, true, false);
    }

    /** Network timeout/connection loss can occur after provider acceptance; never blind-retry it. */
    public static PushProviderSendResult uncertain(String errorMessage) {
        return new PushProviderSendResult(false, null, errorMessage, false, true);
    }
}
