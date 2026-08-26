package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.NotificationCampaignEntity;
import com.grun.calorietracker.entity.NotificationDefinitionEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NotificationCampaignChannel;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.NotificationDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationDefinitionPolicyTest {
    private final NotificationDefinitionRepository repository = mock(NotificationDefinitionRepository.class);
    private final NotificationDefinitionPolicy policy = new NotificationDefinitionPolicy(repository);

    @Test
    void apply_usesLocalizedTemplateAndInAppPolicy() {
        UserEntity user = new UserEntity();
        user.setPreferredLanguage(PreferredLanguage.TR);
        NotificationEntity notification = notification(user);
        NotificationDefinitionEntity definition = definition(true, NotificationCampaignChannel.IN_APP);
        definition.setTitleTr("Hazır: {originalTitle}");
        definition.setMessageTr("{originalMessage} / {note}");
        definition.setSeverity("WARNING");
        definition.setTargetRoute("plans");

        boolean pushAllowed = policy.apply(notification, definition);

        assertFalse(pushAllowed);
        assertTrue(notification.getVisibleInApp());
        assertEquals("Hazır: Original title", notification.getTitle());
        assertEquals("Original message / Important", notification.getMessage());
        assertEquals("WARNING", notification.getSeverity());
        assertEquals("plans", notification.getTargetRoute());
    }

    @Test
    void apply_disabledDefinitionSuppressesEveryChannel() {
        NotificationEntity notification = notification(new UserEntity());

        boolean pushAllowed = policy.apply(notification, definition(false, NotificationCampaignChannel.IN_APP_AND_PUSH));

        assertFalse(pushAllowed);
        assertTrue(notification.getVisibleInApp());
    }

    @Test
    void apply_campaignNotificationPreservesCampaignOwnedDeliveryPolicy() {
        NotificationEntity notification = notification(new UserEntity());
        notification.setCampaign(new NotificationCampaignEntity());
        notification.setVisibleInApp(false);

        boolean pushAllowed = policy.apply(notification, definition(false, NotificationCampaignChannel.IN_APP));

        assertTrue(pushAllowed);
        assertFalse(notification.getVisibleInApp());
        assertEquals("Original title", notification.getTitle());
    }

    @Test
    void hiddenInAppTypes_containsDisabledAndPushOnlyDefinitions() {
        NotificationDefinitionEntity disabled = definition(false, NotificationCampaignChannel.IN_APP_AND_PUSH);
        disabled.setKey("disabled_type");
        NotificationDefinitionEntity pushOnly = definition(true, NotificationCampaignChannel.PUSH);
        pushOnly.setKey("push_type");
        NotificationDefinitionEntity visible = definition(true, NotificationCampaignChannel.IN_APP);
        visible.setKey("visible_type");
        when(repository.findAll()).thenReturn(List.of(disabled, pushOnly, visible));

        assertEquals(List.of("disabled_type", "push_type"), policy.hiddenInAppTypes());
    }

    private NotificationEntity notification(UserEntity user) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(user);
        notification.setTitle("Original title");
        notification.setMessage("Original message");
        notification.setNote("Important");
        notification.setSeverity("INFO");
        notification.setTargetRoute("home");
        notification.setVisibleInApp(true);
        return notification;
    }

    private NotificationDefinitionEntity definition(boolean enabled, NotificationCampaignChannel channel) {
        NotificationDefinitionEntity definition = new NotificationDefinitionEntity();
        definition.setEnabled(enabled);
        definition.setChannel(channel);
        return definition;
    }
}
