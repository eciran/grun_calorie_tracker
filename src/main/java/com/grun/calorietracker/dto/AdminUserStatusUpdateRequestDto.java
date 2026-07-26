package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Admin request to enable/disable or lock/unlock a user account.")
public class AdminUserStatusUpdateRequestDto {

    @NotNull
    @Schema(description = "Whether the account is allowed to authenticate.", example = "true")
    private Boolean accountEnabled;

    @NotNull
    @Schema(description = "Whether the account is locked by admin/security action.", example = "false")
    private Boolean accountLocked;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Required audit reason explaining the status change.", example = "Temporary lock after suspicious activity.")
    private String reason;

    @AssertTrue(message = "Explicit confirmation is required.")
    @Schema(description = "Explicit confirmation for this account access change.", example = "true")
    private boolean confirmed;
}
