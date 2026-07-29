package com.grun.calorietracker.security;

import com.grun.calorietracker.enums.AdminReauthenticationPurpose;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnerSensitiveActionFilterTest {
    @Test void mapsSensitiveMutationsToPurpose(){
        assertEquals(AdminReauthenticationPurpose.ADMIN_TEAM, OwnerSensitiveActionFilter.requiredPurpose("PATCH","/api/v1/admin/security/team/7"));
        assertEquals(AdminReauthenticationPurpose.SUBSCRIPTION_POLICY, OwnerSensitiveActionFilter.requiredPurpose("POST","/api/v1/admin/subscriptions/7"));
        assertEquals(AdminReauthenticationPurpose.RUNTIME_CONFIGURATION, OwnerSensitiveActionFilter.requiredPurpose("PUT","/api/v1/admin/system/operations/policy"));
        assertEquals(AdminReauthenticationPurpose.COMPLIANCE, OwnerSensitiveActionFilter.requiredPurpose("POST","/api/v1/admin/legal/gdpr-requests/3/resolve"));
        assertEquals(AdminReauthenticationPurpose.COMPLIANCE, OwnerSensitiveActionFilter.requiredPurpose("GET","/api/v1/admin/audits/export"));
        assertEquals(AdminReauthenticationPurpose.OWNER_SECURITY, OwnerSensitiveActionFilter.requiredPurpose("DELETE","/api/v1/admin/security/sessions/abc"));
        assertEquals(AdminReauthenticationPurpose.OWNER_SECURITY, OwnerSensitiveActionFilter.requiredPurpose("DELETE","/api/v1/admin/security/owner-sessions/abc"));
    }
    @Test void readsAndNonSensitiveWritesDoNotRequireProof(){
        assertNull(OwnerSensitiveActionFilter.requiredPurpose("GET","/api/v1/admin/subscriptions"));
        assertNull(OwnerSensitiveActionFilter.requiredPurpose("POST","/api/v1/admin/products/reviews"));
    }
}

