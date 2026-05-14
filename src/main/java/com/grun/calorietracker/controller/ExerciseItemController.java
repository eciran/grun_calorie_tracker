package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.service.ExerciseItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exercise-items")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Exercise Items", description = "Exercise catalog operations used when creating exercise logs.")
public class ExerciseItemController {

    private final ExerciseItemService exerciseItemService;

    @GetMapping
    @Operation(
            summary = "List exercise items",
            description = "Returns the exercise catalog available for exercise logging."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise items returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<List<ExerciseItemDto>> getAllItems() {
        return ResponseEntity.ok(exerciseItemService.getAllItems());
    }

    @PostMapping
    @Operation(
            summary = "Create an exercise item",
            description = "Adds a new exercise item to the catalog."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise item created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<ExerciseItemDto> addExerciseItem(@RequestBody @Valid ExerciseItemDto dto) {
        ExerciseItemDto created = exerciseItemService.addItem(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update an exercise item",
            description = "Updates an existing exercise catalog item."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise item updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Exercise item was not found.")
    })
    public ResponseEntity<ExerciseItemDto> updateExerciseItem(
            @Parameter(description = "Exercise item id.", example = "1") @PathVariable Long id,
                                                              @RequestBody @Valid ExerciseItemDto dto) {
        ExerciseItemDto updated = exerciseItemService.updateItem(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an exercise item",
            description = "Deletes an exercise catalog item."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Exercise item deleted."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Exercise item was not found.")
    })
    public ResponseEntity<Void> deleteExerciseItem(
            @Parameter(description = "Exercise item id.", example = "1") @PathVariable Long id) {
        exerciseItemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
