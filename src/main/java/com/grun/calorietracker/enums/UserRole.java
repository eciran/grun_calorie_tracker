package com.grun.calorietracker.enums;

public enum UserRole {
    STANDARD,
    PRO,
    ADMIN,
    ADMIN_SUPPORT,
    ADMIN_CATALOG,
    ADMIN_GROWTH,
    ADMIN_FINANCE,
    ADMIN_TECHNICAL,
    ADMIN_READ_ONLY;

    public boolean isAdminRole() {
        return this == ADMIN || name().startsWith("ADMIN_");
    }
}
