package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Password setup or password change request for the authenticated account.")
public class AccountPasswordRequestDto {

    @Schema(description = "Current password. Required when the account already has a user-managed password.", example = "CurrentPass1!")
    private String currentPassword;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, message = "{validation.password.size}")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,}$",
            message = "{validation.password.pattern}"
    )
    @Schema(description = "New password with at least 8 characters, uppercase, lowercase, number, and special character.", example = "NewStrongPass1!")
    private String newPassword;
}
