package com.grun.calorietracker.service.support;

public final class GtinValidator {
    private GtinValidator() {
    }

    public static boolean isValid(String value) {
        if (value == null || !value.matches("(?:\\d{8}|\\d{12}|\\d{13}|\\d{14})")) {
            return false;
        }
        int total = 0;
        int weight = 3;
        for (int index = value.length() - 2; index >= 0; index--) {
            total += Character.digit(value.charAt(index), 10) * weight;
            weight = weight == 3 ? 1 : 3;
        }
        int checkDigit = (10 - total % 10) % 10;
        return checkDigit == Character.digit(value.charAt(value.length() - 1), 10);
    }
}
