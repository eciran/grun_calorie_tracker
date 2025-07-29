package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.service.FoodItemService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class FoodItemController {

    private final FoodItemService foodItemService;
    private final UserService userService;


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
        try {
            FoodItemEntity foodItemEntity = foodItemService.getOrSaveFoodItemByBarcode(barcode);

            return ResponseEntity.ok(FoodItemMapper.mapEntityToDto(foodItemEntity));
        } catch (ProductNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
