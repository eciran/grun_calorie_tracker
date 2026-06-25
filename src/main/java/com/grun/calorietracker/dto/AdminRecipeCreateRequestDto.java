package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Admin request for creating a recipe on behalf of a user or admin-owned public catalog entry.")
public class AdminRecipeCreateRequestDto {
    @Email
    @Size(max = 255)
    @Schema(description = "Owner email. If omitted, the authenticated admin account is used.", example = "admin@grun.local")
    private String ownerEmail;

    @Valid
    @NotNull
    @Schema(description = "Recipe payload. Ingredients and serving values follow the normal recipe builder rules.", requiredMode = Schema.RequiredMode.REQUIRED)
    private RecipeRequestDto recipe;

    @Schema(description = "Initial admin verification status. Defaults to RAW_IMPORTED unless public approval is requested.", example = "VERIFIED")
    private VerificationStatus verificationStatus;

    @Schema(description = "Initial recipe visibility. Defaults to PRIVATE.", example = "PUBLIC_ADMIN")
    private RecipeVisibility visibility;

    @Schema(description = "Whether the recipe should be created as archived.", example = "false")
    private Boolean archived;

    @Schema(description = "Initial image source override for admin-created recipes.", example = "ADMIN_UPLOAD")
    private ImageSource imageSource;

    @Schema(description = "Initial image moderation status override.", example = "APPROVED")
    private ImageStatus imageStatus;

    @Size(max = 1000)
    @Schema(description = "Internal admin note stored in the audit trail.")
    private String reviewNote;
}
