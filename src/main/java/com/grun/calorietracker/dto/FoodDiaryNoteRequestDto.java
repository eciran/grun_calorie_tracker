package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Daily food diary note request.")
public class FoodDiaryNoteRequestDto {

    @NotBlank(message = "{validation.food-diary-note.note.required}")
    @Size(max = 1000, message = "{validation.food-diary-note.note.size}")
    @Schema(description = "User note for one food diary day.", example = "Felt hungry after lunch; add more protein tomorrow.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String note;
}
