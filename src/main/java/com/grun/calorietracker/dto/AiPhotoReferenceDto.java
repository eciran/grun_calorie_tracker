package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Short-lived AI meal photo reference created after backend upload.")
public class AiPhotoReferenceDto {
    @Schema(description = "Image reference URL to send to the AI photo draft endpoint.")
    private String imageReference;

    @Schema(description = "Server-side storage token for traceability.")
    private String storageToken;

    @Schema(description = "Reference expiration timestamp.")
    private LocalDateTime expiresAt;
}
