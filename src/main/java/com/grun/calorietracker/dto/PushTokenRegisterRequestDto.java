package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.DevicePlatform;
import com.grun.calorietracker.enums.PushProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to register or refresh a mobile push token.")
public class PushTokenRegisterRequestDto {
    @NotNull
    @Schema(description = "Push provider that issued the token.", example = "EXPO")
    private PushProvider provider;

    @NotNull
    @Schema(description = "Device platform.", example = "IOS")
    private DevicePlatform platform;

    @Schema(description = "Client-side stable device identifier, if available.", example = "ios-device-uuid")
    private String deviceId;

    @NotBlank
    @Schema(description = "Raw push token. Backend stores it securely and never returns it in full.", example = "ExponentPushToken[xxxx]")
    private String token;
}
