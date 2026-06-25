package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Paged fasting session history result.")
public class FastingSessionPageDto {

    @Schema(description = "Fasting sessions for the requested page.")
    private List<FastingSessionDto> content;

    @Schema(description = "Current zero-based page number.", example = "0")
    private int page;

    @Schema(description = "Requested page size.", example = "20")
    private int size;

    @Schema(description = "Total matching fasting sessions.", example = "42")
    private long totalElements;

    @Schema(description = "Total page count.", example = "3")
    private int totalPages;
}
