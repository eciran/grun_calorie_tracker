package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.NotificationDto;
import com.grun.calorietracker.dto.NotificationPageDto;
import com.grun.calorietracker.dto.NotificationReadAllResponseDto;
import com.grun.calorietracker.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "Authenticated user's in-app notifications.")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
            summary = "List notifications",
            description = "Returns the authenticated user's notifications. Supports unread-only and type filters for mobile notification centers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications returned.",
                    content = @Content(schema = @Schema(implementation = NotificationPageDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<NotificationPageDto> listNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "When true, only unread notifications are returned.", example = "true")
            @RequestParam(required = false) Boolean unreadOnly,
            @Parameter(description = "Optional notification type filter.", example = "subscription")
            @RequestParam(required = false) String type,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, capped at 100.", example = "25")
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(notificationService.listNotifications(
                userDetails.getUsername(),
                unreadOnly,
                type,
                page,
                size
        ));
    }

    @PatchMapping("/{id}/read")
    @Operation(
            summary = "Mark notification as read",
            description = "Marks one notification as read. Users can only update their own notifications."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read.",
                    content = @Content(schema = @Schema(implementation = NotificationDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Notification could not be found for the authenticated user.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<NotificationDto> markAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Notification id.", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(userDetails.getUsername(), id));
    }

    @PatchMapping("/read-all")
    @Operation(
            summary = "Mark all notifications as read",
            description = "Marks all unread notifications belonging to the authenticated user as read."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread notifications marked as read.",
                    content = @Content(schema = @Schema(implementation = NotificationReadAllResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<NotificationReadAllResponseDto> markAllAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.markAllAsRead(userDetails.getUsername()));
    }
}
