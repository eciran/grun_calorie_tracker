package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ProductTourDecisionRequestDto;
import com.grun.calorietracker.dto.ProductTourDto;
import com.grun.calorietracker.service.ProductTourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account/product-tours")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Product Tours", description = "Authenticated user's product tour state.")
public class ProductTourController {

    private final ProductTourService productTourService;

    @GetMapping("/{tourKey}")
    @Operation(summary = "Get product tour state")
    public ResponseEntity<ProductTourDto> get(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String tourKey,
            @RequestParam String version) {
        return ResponseEntity.ok(productTourService.get(
                userDetails.getUsername(), tourKey, version));
    }

    @PutMapping("/{tourKey}")
    @Operation(summary = "Complete, skip, or dismiss a product tour")
    public ResponseEntity<ProductTourDto> decide(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String tourKey,
            @RequestParam String version,
            @RequestBody @Valid ProductTourDecisionRequestDto request) {
        return ResponseEntity.ok(productTourService.recordDecision(
                userDetails.getUsername(), tourKey, version, request.getStatus()));
    }
}
