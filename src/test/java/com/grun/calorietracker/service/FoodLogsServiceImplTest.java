package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.service.impl.FoodLogsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoodLogsServiceImplTest {

    @Mock
    private FoodLogsRepository foodLogsRepository;
    @Mock
    private FoodItemRepository foodItemRepository;

    @InjectMocks
    private FoodLogsServiceImpl foodLogsService;

    private UserEntity user;
    private FoodItemEntity foodItem;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        user = new UserEntity();
        user.setId(1L);
        foodItem = new FoodItemEntity();
        foodItem.setId(1L);
        foodItem.setName("Egg");
    }

    @Test
    void testAddFoodLog_success() {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(100.0);
        dto.setMealType("breakfast");
        dto.setLogDate(LocalDateTime.now());

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(foodItem));

        FoodLogsEntity savedEntity = new FoodLogsEntity();
        savedEntity.setId(1L);
        savedEntity.setUser(user);
        savedEntity.setFoodItem(foodItem);
        savedEntity.setPortionSize(100.0);
        savedEntity.setMealType("breakfast");
        savedEntity.setLogDate(dto.getLogDate());

        when(foodLogsRepository.save(any(FoodLogsEntity.class))).thenReturn(savedEntity);

        FoodLogsDto result = foodLogsService.addFoodLog(dto, user);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Egg", result.getFoodName());
        verify(foodLogsRepository, times(1)).save(any(FoodLogsEntity.class));
    }

    @Test
    void testAddFoodLog_foodItemNotFound() {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(99L);

        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> foodLogsService.addFoodLog(dto, user));
    }
}
