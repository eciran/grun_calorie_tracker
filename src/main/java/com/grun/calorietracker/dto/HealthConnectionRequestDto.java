package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Mobile request used after a user grants health permissions on device.")
public class HealthConnectionRequestDto {

    @Size(max = 255)
    @Schema(description = "Provider-specific user/device identifier when available.", example = "apple-health-device")
    private String providerUserId;

    @Size(max = 255)
    @Schema(description = "Device model that will send health data.", example = "iPhone 15")
    private String deviceModel;

    @Size(max = 255)
    @Schema(description = "Mobile app version.", example = "1.0.0")
    private String appVersion;
}
