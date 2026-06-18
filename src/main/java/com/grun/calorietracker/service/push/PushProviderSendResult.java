package com.grun.calorietracker.service.push;

public record PushProviderSendResult(
        boolean sent,
        String providerMessageId,
        String errorMessage,
        boolean invalidToken
) {
    public static PushProviderSendResult sent(String providerMessageId) {
        return new PushProviderSendResult(true, providerMessageId, null, false);
    }

    public static PushProviderSendResult failed(String errorMessage) {
        return new PushProviderSendResult(false, null, errorMessage, false);
    }

    public static PushProviderSendResult invalidToken(String errorMessage) {
        return new PushProviderSendResult(false, null, errorMessage, true);
    }
}
