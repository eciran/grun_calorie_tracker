package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.BodyMeasurementDto;
import com.grun.calorietracker.dto.BodyMeasurementRequestDto;
import com.grun.calorietracker.dto.BodyMeasurementSummaryDto;
import com.grun.calorietracker.service.BodyMeasurementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/progress/measurements")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Progress", description = "Authenticated progress logs and canonical body measurement history.")
public class BodyMeasurementController {

    private final BodyMeasurementService service;

    @PostMapping
    @Operation(summary = "Record body measurements", description = "Normalizes metric or imperial input to kg/cm. Provider plus externalId is idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Measurement recorded."),
            @ApiResponse(responseCode = "400", description = "Measurement validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<BodyMeasurementDto> create(
            @RequestBody @Valid BodyMeasurementRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a manual body measurement", description = "Only user-entered MANUAL measurements can be edited.")
    public ResponseEntity<BodyMeasurementDto> update(
            @PathVariable Long id,
            @RequestBody @Valid BodyMeasurementRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.update(id, request, userDetails.getUsername()));
    }

    @GetMapping
    @Operation(summary = "List body measurement history", description = "Returns up to a 366-day inclusive date range. Defaults to the most recent 90 days.")
    public ResponseEntity<List<BodyMeasurementDto>> list(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LocalDate endDate = end == null ? LocalDate.now() : LocalDate.parse(end);
        LocalDate startDate = start == null ? endDate.minusDays(89) : LocalDate.parse(start);
        return ResponseEntity.ok(service.list(
                userDetails.getUsername(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        ));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get basic body measurement summary", description = "Returns latest values, previous values, simple change and BMI. Predictive analytics are not included.")
    public ResponseEntity<BodyMeasurementSummaryDto> summary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.summary(userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a manual body measurement", description = "Provider-owned measurements must be removed through the provider data flow.")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        service.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
