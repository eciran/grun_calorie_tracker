package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Size(max = 500)
    @Schema(description = "Optional admin note explaining the status change.", example = "Temporary lock after suspicious activity.")
    private String reason;
}
