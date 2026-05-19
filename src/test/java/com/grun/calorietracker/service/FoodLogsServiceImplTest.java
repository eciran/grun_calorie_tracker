package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.FoodLogsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Date;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoodLogsServiceImplTest {

    @Mock
    private FoodLogsRepository foodLogsRepository;
    @Mock
    private FoodItemRepository foodItemRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FoodLogsServiceImpl foodLogsService;

    private UserEntity user;
    private FoodItemEntity foodItem;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("test@test.com");
        foodItem = new FoodItemEntity();
        foodItem.setId(1L);
        foodItem.setName("Egg");
        foodItem.setBarcode("123456789");
        foodItem.setCalories(155.0);
        foodItem.setProtein(13.0);
        foodItem.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        foodItem.setImageStatus(ImageStatus.NEEDS_REVIEW);
        foodItem.setUsageCount(0L);
    }

    @Test
    void testAddFoodLog_success() {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(100.0);
        dto.setMealType("breakfast");
        dto.setLogDate(LocalDateTime.now());

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(foodItem));
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        FoodLogsEntity savedEntity = new FoodLogsEntity();
        savedEntity.setId(1L);
        savedEntity.setUser(user);
        savedEntity.setFoodItem(foodItem);
        savedEntity.setPortionSize(100.0);
        savedEntity.setPortionUnit(FoodPortionUnit.GRAM);
        savedEntity.setNormalizedPortionGrams(100.0);
        savedEntity.setMealType("breakfast");
        savedEntity.setLogDate(dto.getLogDate());

        when(foodLogsRepository.save(any(FoodLogsEntity.class))).thenReturn(savedEntity);

        FoodLogsDto result = foodLogsService.addFoodLog(dto, "test@test.com");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Egg", result.getFoodName());
        assertEquals(FoodPortionUnit.GRAM, result.getPortionUnit());
        assertEquals(100.0, result.getNormalizedPortionGrams());
        assertEquals(1L, foodItem.getUsageCount());
        assertNotNull(foodItem.getQualityScore());
        assertNotNull(foodItem.getReviewPriority());
        verify(foodLogsRepository, times(1)).save(any(FoodLogsEntity.class));
        verify(foodItemRepository).save(foodItem);
    }

    @Test
    void testAddFoodLog_whenServingUnitProvided_normalizesUsingFoodServingSize() {
        foodItem.setServingSizeGrams(50.0);
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(2.0);
        dto.setPortionUnit(FoodPortionUnit.SERVING);
        dto.setMealType("snack");
        dto.setLogDate(LocalDateTime.now());

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(foodItem));
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.save(any(FoodLogsEntity.class))).thenAnswer(invocation -> {
            FoodLogsEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        FoodLogsDto result = foodLogsService.addFoodLog(dto, "test@test.com");

        assertEquals(FoodPortionUnit.SERVING, result.getPortionUnit());
        assertEquals(100.0, result.getNormalizedPortionGrams());
        verify(foodLogsRepository).save(argThat(entity ->
                entity.getPortionUnit() == FoodPortionUnit.SERVING
                        && entity.getNormalizedPortionGrams().equals(100.0)
        ));
    }

    @Test
    void testAddFoodLog_foodItemNotFound() {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(99L);

        when(foodItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> foodLogsService.addFoodLog(dto, user.getEmail()));
    }

    @Test
    void testGetDailyStats_success() {
        LocalDateTime start = LocalDate.of(2026, 5, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 5, 2).atStartOfDay();

        Object[] row = new Object[]{
                Date.valueOf("2026-05-01"),
                BigDecimal.valueOf(450.5),
                BigDecimal.valueOf(30.0),
                BigDecimal.valueOf(55.25),
                BigDecimal.valueOf(12.75)
        };

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.getDailyStatsByUserAndDateBetween(1L, start, end))
                .thenReturn(Collections.singletonList(row));

        List<FoodLogDailyStatsDto> result = foodLogsService.getDailyStats("test@test.com", start, end);

        assertEquals(1, result.size());
        assertEquals("2026-05-01", result.get(0).getDate());
        assertEquals(450.5, result.get(0).getTotalCalories());
        assertEquals(30.0, result.get(0).getTotalProtein());
        assertEquals(55.25, result.get(0).getTotalCarbs());
        assertEquals(12.75, result.get(0).getTotalFat());
        verify(foodLogsRepository).getDailyStatsByUserAndDateBetween(1L, start, end);
    }

    @Test
    void testGetDailyStats_whenValuesAreNull_mapsTotalsToZero() {
        LocalDateTime start = LocalDate.of(2026, 5, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 5, 2).atStartOfDay();

        Object[] row = new Object[]{Date.valueOf("2026-05-01"), null, null, null, null};

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.getDailyStatsByUserAndDateBetween(1L, start, end))
                .thenReturn(Collections.singletonList(row));

        List<FoodLogDailyStatsDto> result = foodLogsService.getDailyStats("test@test.com", start, end);

        assertEquals(0.0, result.get(0).getTotalCalories());
        assertEquals(0.0, result.get(0).getTotalProtein());
        assertEquals(0.0, result.get(0).getTotalCarbs());
        assertEquals(0.0, result.get(0).getTotalFat());
    }

    @Test
    void testGetDailyStats_whenUserNotFound_throwsInvalidCredentials() {
        LocalDateTime start = LocalDate.of(2026, 5, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 5, 2).atStartOfDay();

        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> foodLogsService.getDailyStats("missing@test.com", start, end));
    }
}
