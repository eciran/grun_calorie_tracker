package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Paginated admin user management response.")
public class AdminUserPageDto {

    @Schema(description = "Users in the current page.")
    private List<UserProfileDto> content;

    @Schema(description = "Current zero-based page number.", example = "0")
    private int page;

    @Schema(description = "Requested page size.", example = "25")
    private int size;

    @Schema(description = "Total number of matching users.", example = "1240")
    private long totalElements;

    @Schema(description = "Total number of matching pages.", example = "50")
    private int totalPages;

    @Schema(description = "Whether this is the first page.", example = "true")
    private boolean first;

    @Schema(description = "Whether this is the last page.", example = "false")
    private boolean last;
}
