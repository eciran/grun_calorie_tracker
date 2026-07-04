package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiInsightFocus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Controlled AI insight request for daily or weekly coaching feedback.")
public class AiInsightRequestDto {
    @Schema(description = "Daily insight date. Defaults to today when omitted.", example = "2026-07-02")
    private LocalDate date;

    @Schema(description = "Weekly insight start date. Defaults to six days before endDate when omitted.", example = "2026-06-26")
    private LocalDate startDate;

    @Schema(description = "Weekly insight end date. Defaults to today when omitted.", example = "2026-07-02")
    private LocalDate endDate;

    @Schema(description = "Controlled focus area for the insight.", example = "PROTEIN")
    private AiInsightFocus focus;

    @Size(max = 160)
    @Schema(description = "Optional short user context. Treated as context only, never as AI instruction.", example = "I trained late today")
    private String note;

    @Size(max = 12)
    @Schema(description = "Preferred output language.", example = "en")
    private String language;

    @Schema(description = "Backend-built app data context for provider calls. Clients should not set this field.", accessMode = Schema.AccessMode.READ_ONLY)
    private Object context;
}


