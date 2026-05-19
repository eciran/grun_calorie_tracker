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

    @Schema(description = "Long-lived refresh token used by mobile clients to request a new access token.", example = "Qh3k9_xxY8Pq...")
    private String refreshToken;

    @Schema(description = "Access token type.", example = "Bearer")
    private String tokenType;

    @Schema(description = "Access token expiry in seconds.", example = "900")
    private Long expiresIn;

    @Schema(description = "Human-readable authentication result.", example = "Login successful")
    private String message;

    public AuthResponse(String token, String message) {
        this.token = token;
        this.tokenType = token == null ? null : "Bearer";
        this.message = message;
    }
}
