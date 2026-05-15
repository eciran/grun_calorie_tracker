package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Paginated exercise catalog response.")
public class ExerciseItemPageDto {

    @Schema(description = "Exercise items in the current page.")
    private List<ExerciseItemDto> content;

    @Schema(description = "Current zero-based page number.", example = "0")
    private Integer page;

    @Schema(description = "Requested page size.", example = "25")
    private Integer size;

    @Schema(description = "Total number of matching exercise items.", example = "12")
    private Long totalElements;

    @Schema(description = "Total number of matching pages.", example = "1")
    private Integer totalPages;

    @Schema(description = "Whether this is the first page.", example = "true")
    private Boolean first;

    @Schema(description = "Whether this is the last page.", example = "true")
    private Boolean last;
}
