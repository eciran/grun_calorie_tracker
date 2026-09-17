package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.mapper.FoodItemMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodItemBrandDisplayTest {
    @Test
    void legacyBrandDisplayDoesNotMutateStoredIdentity() {
        FoodItemEntity entity = new FoodItemEntity();
        entity.setName("Lean & Green");
        entity.setBrand("Vit-Hit");
        entity.setBarcode("5034033000220");
        entity.setSourceKey("barcode:5034033000220");
        var dto = FoodItemMapper.mapEntityToDto(entity);
        assertEquals("VITHIT", dto.getBrand());
        assertEquals(entity.getBarcode(), dto.getBarcode());
        assertEquals(entity.getSourceKey(), dto.getSourceKey());
        assertEquals("Vit-Hit", entity.getBrand());
        assertEquals("VITHIT", FoodItemMapper.mapDtoToEntity(dto).getBrand());
    }
}
