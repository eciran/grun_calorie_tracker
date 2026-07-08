package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Open-source recipe catalog JSON import batch. Imported rows become admin candidates and are not published automatically.")
public class AdminRecipeImportBatchRequestDto {
    @Size(max = 160)
    private String batchId;

    private LocalDate createdAt;

    @Size(max = 1000)
    private String purpose;

    @Valid
    private SourcePolicy sourcePolicy;

    @Valid
    @NotEmpty
    private List<AdminRecipeImportCandidateRequestDto> recipes;

    @Data
    public static class SourcePolicy {
        @Size(max = 255)
        private String sourceName;

        @Size(max = 1000)
        private String sourceUrl;

        @Size(max = 1000)
        private String selectionBasis;

        @Size(max = 120)
        private String license;

        @Size(max = 1000)
        private String licenseUrl;

        private Boolean attributionRequired;
        private Boolean shareAlikeRequired;

        @Size(max = 2000)
        private String notes;
    }
}
