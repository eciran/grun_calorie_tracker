package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminUserSupportNoteRequestDto(
        @NotBlank
        @Size(max = 1000)
        String note,

        @Size(max = 8)
        Set<@Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String> tags
) {
}
