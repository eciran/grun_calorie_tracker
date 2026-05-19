package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.service.FoodItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/products", "/api/v1/products"})
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Products", description = "Food product search and barcode lookup with local catalog and external fallback support.")
public class FoodItemController {

    private final FoodItemService foodItemService;


    @GetMapping("/search")
    @Operation(
            summary = "Search food products",
            description = "Searches products by text in the local catalog. The query currently matches product name and barcode."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching products returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<FoodProductSearchPageDto> searchProducts(
            @Parameter(description = "Search text, product name, or barcode fragment.", example = "milk")
            @RequestParam String q,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25")
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(q);

        FoodProductSearchPageDto products = foodItemService.searchFoodItems(criteria, page, size);

        return ResponseEntity.ok(products);
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(
            summary = "Get product by barcode",
            description = "Finds a product by barcode. If the product is not available locally, the service can fetch and cache it from Open Food Facts."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found and returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Product could not be found locally or from the configured external source.")
    })
    public ResponseEntity<FoodProductDto> getProductByBarcode(
            @Parameter(description = "Product barcode.", example = "3017620422003")
            @PathVariable String barcode) {
        FoodItemEntity foodItemEntity = foodItemService.getOrSaveFoodItemByBarcode(barcode);
        return ResponseEntity.ok(FoodItemMapper.mapEntityToDto(foodItemEntity));
    }
}
