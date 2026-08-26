package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdvancedGoalPreviewDto;
import com.grun.calorietracker.dto.AdvancedGoalRequestDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.service.AdvancedUserGoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/goals/advanced")
@RequiredArgsConstructor
public class AdvancedUserGoalController {
    private final AdvancedUserGoalService service;

    @PostMapping("/preview")
    public ResponseEntity<AdvancedGoalPreviewDto> preview(@Valid @RequestBody AdvancedGoalRequestDto request,
                                                          @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.preview(request, user.getUsername()));
    }

    @PostMapping("/save")
    public ResponseEntity<UserGoalDto> save(@Valid @RequestBody AdvancedGoalRequestDto request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.save(request, user.getUsername(), idempotencyKey));
    }

    @PostMapping("/restore-automatic")
    public ResponseEntity<UserGoalDto> restoreAutomatic(@Valid @RequestBody AdvancedGoalRequestDto request,
                                                         @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.restoreAutomatic(request, user.getUsername()));
    }
}
