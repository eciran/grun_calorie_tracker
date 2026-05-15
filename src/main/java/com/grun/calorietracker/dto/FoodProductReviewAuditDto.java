package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Admin audit entry for a food product review change.")
public class FoodProductReviewAuditDto {

    @Schema(description = "Audit entry id.", example = "10")
    private Long id;

    @Schema(description = "Reviewed food product id.", example = "1")
    private Long foodItemId;

    @Schema(description = "Admin email that performed the review change.", example = "admin@grun.app")
    private String reviewedBy;

    @Schema(description = "Audit action type.", example = "STATUS_CHANGE")
    private FoodProductReviewAuditAction actionType;

    @Schema(description = "Changed product field.", example = "verificationStatus")
    private String fieldName;

    @Schema(description = "Previous value.", example = "RAW_IMPORTED")
    private String oldValue;

    @Schema(description = "New value.", example = "VERIFIED")
    private String newValue;

    @Schema(description = "Optional review note.", example = "Verified from official product label.")
    private String note;

    @Schema(description = "Audit creation timestamp.", example = "2026-05-15T18:55:00")
    private LocalDateTime createdAt;
}
