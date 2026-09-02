package com.grun.calorietracker.security;

import com.grun.calorietracker.enums.AdminPermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FreePromotionPermissionContractTest {
    @Test
    void adminPolicyReadAndWriteUseGrowthPermissions() {
        assertThat(AdminPermissionMatrix.requiredPermission("GET", "/api/v1/admin/free-promotion"))
                .isEqualTo(AdminPermission.GROWTH_READ);
        assertThat(AdminPermissionMatrix.requiredPermission("PUT", "/api/v1/admin/free-promotion"))
                .isEqualTo(AdminPermission.GROWTH_MANAGE);
    }
}
