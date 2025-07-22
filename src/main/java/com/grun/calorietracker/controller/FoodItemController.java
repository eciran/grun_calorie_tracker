package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.service.OpenFoodFactsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class OpenFoodFactsController {

    private final OpenFoodFactsService openFoodFactsService;

    @GetMapping("/search")
    public ResponseEntity<List<FoodProductDto>> searchProducts(@RequestParam String q) {
        List<FoodProductDto> products = openFoodFactsService.searchProducts(q);
        if (products.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(products);
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<FoodProductDto> getProductByBarcode(@PathVariable String barcode) {
        Optional<FoodProductDto> productOptional = openFoodFactsService.getProductByBarcode(barcode);

        return productOptional.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
