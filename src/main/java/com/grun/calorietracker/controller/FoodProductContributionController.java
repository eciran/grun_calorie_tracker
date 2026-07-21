package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.FoodProductContributionDto;
import com.grun.calorietracker.dto.FoodProductContributionPageDto;
import com.grun.calorietracker.dto.FoodProductContributionRequestDto;
import com.grun.calorietracker.service.FoodProductContributionService;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.EvidenceContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products/contributions")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Food Product Contributions", description = "Review-first product-label contributions. No submission writes directly to the public food catalog.")
public class FoodProductContributionController {
    private final FoodProductContributionService contributionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit private food label evidence", description = "Atomically stores a private label image, calculates its SHA-256 checksum server-side, and queues TR product metadata for admin review.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contribution queued."),
            @ApiResponse(responseCode = "400", description = "GTIN, nutrition, image, or consent validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "The same evidence was already submitted.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FoodProductContributionDto> submit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("metadata") @Valid FoodProductContributionRequestDto request,
            @RequestPart("file") MultipartFile evidenceFile) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contributionService.submit(userDetails.getUsername(), request, evidenceFile));
    }

    @GetMapping
    @Operation(summary = "List my food-label contributions")
    public ResponseEntity<FoodProductContributionPageDto> listMine(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(contributionService.listMine(userDetails.getUsername(), page, size));
    }

    @GetMapping("/{id}/evidence")
    @Operation(summary = "View my private label evidence", description = "Streams evidence only when the contribution belongs to the authenticated user.")
    public ResponseEntity<byte[]> viewMyEvidence(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return evidenceResponse(contributionService.loadEvidenceForUser(id, userDetails.getUsername()));
    }

    private ResponseEntity<byte[]> evidenceResponse(EvidenceContent evidence) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(safeMediaType(evidence.contentType()));
        headers.setContentDisposition(ContentDisposition.inline().filename("product-label").build());
        headers.setCacheControl("private, no-store, max-age=0");
        return ResponseEntity.ok().headers(headers).body(evidence.bytes());
    }

    private MediaType safeMediaType(String value) {
        try {
            return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}