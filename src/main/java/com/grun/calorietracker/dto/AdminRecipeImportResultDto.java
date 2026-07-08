package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminRecipeImportResultDto {
    private String batchId;
    private int totalCandidates;
    private int createdCandidates;
    private int skippedDuplicates;
    private int failedCandidates;
    private List<AdminRecipeImportCandidateDto> candidates;
}
