package com.grun.calorietracker.enums;

public enum UserRole {
    STANDARD,
    PRO,
    OWNER,
    ADMIN,
    ADMIN_SUPPORT,
    ADMIN_CATALOG,
    ADMIN_GROWTH,
    ADMIN_FINANCE,
    ADMIN_TECHNICAL,
    ADMIN_READ_ONLY;

    public boolean isAdminRole() {
        return this == OWNER || this == ADMIN || name().startsWith("ADMIN_");
    }

    public boolean isOwner() {
        return this == OWNER;
    }
}
