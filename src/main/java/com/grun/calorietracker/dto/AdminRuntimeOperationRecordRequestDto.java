package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminRuntimeOperationRecordRequestDto {
    @NotNull
    private RuntimeOperationRecordType recordType;
    @NotNull
    private RuntimeOperationStatus status;
    @NotBlank @Size(max = 120) @Pattern(regexp = "[A-Za-z0-9._:-]+")
    private String operationKey;
    @NotBlank @Size(max = 160)
    private String title;
    @NotBlank @Size(max = 1000)
    private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime nextRunAt;
    @NotNull
    private Boolean retryable;
}
