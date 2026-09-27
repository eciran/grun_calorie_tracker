package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.CatalogClassificationQualityDto;
import com.grun.calorietracker.dto.FoodBrandSummaryDto;
import com.grun.calorietracker.dto.FoodCategoryTreeDto;
import com.grun.calorietracker.service.CatalogClassificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogClassificationController {
    private final CatalogClassificationService service;

    @GetMapping("/brands")
    public ResponseEntity<List<FoodBrandSummaryDto>> autocompleteBrands(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(service.autocompleteBrands(query, limit));
    }

    @GetMapping("/categories/tree")
    public ResponseEntity<List<FoodCategoryTreeDto>> categoryTree() {
        return ResponseEntity.ok(service.activeCategoryTree());
    }

    @GetMapping("/quality-summary")
    public ResponseEntity<CatalogClassificationQualityDto> qualitySummary() {
        return ResponseEntity.ok(service.qualitySummary());
    }
}
