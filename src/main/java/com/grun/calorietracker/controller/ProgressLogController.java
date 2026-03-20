package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.service.ProgressLogService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressLogController {

    private final ProgressLogService progressLogService;

    @PostMapping("/saveLogs")
    public ResponseEntity<String> saveProgress( @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                               @RequestBody @Valid ProgressLogDto logDto) {
        logDto.setLogDate(LocalDateTime.now());
        progressLogService.saveLog(logDto, userDetails.getUsername());
        return ResponseEntity.ok("Progress saved successfully");
    }

    @GetMapping("/getLogs")
    public ResponseEntity<List<ProgressLogDto>> getUserLogs( @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        List<ProgressLogDto> logs = progressLogService.getUserLogs(userDetails.getUsername());
        return ResponseEntity.ok(logs);
    }
}