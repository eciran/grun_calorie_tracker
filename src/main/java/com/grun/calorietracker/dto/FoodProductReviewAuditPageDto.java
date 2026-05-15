package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Paginated food product review audit response.")
public class FoodProductReviewAuditPageDto {

    @Schema(description = "Audit entries for the current page.")
    private List<FoodProductReviewAuditDto> content;

    @Schema(description = "Zero-based page number.", example = "0")
    private int page;

    @Schema(description = "Page size.", example = "25")
    private int size;

    @Schema(description = "Total audit entries.", example = "4")
    private long totalElements;

    @Schema(description = "Total pages.", example = "1")
    private int totalPages;

    @Schema(description = "Whether this is the first page.", example = "true")
    private boolean first;

    @Schema(description = "Whether this is the last page.", example = "true")
    private boolean last;
}
