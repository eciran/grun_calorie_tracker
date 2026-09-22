package com.grun.calorietracker.entity;

import java.util.Locale;

public final class EmailAddress {
    private EmailAddress() {}

    public static String canonical(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
