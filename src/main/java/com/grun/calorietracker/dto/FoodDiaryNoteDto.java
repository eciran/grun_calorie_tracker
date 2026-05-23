package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "Daily food diary note.")
public class FoodDiaryNoteDto {

    @Schema(description = "Food diary note id.", example = "1")
    private Long id;

    @Schema(description = "Diary date.", example = "2026-05-23")
    private LocalDate diaryDate;

    @Schema(description = "User note for the diary day.", example = "Felt hungry after lunch; add more protein tomorrow.")
    private String note;

    @Schema(description = "Note creation timestamp.", example = "2026-05-23T09:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp.", example = "2026-05-23T10:15:00")
    private LocalDateTime updatedAt;
}
