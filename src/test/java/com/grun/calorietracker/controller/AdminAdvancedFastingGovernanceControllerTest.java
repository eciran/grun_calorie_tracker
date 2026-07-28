package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAdvancedFastingGovernanceControllerTest {

    @Test void endpointsRequireTechnicalAdminAuthorities() throws Exception {
        Method read = AdminAdvancedFastingGovernanceController.class.getMethod("getGovernance");
        Method update = AdminAdvancedFastingGovernanceController.class.getMethod(
                "updateOperations", UserDetails.class,
                AdvancedFastingOperationsConfigRequestDto.class, HttpServletRequest.class);

        assertThat(read.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('ADMIN_PERMISSION_TECHNICAL_READ')");
        assertThat(update.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('ADMIN_PERMISSION_TECHNICAL_MANAGE')");
    }

    @Test void operationalConfigRejectsValuesOutsideCodedSafetyBounds() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(
                    new AdvancedFastingOperationsConfigRequestDto(true, 5, 90, 10, 9));
            assertThat(violations).hasSize(4);
        }
    }

    @Test void updateWritesConfigOnlyAuditSnapshot() {
        AdvancedFastingGovernanceService governance = mock(AdvancedFastingGovernanceService.class);
        AdminAuditService audit = mock(AdminAuditService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        AdminAdvancedFastingGovernanceController controller =
                new AdminAdvancedFastingGovernanceController(governance, audit);
        AdvancedFastingGovernanceDto before = governance(true, 30);
        AdvancedFastingGovernanceDto after = governance(false, 20);
        when(governance.getGovernance()).thenReturn(before);
        when(governance.updateOperations(any())).thenReturn(after);
        when(servletRequest.getAttribute("correlationId")).thenReturn("cid-7");
        UserDetails admin = new User("admin@grun.test", "n/a", List.of());

        controller.updateOperations(admin,
                new AdvancedFastingOperationsConfigRequestDto(false, 20, 25, 90, 4), servletRequest);

        verify(audit).record(eq("admin@grun.test"),
                eq(AdminAuditActionType.FASTING_OPERATIONS_CONFIG_UPDATE),
                eq(AdminAuditTargetType.FASTING_GOVERNANCE), eq("GLOBAL"),
                isA(AdvancedFastingOperationsConfigRequestDto.class),
                isA(AdvancedFastingOperationsConfigRequestDto.class), eq("cid-7"));
    }

    private AdvancedFastingGovernanceDto governance(boolean enabled, int preStart) {
        return new AdvancedFastingGovernanceDto("FASTING_SAFETY_V1", 24, enabled, preStart,
                30, 60, 3, 0, 0, 0, 0, 0, 0, 0,
                LocalDateTime.of(2026, 7, 28, 12, 0));
    }
}