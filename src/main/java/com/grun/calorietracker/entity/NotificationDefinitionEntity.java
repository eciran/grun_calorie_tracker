package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.NotificationCampaignChannel;
import com.grun.calorietracker.enums.NotificationClassification;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_definitions")
@Data
public class NotificationDefinitionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "notification_key", nullable = false, unique = true, length = 80)
    private String key;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "protected_definition", nullable = false)
    private boolean protectedDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCampaignChannel channel = NotificationCampaignChannel.IN_APP_AND_PUSH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationClassification classification = NotificationClassification.USER_REQUESTED_RESULT;

    @Column(name = "parameter_schema_json", nullable = false, columnDefinition = "TEXT")
    private String parameterSchemaJson = "{}";

    @Column(length = 16)
    private String severity;

    @Column(name = "target_route", length = 255)
    private String targetRoute;

    @Column(name = "title_en", length = 120)
    private String titleEn;

    @Column(name = "message_en", length = 1000)
    private String messageEn;

    @Column(name = "title_tr", length = 120)
    private String titleTr;

    @Column(name = "message_tr", length = 1000)
    private String messageTr;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
