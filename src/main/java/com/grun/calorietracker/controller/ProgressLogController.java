package com.grun.calorietracker.controller;

import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.ProgressLogService;
import com.grun.calorietracker.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/progress")
public class ProgressLogController {

    private final ProgressLogService progressLogService;
    private final UserService userService;

    public ProgressLogController(ProgressLogService progressLogService, UserService userService) {
        this.progressLogService = progressLogService;
        this.userService = userService;
    }

    @PostMapping("/saveLogs")
    public ResponseEntity<?> saveProgress(@AuthenticationPrincipal UserDetails userDetails,
                                          @RequestBody ProgressLogEntity log) {
        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        log.setUser(user);
        log.setLogDate(LocalDateTime.now());

        progressLogService.saveLog(log);
        return ResponseEntity.ok("Progress saved");
    }

    @GetMapping("/getLogs")
    public ResponseEntity<?> getUserLogs(@AuthenticationPrincipal UserDetails userDetails) {
        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        List<ProgressLogEntity> logs = progressLogService.getUserLogs(user);
        return ResponseEntity.ok(logs);
    }
}
