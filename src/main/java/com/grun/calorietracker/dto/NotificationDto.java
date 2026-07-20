package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "User notification entry.")
public class NotificationDto {
    private Long id;
    private String message;
    private String type;
    private String severity;
    private String source;
    private String targetType;
    private String targetId;
    private String targetRoute;
    private Boolean read;
    private LocalDateTime createdAt;
}
