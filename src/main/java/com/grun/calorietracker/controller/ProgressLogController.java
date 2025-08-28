package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.service.ProgressLogService;
import com.grun.calorietracker.service.UserService;
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
    private final UserService userService;


    @PostMapping("/saveLogs")
    public ResponseEntity<String> saveProgress(@AuthenticationPrincipal UserDetails userDetails,
                                                       @RequestBody @Valid ProgressLogDto logDto) {
        logDto.setLogDate(LocalDateTime.now());
        progressLogService.saveLog(logDto, userDetails.getUsername());
        return ResponseEntity.ok("Progress saved successfully");
    }

    @GetMapping("/getLogs")
    public ResponseEntity<List<ProgressLogDto>> getUserLogs(@AuthenticationPrincipal UserDetails userDetails) {
        List<ProgressLogDto> logs = progressLogService.getUserLogs(userDetails.getUsername());
        return ResponseEntity.ok(logs);
    }
}
