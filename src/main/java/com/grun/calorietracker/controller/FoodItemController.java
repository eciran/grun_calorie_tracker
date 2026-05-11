package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.service.FoodItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Food product management endpoints")
public class FoodItemController {

    private final FoodItemService foodItemService;

    @GetMapping("/search")
    public ResponseEntity<List<FoodProductDto>> searchProducts(@RequestParam String q) {
        FoodSearchCriteriaDto criteria = new FoodSearchCriteriaDto();
        criteria.setQuery(q);

        List<FoodProductDto> products = foodItemService.searchFoodItems(criteria);

        if (products.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(products);
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<FoodProductDto> getProductByBarcode(@PathVariable String barcode) {
        FoodItemEntity foodItemEntity = foodItemService.getOrSaveFoodItemByBarcode(barcode);
        return ResponseEntity.ok(FoodItemMapper.mapEntityToDto(foodItemEntity));
    }

    @PostMapping
    @Operation(summary = "Add a custom food product")
    public ResponseEntity<FoodProductDto> addProduct(@RequestBody FoodProductDto dto) {
        FoodProductDto saved = foodItemService.addProduct(dto);
        return ResponseEntity.ok(saved);
    }
}