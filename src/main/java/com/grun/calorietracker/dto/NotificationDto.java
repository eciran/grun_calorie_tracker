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
    private Boolean read;
    private LocalDateTime createdAt;
}
