package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.service.ExerciseItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exercise-items")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Exercise Items", description = "Exercise catalog operations used when creating exercise logs.")
public class ExerciseItemController {

    private final ExerciseItemService exerciseItemService;

    @GetMapping({"", "/search"})
    @Operation(
            summary = "List exercise items",
            description = "Returns the exercise catalog available for exercise logging. /search is the preferred mobile path; the collection root remains supported for compatibility. Defaults to active items only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise items returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ExerciseItemPageDto> getAllItems(
            @Parameter(description = "Optional search text matched against name, metCode, and description.", example = "run")
            @RequestParam(required = false) String q,
            @Parameter(description = "Optional primary muscle group filter.", example = "Lower Body")
            @RequestParam(required = false) String primaryMuscleGroup,
            @Parameter(description = "Optional equipment filter.", example = "None")
            @RequestParam(required = false) String equipment,
            @Parameter(description = "Optional difficulty filter.", example = "BEGINNER")
            @RequestParam(required = false) ExerciseDifficulty difficulty,
            @Parameter(description = "Whether to return active or inactive catalog items.", example = "true")
            @RequestParam(defaultValue = "true") Boolean active,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25")
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(exerciseItemService.searchItems(
                q,
                primaryMuscleGroup,
                equipment,
                difficulty,
                active,
                page,
                size
        ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create an exercise item",
            description = "Adds a new exercise item to the catalog."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise item created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Exercise item metCode already exists.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ExerciseItemDto> addExerciseItem(@RequestBody @Valid ExerciseItemDto dto) {
        ExerciseItemDto created = exerciseItemService.addItem(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update an exercise item",
            description = "Updates an existing exercise catalog item."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise item updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Exercise item was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Exercise item metCode already exists.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ExerciseItemDto> updateExerciseItem(
            @Parameter(description = "Exercise item id.", example = "1") @PathVariable Long id,
                                                              @RequestBody @Valid ExerciseItemDto dto) {
        ExerciseItemDto updated = exerciseItemService.updateItem(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete an exercise item",
            description = "Deletes an exercise catalog item."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Exercise item deleted.", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Exercise item was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteExerciseItem(
            @Parameter(description = "Exercise item id.", example = "1") @PathVariable Long id) {
        exerciseItemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
