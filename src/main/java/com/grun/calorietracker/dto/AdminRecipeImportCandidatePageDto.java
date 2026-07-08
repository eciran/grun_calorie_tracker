package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminRecipeImportCandidatePageDto {
    private List<AdminRecipeImportCandidateDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
