package com.grun.calorietracker.security;

import com.grun.calorietracker.enums.AdminPermission;
import com.grun.calorietracker.enums.UserRole;
import org.springframework.http.HttpMethod;

import java.util.EnumSet;
import java.util.Set;

public final class AdminPermissionMatrix {
    public static final String AUTHORITY_PREFIX = "ADMIN_PERMISSION_";

    private AdminPermissionMatrix() {
    }

    public static Set<AdminPermission> permissionsFor(UserRole role) {
        if (role == null || !role.isAdminRole()) {
            return Set.of();
        }
        return switch (role) {
            case OWNER -> EnumSet.allOf(AdminPermission.class);
            case ADMIN, ADMIN_READ_ONLY -> readOnlyPermissions();
            case ADMIN_SUPPORT -> EnumSet.of(AdminPermission.DASHBOARD_READ, AdminPermission.USERS_READ,
                    AdminPermission.USERS_MANAGE, AdminPermission.CATALOG_READ, AdminPermission.GROWTH_READ);
            case ADMIN_CATALOG -> EnumSet.of(AdminPermission.DASHBOARD_READ, AdminPermission.CATALOG_READ,
                    AdminPermission.CATALOG_MANAGE);
            case ADMIN_GROWTH -> EnumSet.of(AdminPermission.DASHBOARD_READ, AdminPermission.GROWTH_READ,
                    AdminPermission.GROWTH_MANAGE);
            case ADMIN_FINANCE -> EnumSet.of(AdminPermission.DASHBOARD_READ, AdminPermission.FINANCE_READ,
                    AdminPermission.FINANCE_MANAGE, AdminPermission.AUDIT_READ);
            case ADMIN_TECHNICAL -> EnumSet.of(AdminPermission.DASHBOARD_READ, AdminPermission.TECHNICAL_READ,
                    AdminPermission.TECHNICAL_MANAGE, AdminPermission.AUDIT_READ);
            default -> Set.of();
        };
    }

    private static Set<AdminPermission> readOnlyPermissions() {
        return EnumSet.of(AdminPermission.DASHBOARD_READ, AdminPermission.USERS_READ,
                AdminPermission.CATALOG_READ, AdminPermission.GROWTH_READ, AdminPermission.FINANCE_READ,
                AdminPermission.TECHNICAL_READ, AdminPermission.COMPLIANCE_READ, AdminPermission.AUDIT_READ,
                AdminPermission.ADMIN_TEAM_READ);
    }

    public static String authority(AdminPermission permission) {
        return AUTHORITY_PREFIX + permission.name();
    }

    public static AdminPermission requiredPermission(String method, String path) {
        boolean write = !HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method);
        if (path.equals("/api/v1/admin/security/me")) {
            return AdminPermission.DASHBOARD_READ;
        }
        if (path.startsWith("/api/v1/admin/security")) {
            return write ? AdminPermission.ADMIN_TEAM_MANAGE : AdminPermission.ADMIN_TEAM_READ;
        }
        if (path.startsWith("/api/v1/admin/legal")) {
            return write ? AdminPermission.COMPLIANCE_MANAGE : AdminPermission.COMPLIANCE_READ;
        }
        if (path.startsWith("/api/v1/admin/audits")) {
            return AdminPermission.AUDIT_READ;
        }
        if (containsAny(path, "/subscriptions", "/subscription-events", "/revenuecat", "/ai/credits", "/ai/quota")) {
            return write ? AdminPermission.FINANCE_MANAGE : AdminPermission.FINANCE_READ;
        }
        if (path.startsWith("/api/v1/admin/users")) {
            return write ? AdminPermission.USERS_MANAGE : AdminPermission.USERS_READ;
        }
        if (containsAny(path, "/catalog", "/products", "/recipes", "/food", "/achievements", "/exercises")) {
            return write ? AdminPermission.CATALOG_MANAGE : AdminPermission.CATALOG_READ;
        }
        if (containsAny(path, "/promos", "/notification-campaigns", "/engagement", "/tracking", "/growth", "/onboarding")) {
            return write ? AdminPermission.GROWTH_MANAGE : AdminPermission.GROWTH_READ;
        }
        if (containsAny(path, "/system", "/mail", "/brevo", "/push", "/ai/requests", "/ai/monitoring", "/ai/meal-drafts")) {
            return write ? AdminPermission.TECHNICAL_MANAGE : AdminPermission.TECHNICAL_READ;
        }
        return AdminPermission.DASHBOARD_READ;
    }

    private static boolean containsAny(String path, String... fragments) {
        for (String fragment : fragments) {
            if (path.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
