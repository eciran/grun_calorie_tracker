package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.GroceryListManualItemRequestDto;
import com.grun.calorietracker.dto.GroceryListPurchaseRequestDto;
import com.grun.calorietracker.dto.GroceryListQuantityRequestDto;
import com.grun.calorietracker.dto.GroceryListRefreshRequestDto;
import com.grun.calorietracker.dto.PersistedGroceryListDto;
import com.grun.calorietracker.service.GroceryListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grocery-lists")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Grocery Lists", description = "Persistent paid shopping checklists generated from owned meal plans.")
public class GroceryListController {

    private final GroceryListService groceryListService;

    @PostMapping("/meal-plans/{mealPlanId}")
    @Operation(summary = "Create or get a grocery list", description = "Idempotently creates the active persistent grocery list for an owned meal plan.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active grocery list returned."),
            @ApiResponse(responseCode = "403", description = "Grocery List is not included in the resolved subscription."),
            @ApiResponse(responseCode = "404", description = "Meal plan was not found for this user.")
    })
    public ResponseEntity<PersistedGroceryListDto> createFromMealPlan(
            @PathVariable @Positive Long mealPlanId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(groceryListService.createFromMealPlan(userDetails.getUsername(), mealPlanId));
    }

    @GetMapping("/{listId}")
    @Operation(summary = "Get grocery list", description = "Returns one persistent grocery list owned by the authenticated user.")
    public ResponseEntity<PersistedGroceryListDto> get(
            @PathVariable @Positive Long listId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(groceryListService.get(userDetails.getUsername(), listId));
    }

    @PostMapping("/{listId}/items")
    @Operation(summary = "Add manual grocery item")
    public ResponseEntity<PersistedGroceryListDto> addManualItem(
            @PathVariable @Positive Long listId,
            @RequestBody @Valid GroceryListManualItemRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(groceryListService.addManualItem(userDetails.getUsername(), listId, request));
    }

    @PatchMapping("/{listId}/items/{itemId}/purchased")
    @Operation(summary = "Check or uncheck grocery item")
    public ResponseEntity<PersistedGroceryListDto> setPurchased(
            @PathVariable @Positive Long listId,
            @PathVariable @Positive Long itemId,
            @RequestBody @Valid GroceryListPurchaseRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(groceryListService.setPurchased(
                userDetails.getUsername(), listId, itemId, request));
    }

    @PutMapping("/{listId}/items/{itemId}/quantity")
    @Operation(summary = "Override grocery item quantity")
    public ResponseEntity<PersistedGroceryListDto> updateQuantity(
            @PathVariable @Positive Long listId,
            @PathVariable @Positive Long itemId,
            @RequestBody @Valid GroceryListQuantityRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(groceryListService.updateQuantity(
                userDetails.getUsername(), listId, itemId, request));
    }

    @DeleteMapping("/{listId}/items/{itemId}")
    @Operation(summary = "Remove or exclude grocery item", description = "Deletes a manual item or excludes a generated item while preserving source history.")
    public ResponseEntity<PersistedGroceryListDto> removeItem(
            @PathVariable @Positive Long listId,
            @PathVariable @Positive Long itemId,
            @RequestParam @PositiveOrZero Long expectedVersion,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(groceryListService.removeItem(
                userDetails.getUsername(), listId, itemId, expectedVersion));
    }

    @PostMapping("/{listId}/refresh")
    @Operation(summary = "Refresh grocery list from meal plan",
            description = "Merges current meal plan ingredients while preserving manual items, purchase state, exclusions and quantity overrides.")
    public ResponseEntity<PersistedGroceryListDto> refresh(
            @PathVariable @Positive Long listId,
            @RequestBody @Valid GroceryListRefreshRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(groceryListService.refresh(userDetails.getUsername(), listId, request));
    }

    @PostMapping("/{listId}/complete")
    @Operation(summary = "Complete grocery list")
    public ResponseEntity<PersistedGroceryListDto> complete(
            @PathVariable @Positive Long listId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(groceryListService.complete(userDetails.getUsername(), listId));
    }

    @DeleteMapping("/{listId}")
    @Operation(summary = "Archive grocery list")
    public ResponseEntity<Void> archive(
            @PathVariable @Positive Long listId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        groceryListService.archive(userDetails.getUsername(), listId);
        return ResponseEntity.noContent().build();
    }
}
