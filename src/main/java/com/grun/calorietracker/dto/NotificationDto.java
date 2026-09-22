package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "User notification entry.")
public class NotificationDto {
    private Long id;
    private String title;
    private String message;
    private String note;
    private String primaryAction;
    private Integer actionAmountMl;
    private String type;
    private String category;
    private String severity;
    private String source;
    private String targetType;
    private String targetId;
    private String targetRoute;
    private Boolean read;
    @Schema(description = "UTC creation timestamp with an explicit offset.", example = "2026-09-06T14:11:00Z")
    private Instant createdAt;
}
