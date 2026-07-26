package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Admin decision rejecting an AI quota refund request.")
public class AdminAiQuotaRefundRejectRequestDto {
    @NotBlank(message = "Refund rejection reason is required.")
    @Size(max = 500, message = "Refund rejection reason must be 500 characters or fewer.")
    private String reason;
}