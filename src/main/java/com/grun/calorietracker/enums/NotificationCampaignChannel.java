package com.grun.calorietracker.enums;

public enum NotificationCampaignChannel {
    IN_APP,
    PUSH,
    IN_APP_AND_PUSH,
    EMAIL,
    EMAIL_AND_IN_APP,
    EMAIL_PUSH_IN_APP;

    public boolean includesEmail() { return this == EMAIL || this == EMAIL_AND_IN_APP || this == EMAIL_PUSH_IN_APP; }
    public boolean includesPush() { return this == PUSH || this == IN_APP_AND_PUSH || this == EMAIL_PUSH_IN_APP; }
    public boolean includesInApp() { return this == IN_APP || this == IN_APP_AND_PUSH || this == EMAIL_AND_IN_APP || this == EMAIL_PUSH_IN_APP; }
}
