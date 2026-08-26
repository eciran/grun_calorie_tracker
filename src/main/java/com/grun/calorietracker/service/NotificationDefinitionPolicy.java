package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.NotificationDefinitionEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.enums.NotificationCampaignChannel;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.NotificationDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationDefinitionPolicy {
    private final NotificationDefinitionRepository repository;

    public NotificationDefinitionEntity find(String type) {
        String key = normalize(type);
        return key == null ? null : repository.findByKey(key).orElse(null);
    }

    public Map<String, NotificationDefinitionEntity> findAll(Collection<String> types) {
        if (types == null || types.isEmpty()) return Collections.emptyMap();
        List<String> keys = types.stream().map(this::normalize).filter(value -> value != null).distinct().toList();
        return repository.findByKeyIn(keys).stream()
                .collect(Collectors.toMap(NotificationDefinitionEntity::getKey, Function.identity()));
    }

    public List<String> hiddenInAppTypes() {
        return repository.findAll().stream()
                .filter(definition -> !definition.isEnabled()
                        || definition.getChannel() == NotificationCampaignChannel.PUSH)
                .map(NotificationDefinitionEntity::getKey)
                .toList();
    }

    public boolean apply(NotificationEntity notification, NotificationDefinitionEntity definition) {
        if (notification == null || definition == null || notification.getCampaign() != null) return true;
        if (!definition.isEnabled()) {
            return false;
        }
        NotificationPresentation presentation = presentation(notification, definition);
        notification.setTitle(presentation.title());
        notification.setMessage(presentation.message());
        notification.setSeverity(presentation.severity());
        notification.setTargetRoute(presentation.targetRoute());
        return definition.getChannel() != NotificationCampaignChannel.IN_APP;
    }

    public boolean apply(NotificationEntity notification) {
        return apply(notification, find(notification == null ? null : notification.getType()));
    }

    public String normalize(String type) {
        if (type == null || type.isBlank()) return null;
        return type.trim().toLowerCase(Locale.ROOT);
    }

    public NotificationPresentation presentation(NotificationEntity notification, NotificationDefinitionEntity definition) {
        if (definition == null || notification.getCampaign() != null) {
            return new NotificationPresentation(notification.getTitle(), notification.getMessage(), notification.getSeverity(), notification.getTargetRoute());
        }
        PreferredLanguage language = notification.getUser() == null
                ? PreferredLanguage.EN : notification.getUser().getPreferredLanguage();
        String originalTitle = notification.getTitle();
        String originalMessage = notification.getMessage();
        String titleTemplate = language == PreferredLanguage.TR ? definition.getTitleTr() : definition.getTitleEn();
        String messageTemplate = language == PreferredLanguage.TR ? definition.getMessageTr() : definition.getMessageEn();
        String title = titleTemplate == null || titleTemplate.isBlank() ? originalTitle : render(titleTemplate, originalTitle, originalMessage, notification.getNote());
        String message = messageTemplate == null || messageTemplate.isBlank() ? originalMessage : render(messageTemplate, originalTitle, originalMessage, notification.getNote());
        String severity = definition.getSeverity() == null || definition.getSeverity().isBlank() ? notification.getSeverity() : definition.getSeverity();
        String route = definition.getTargetRoute() == null || definition.getTargetRoute().isBlank() ? notification.getTargetRoute() : definition.getTargetRoute();
        return new NotificationPresentation(title, message, severity, route);
    }

    private String render(String template, String title, String message, String note) {
        return template
                .replace("{originalTitle}", title == null ? "" : title)
                .replace("{originalMessage}", message == null ? "" : message)
                .replace("{note}", note == null ? "" : note);
    }

    public record NotificationPresentation(String title, String message, String severity, String targetRoute) {}
}
