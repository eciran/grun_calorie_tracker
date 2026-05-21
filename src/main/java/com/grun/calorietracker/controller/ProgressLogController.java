package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.service.ProgressLogService;
import com.grun.calorietracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Progress", description = "Authenticated progress logs for body measurements and tracking history.")
public class ProgressLogController {

    private final ProgressLogService progressLogService;
    private final UserService userService;


    @PostMapping("/saveLogs")
    @Operation(
            summary = "Save a progress log",
            description = "Stores a progress entry for the authenticated user and sets the log date to the current server time."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress log saved."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<String> saveProgress(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                       @RequestBody @Valid ProgressLogDto logDto) {
        logDto.setLogDate(LocalDateTime.now());
        progressLogService.saveLog(logDto, userDetails.getUsername());
        return ResponseEntity.ok("Progress saved successfully");
    }

    @GetMapping("/getLogs")
    @Operation(
            summary = "List progress logs",
            description = "Returns all progress logs for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress logs returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<List<ProgressLogDto>> getUserLogs(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        List<ProgressLogDto> logs = progressLogService.getUserLogs(userDetails.getUsername());
        return ResponseEntity.ok(logs);
    }
}
