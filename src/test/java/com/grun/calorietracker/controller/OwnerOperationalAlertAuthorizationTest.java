package com.grun.calorietracker.controller;

import com.grun.calorietracker.entity.OwnerOperationalAlertEntity;
import com.grun.calorietracker.repository.OwnerOperationalAlertRepository;
import com.grun.calorietracker.service.OwnerOperationalAlertManagementService;
import com.grun.calorietracker.service.OwnerDailySummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OwnerOperationalAlertAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean OwnerOperationalAlertRepository repository() { return mock(OwnerOperationalAlertRepository.class); }
        @Bean OwnerOperationalAlertManagementService managementService() { return mock(OwnerOperationalAlertManagementService.class); }
        @Bean OwnerDailySummaryService dailySummaryService() { return mock(OwnerDailySummaryService.class); }
        @Bean OwnerOperationalAlertController controller(OwnerOperationalAlertRepository repository,
                OwnerOperationalAlertManagementService managementService, OwnerDailySummaryService dailySummaryService) {
            return new OwnerOperationalAlertController(repository, managementService, dailySummaryService);
        }
    }

    @Test void endpointsAreOwnerOnlyAndValidateBoundedFilters() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(OwnerOperationalAlertController.class);
            var repository = context.getBean(OwnerOperationalAlertRepository.class);
            for (String role : new String[]{"ADMIN", "ADMIN_TECHNICAL", "ADMIN_READ_ONLY", "USER"}) {
                authenticate(role);
                assertThrows(AccessDeniedException.class, () -> controller.list(null, null, 0, 25));
                assertThrows(AccessDeniedException.class, () -> controller.detail(1L));
                assertThrows(AccessDeniedException.class, () -> controller.dailySummary(null));
            }

            authenticate("OWNER");
            assertEquals(400, controller.list("UNKNOWN", null, 0, 25).getStatusCode().value());
            assertEquals(400, controller.list(null, "X".repeat(61), 0, 25).getStatusCode().value());
            assertEquals(400, controller.list(null, null, -1, 25).getStatusCode().value());
            assertEquals(400, controller.list(null, null, 0, 101).getStatusCode().value());
            verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));

            var alert = alert();
            when(repository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(alert), PageRequest.of(0, 25), 1));
            var list = controller.list(" retry ", " backend_error ", 0, 25);
            assertEquals(200, list.getStatusCode().value());
            assertEquals("no-store", list.getHeaders().getFirst("Cache-Control"));
            assertEquals(1, list.getBody().totalElements());
            assertEquals("RETRY", list.getBody().content().get(0).status());

            when(repository.findById(7L)).thenReturn(Optional.of(alert));
            assertEquals(200, controller.detail(7L).getStatusCode().value());
            assertEquals(400, controller.detail(0L).getStatusCode().value());
            assertEquals(404, controller.detail(8L).getStatusCode().value());
        } finally { SecurityContextHolder.clearContext(); }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture", null,
                AuthorityUtils.createAuthorityList("ROLE_" + role, "ADMIN_PERMISSION_TECHNICAL_READ")));
    }

    private OwnerOperationalAlertEntity alert() {
        var value = new OwnerOperationalAlertEntity();
        value.setId(7L); value.setCategory("BACKEND_ERROR"); value.setSeverity("CRITICAL"); value.setStatus("RETRY");
        value.setTitleEn("Critical backend error"); value.setTitleTr("Kritik backend hatası");
        value.setMessageEn("A grouped error occurred."); value.setMessageTr("Gruplanmış bir hata oluştu.");
        value.setTargetPath("/admin/system/errors?correlationId=fixture"); value.setOccurrenceCount(2L);
        value.setFirstOccurredAt(Instant.parse("2026-09-17T10:00:00Z")); value.setLastOccurredAt(Instant.parse("2026-09-17T11:00:00Z"));
        value.setAttemptCount(1); value.setNextAttemptAt(Instant.parse("2026-09-17T11:05:00Z"));
        value.setCreatedAt(Instant.parse("2026-09-17T10:00:00Z")); value.setUpdatedAt(Instant.parse("2026-09-17T11:00:00Z"));
        return value;
    }
}
