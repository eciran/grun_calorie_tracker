package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.FastingHistoryArchiveRequestDto;
import com.grun.calorietracker.dto.FastingHistoryCorrectionRequestDto;
import com.grun.calorietracker.dto.FastingHistoryRecordDto;
import com.grun.calorietracker.service.AdvancedFastingHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fasting/advanced/history")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Advanced Fasting History", description = "Owner-scoped manual fasting history and corrections.")
public class AdvancedFastingHistoryController {

    private final AdvancedFastingHistoryService historyService;

    @PostMapping
    @Operation(summary = "Create manual fasting history")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Historical record created."),
            @ApiResponse(responseCode = "400", description = "History range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "History overlaps another session.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FastingHistoryRecordDto> create(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody @Valid FastingHistoryCorrectionRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(historyService.create(user.getUsername(), request));
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "Correct fasting history")
    public ResponseEntity<FastingHistoryRecordDto> correct(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long sessionId,
            @RequestBody @Valid FastingHistoryCorrectionRequestDto request) {
        return ResponseEntity.ok(historyService.correct(user.getUsername(), sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Archive fasting history", description = "Soft-deletes a non-active history record while preserving its audit trail.")
    public ResponseEntity<Void> archive(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long sessionId,
            @RequestBody @Valid FastingHistoryArchiveRequestDto request) {
        historyService.archive(user.getUsername(), sessionId, request.getCorrectionReason());
        return ResponseEntity.noContent().build();
    }
}