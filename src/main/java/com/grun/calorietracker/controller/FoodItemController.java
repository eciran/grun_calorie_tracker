package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.CustomFoodRequestDto;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodServingOptionDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.dto.ProductCorrectionSuggestionDto;
import com.grun.calorietracker.dto.ProductCorrectionSuggestionRequestDto;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.service.FailedBarcodeScanService;
import com.grun.calorietracker.service.FoodItemService;
import com.grun.calorietracker.service.FoodServingOptionService;
import com.grun.calorietracker.service.ProductCorrectionSuggestionService;
import com.grun.calorietracker.service.UserProductLibraryService;
import com.grun.calorietracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Products", description = "Food product search and barcode lookup with local catalog and external fallback support.")
public class FoodItemController {

    private final FoodItemService foodItemService;
    private final FoodServingOptionService foodServingOptionService;
    private final FailedBarcodeScanService failedBarcodeScanService;
    private final ProductCorrectionSuggestionService productCorrectionSuggestionService;
    private final UserProductLibraryService userProductLibraryService;
    private final UserService userService;


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
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @Parameter(description = "Optional market region preference. Supported values: GLOBAL, TR, UK_IE, EU.", example = "UK_IE")
            @RequestParam(required = false) MarketRegion region,
            @Parameter(description = "Optional catalog type filter.", example = "BRANDED_PRODUCT")
            @RequestParam(required = false) FoodCatalogType catalogType,
            @Parameter(description = "Optional brand filter.", example = "Tesco")
            @RequestParam(required = false) String brand,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(q);
        criteria.setMarketRegion(resolveSearchRegion(region, userDetails));
        criteria.setCatalogType(catalogType);
        criteria.setBrand(brand);

        FoodProductSearchPageDto products = foodItemService.searchFoodItems(criteria, page, size);

        return ResponseEntity.ok(products);
    }

    private MarketRegion resolveSearchRegion(MarketRegion requestedRegion, UserDetails userDetails) {
        if (requestedRegion != null || userDetails == null) {
            return requestedRegion;
        }
        return userService.findByEmail(userDetails.getUsername())
                .map(user -> user.getMarketRegion())
                .orElse(null);
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
            @PathVariable String barcode,
            @Parameter(description = "Optional market region used when recording failed barcode scans.", example = "UK_IE")
            @RequestParam(required = false) MarketRegion region,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(foodItemService.getFoodProductByBarcode(barcode));
        } catch (ProductNotFoundException ex) {
            failedBarcodeScanService.recordFailedScan(
                    barcode,
                    region,
                    userDetails == null ? null : userDetails.getUsername()
            );
            throw ex;
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get product by id",
            description = "Returns one available product by id. User-owned custom foods are visible only to their owner."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found and returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Product was not found or is not visible to the authenticated user.")
    })
    public ResponseEntity<FoodProductDto> getProductById(
            @Parameter(description = "Food product id.", example = "12") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodItemService.getFoodItemById(id, userDetails.getUsername()));
    }

    @GetMapping("/{id}/serving-options")
    @Operation(
            summary = "List product serving options",
            description = "Returns product-specific serving options such as slice, cup, bottle, or package. Clients can pass the selected servingOptionId when creating a food log."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Serving options returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Product was not found or is not visible to the authenticated user.")
    })
    public ResponseEntity<java.util.List<FoodServingOptionDto>> getServingOptions(
            @Parameter(description = "Food product id.", example = "12") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodServingOptionService.getServingOptions(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/correction-suggestions")
    @Operation(
            summary = "Suggest product correction",
            description = "Creates a user-submitted nutrition correction suggestion for admin review."
    )
    public ResponseEntity<ProductCorrectionSuggestionDto> suggestProductCorrection(
            @Parameter(description = "Food product id.", example = "12") @PathVariable Long id,
            @RequestBody @Valid ProductCorrectionSuggestionRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(productCorrectionSuggestionService.suggestCorrection(id, userDetails.getUsername(), request));
    }

    @GetMapping("/recent")
    @Operation(
            summary = "List recently logged products",
            description = "Returns distinct available products most recently used in the authenticated user's food logs."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recent products returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<java.util.List<FoodProductDto>> getRecentProducts(
            @Parameter(description = "Maximum recent product count. Maximum 50.", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProductLibraryService.getRecentProducts(userDetails.getUsername(), limit));
    }

    @GetMapping("/favorites")
    @Operation(summary = "List favorite products", description = "Returns the authenticated user's available favorite food products.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Favorite products returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<java.util.List<FoodProductDto>> getFavoriteProducts(
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "50")
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProductLibraryService.getFavoriteProducts(userDetails.getUsername(), page, size));
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "Favorite a product", description = "Adds an available food product to the authenticated user's favorites.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product added to favorites."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Product was not found or is unavailable.")
    })
    public ResponseEntity<FoodProductDto> addFavoriteProduct(
            @Parameter(description = "Food product id.", example = "12") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProductLibraryService.addFavoriteProduct(userDetails.getUsername(), id));
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "Remove a favorite product", description = "Removes a food product from the authenticated user's favorites.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Favorite product removed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Product was not found.")
    })
    public ResponseEntity<Void> removeFavoriteProduct(
            @Parameter(description = "Food product id.", example = "12") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        userProductLibraryService.removeFavoriteProduct(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/custom")
    @Operation(
            summary = "Create a custom food",
            description = "Creates a user-owned manual food for products or meals that are not available in the shared catalog."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Custom food created."),
            @ApiResponse(responseCode = "400", description = "Custom food validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<FoodProductDto> createCustomFood(
            @RequestBody @Valid CustomFoodRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProductLibraryService.createCustomFood(userDetails.getUsername(), request));
    }

    @PutMapping("/custom/{id}")
    @Operation(
            summary = "Update a custom food",
            description = "Updates a manual food owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Custom food updated."),
            @ApiResponse(responseCode = "400", description = "Custom food validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Custom food was not found for the current user.")
    })
    public ResponseEntity<FoodProductDto> updateCustomFood(
            @Parameter(description = "Custom food product id.", example = "12") @PathVariable Long id,
            @RequestBody @Valid CustomFoodRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProductLibraryService.updateCustomFood(userDetails.getUsername(), id, request));
    }

    @DeleteMapping("/custom/{id}")
    @Operation(
            summary = "Delete a custom food",
            description = "Deletes an unused manual food owned by the authenticated user. Custom foods already referenced by diary history stay available for history integrity."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Custom food deleted."),
            @ApiResponse(responseCode = "400", description = "Custom food is already used in diary history."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Custom food was not found for the current user.")
    })
    public ResponseEntity<Void> deleteCustomFood(
            @Parameter(description = "Custom food product id.", example = "12") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        userProductLibraryService.deleteCustomFood(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/custom")
    @Operation(summary = "List custom foods", description = "Returns manual food products owned by the authenticated user.")
    public ResponseEntity<java.util.List<FoodProductDto>> getCustomFoods(
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size. Maximum 100.", example = "50")
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProductLibraryService.getCustomFoods(userDetails.getUsername(), page, size));
    }
}
