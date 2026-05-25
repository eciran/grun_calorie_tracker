package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.service.RevenueCatWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/revenuecat")
@RequiredArgsConstructor
@Tag(name = "RevenueCat Webhooks", description = "Public webhook endpoint used by RevenueCat to synchronize subscription and AI add-on purchases.")
public class RevenueCatWebhookController {

    private final RevenueCatWebhookService revenueCatWebhookService;

    @PostMapping
    @Operation(
            summary = "Receive RevenueCat webhook",
            description = "Accepts RevenueCat subscription events, stores each provider event idempotently, and updates backend subscription/quota state."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook accepted.",
                    content = @Content(schema = @Schema(implementation = RevenueCatWebhookResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Webhook payload is invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Webhook authorization header is invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RevenueCatWebhookResponseDto> receiveWebhook(
            @Parameter(description = "Authorization header configured in the RevenueCat dashboard.")
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody JsonNode payload) {
        return ResponseEntity.ok(revenueCatWebhookService.processWebhook(authorizationHeader, payload));
    }
}
