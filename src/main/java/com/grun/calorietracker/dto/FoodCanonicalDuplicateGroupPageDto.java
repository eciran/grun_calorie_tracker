package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated canonical generic duplicate candidate response.")
public class FoodCanonicalDuplicateGroupPageDto {

    @Schema(description = "Canonical duplicate candidate groups in the current page.")
    private List<FoodCanonicalDuplicateGroupDto> content;

    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private Boolean first;
    private Boolean last;
}