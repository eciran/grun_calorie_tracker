package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated duplicate product group response.")
public class FoodProductDuplicateGroupPageDto {

    @Schema(description = "Duplicate groups in the current page.")
    private List<FoodProductDuplicateGroupDto> content;

    @Schema(description = "Current zero-based page number.", example = "0")
    private Integer page;

    @Schema(description = "Requested page size.", example = "25")
    private Integer size;

    @Schema(description = "Total number of duplicate barcode groups.", example = "14")
    private Long totalElements;

    @Schema(description = "Total number of duplicate group pages.", example = "1")
    private Integer totalPages;

    @Schema(description = "Whether this is the first page.", example = "true")
    private Boolean first;

    @Schema(description = "Whether this is the last page.", example = "true")
    private Boolean last;
}
