package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.PushTokenDto;
import com.grun.calorietracker.dto.PushTokenRegisterRequestDto;
import com.grun.calorietracker.service.PushTokenService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices/push-tokens")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Push Tokens", description = "Authenticated user's mobile push token registration.")
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @GetMapping
    @Operation(summary = "List registered push tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Push tokens returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<PushTokenDto>> list(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(pushTokenService.list(userDetails.getUsername()));
    }

    @PostMapping
    @Operation(summary = "Register or refresh push token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Push token registered."),
            @ApiResponse(responseCode = "400", description = "Invalid push token request.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<PushTokenDto> register(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid PushTokenRegisterRequestDto request) {
        return ResponseEntity.ok(pushTokenService.register(userDetails.getUsername(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke push token")
    public ResponseEntity<PushTokenDto> revoke(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(pushTokenService.revoke(userDetails.getUsername(), id));
    }
}
