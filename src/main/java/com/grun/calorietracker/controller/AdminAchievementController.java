package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminAchievementDefinitionDto;
import com.grun.calorietracker.dto.AdminAchievementDefinitionRequestDto;
import com.grun.calorietracker.dto.AdminAchievementMetricsDto;
import com.grun.calorietracker.service.AdminAchievementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/achievements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Achievements", description = "Admin-only achievement definition management.")
public class AdminAchievementController {

    private final AdminAchievementService adminAchievementService;

    @GetMapping
    @Operation(summary = "List achievement definitions")
    public ResponseEntity<List<AdminAchievementDefinitionDto>> listDefinitions() {
        return ResponseEntity.ok(adminAchievementService.listDefinitions());
    }

    @GetMapping("/metrics")
    @Operation(summary = "List supported achievement metric keys")
    public ResponseEntity<AdminAchievementMetricsDto> listMetricKeys() {
        return ResponseEntity.ok(adminAchievementService.listMetricKeys());
    }

    @PostMapping
    @Operation(summary = "Create achievement definition")
    public ResponseEntity<AdminAchievementDefinitionDto> createDefinition(
            @RequestBody @Valid AdminAchievementDefinitionRequestDto request) {
        return ResponseEntity.ok(adminAchievementService.createDefinition(request));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update achievement definition")
    public ResponseEntity<AdminAchievementDefinitionDto> updateDefinition(
            @PathVariable String code,
            @RequestBody @Valid AdminAchievementDefinitionRequestDto request) {
        return ResponseEntity.ok(adminAchievementService.updateDefinition(code, request));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Deactivate achievement definition")
    public ResponseEntity<AdminAchievementDefinitionDto> deactivateDefinition(@PathVariable String code) {
        return ResponseEntity.ok(adminAchievementService.deactivateDefinition(code));
    }
}
