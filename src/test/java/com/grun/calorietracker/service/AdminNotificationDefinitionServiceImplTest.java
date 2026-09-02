package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminNotificationDefinitionRequestDto;
import com.grun.calorietracker.entity.NotificationDefinitionEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.NotificationCampaignChannel;
import com.grun.calorietracker.repository.NotificationDefinitionRepository;
import com.grun.calorietracker.service.impl.AdminNotificationDefinitionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminNotificationDefinitionServiceImplTest {
    @Mock
    private NotificationDefinitionRepository repository;
    @Mock
    private AdminAuditService auditService;
    @InjectMocks
    private AdminNotificationDefinitionServiceImpl service;

    @Test
    void create_normalizesKeyAndRecordsAudit() {
        when(repository.save(any(NotificationDefinitionEntity.class))).thenAnswer(invocation -> {
            NotificationDefinitionEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return entity;
        });

        var result = service.create(request(" Plan_Ready "), "admin@grun.app", "cid-1");

        assertEquals("plan_ready", result.getKey());
        assertTrue(result.isEnabled());
        verify(auditService).record(eq("admin@grun.app"),
                eq(AdminAuditActionType.NOTIFICATION_DEFINITION_CREATE),
                eq(AdminAuditTargetType.NOTIFICATION_DEFINITION), eq("7"), isNull(), any(), eq("cid-1"));
    }

    @Test
    void update_rejectsKeyMutation() {
        NotificationDefinitionEntity entity = entity("plan_ready", false);
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class,
                () -> service.update(7L, request("different_key"), "admin@grun.app", "cid-2"));
        verify(repository, never()).save(any());
    }

    @Test
    void update_cannotDisableProtectedDefinition() {
        NotificationDefinitionEntity entity = entity("admin_security_alert", true);
        when(repository.findById(7L)).thenReturn(Optional.of(entity));
        AdminNotificationDefinitionRequestDto request = request("admin_security_alert");
        request.setEnabled(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.update(7L, request, "admin@grun.app", "cid-3"));
        verify(repository, never()).save(any());
    }

    @Test
    void legacyUpdateCannotBypassMealReminderApproval() {
        NotificationDefinitionEntity entity = entity("meal_reminder_lunch", true);
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        assertThrows(IllegalArgumentException.class,
                () -> service.update(7L, request("meal_reminder_lunch"), "admin@grun.app", "cid-4"));
        verify(repository, never()).save(any());
    }

    @Test
    void legacyUpdateCannotBypassSubscriptionLifecycleApproval() {
        NotificationDefinitionEntity entity = entity("subscription_billing_issue", true);
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.update(7L, request("subscription_billing_issue"), "admin@grun.app", "cid-5"));

        assertTrue(exception.getMessage().contains("protected approval workflow"));
        verify(repository, never()).save(any());
    }

    private AdminNotificationDefinitionRequestDto request(String key) {
        AdminNotificationDefinitionRequestDto request = new AdminNotificationDefinitionRequestDto();
        request.setKey(key);
        request.setDisplayName("Plan ready");
        request.setEnabled(true);
        request.setChannel(NotificationCampaignChannel.IN_APP_AND_PUSH);
        request.setSeverity("INFO");
        return request;
    }

    private NotificationDefinitionEntity entity(String key, boolean protectedDefinition) {
        NotificationDefinitionEntity entity = new NotificationDefinitionEntity();
        entity.setId(7L);
        entity.setKey(key);
        entity.setDisplayName("Definition");
        entity.setEnabled(true);
        entity.setProtectedDefinition(protectedDefinition);
        entity.setChannel(NotificationCampaignChannel.IN_APP_AND_PUSH);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
