package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Admin request to refund AI quota consumed by one AI request.")
public class AdminAiQuotaRefundRequestDto {

    @NotNull(message = "Refund amount is required.")
    @Positive(message = "Refund amount must be greater than zero.")
    @Schema(description = "Number of AI credits to refund. Must not exceed the credits consumed by this request.", example = "1")
    private Integer amount;

    @NotBlank(message = "Refund reason is required.")
    @Size(max = 500, message = "Refund reason must be 500 characters or fewer.")
    @Schema(description = "Admin explanation for quota refund.", example = "AI result was unrelated to the uploaded meal photo.")
    private String reason;
}
