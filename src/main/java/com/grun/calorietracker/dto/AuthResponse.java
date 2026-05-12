package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Authentication response containing a JWT token and operation message.")
public class AuthResponse {

    @Schema(description = "JWT bearer token. Use this value in Swagger UI Authorize as the bearer token.", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "Human-readable authentication result.", example = "Login successful")
    private String message;
}
