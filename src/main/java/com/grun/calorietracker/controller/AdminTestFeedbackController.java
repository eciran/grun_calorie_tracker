package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.TestFeedbackPlatform;
import com.grun.calorietracker.enums.TestFeedbackStatus;
import com.grun.calorietracker.enums.TestFeedbackType;
import com.grun.calorietracker.service.AdminTestFeedbackService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/test-feedback")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Test Feedback")
public class AdminTestFeedbackController {
    private final AdminTestFeedbackService service;

    @GetMapping
    public AdminTestFeedbackPageDto list(@RequestParam(required = false) TestFeedbackStatus status,
                                         @RequestParam(required = false) TestFeedbackType type,
                                         @RequestParam(required = false) TestFeedbackPlatform platform,
                                         @RequestParam(required = false) String route,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.list(status, type, platform, route, page, size);
    }

    @GetMapping("/{id}")
    public AdminTestFeedbackDto detail(@PathVariable Long id) { return service.detail(id); }

    @PatchMapping("/{id}")
    public AdminTestFeedbackDto update(@PathVariable Long id,
                                       @Valid @RequestBody AdminTestFeedbackUpdateRequestDto request,
                                       @AuthenticationPrincipal UserDetails user,
                                       @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return service.update(id, request, user.getUsername(), correlationId);
    }

    @GetMapping("/analytics")
    public AdminTestFeedbackAnalyticsDto analytics() { return service.analytics(); }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) TestFeedbackStatus status,
                                         @RequestParam(required = false) TestFeedbackType type,
                                         @RequestParam(required = false) TestFeedbackPlatform platform,
                                         @RequestParam(required = false) String route) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=test-feedback.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(service.exportCsv(status, type, platform, route));
    }
}
