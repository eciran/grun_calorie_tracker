package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogCopyMealRequestDto;
import com.grun.calorietracker.dto.FoodLogMealSummaryDto;
import com.grun.calorietracker.dto.FoodLogRecentMealDto;
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
    void addFoodLog_whenGramUnitProvided_usesEnteredGramsForNutritionBasis() {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(250.0);
        dto.setPortionUnit(FoodPortionUnit.GRAM);
        dto.setMealType("lunch");
        dto.setLogDate(LocalDateTime.now());

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(foodItem));
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.save(any(FoodLogsEntity.class))).thenAnswer(invocation -> {
            FoodLogsEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });

        FoodLogsDto result = foodLogsService.addFoodLog(dto, "test@test.com");

        assertEquals(FoodPortionUnit.GRAM, result.getPortionUnit());
        assertEquals(250.0, result.getNormalizedPortionGrams());
        verify(foodLogsRepository).save(argThat(entity ->
                entity.getPortionUnit() == FoodPortionUnit.GRAM
                        && entity.getNormalizedPortionGrams().equals(250.0)
        ));
    }

    @Test
    void addFoodLog_whenMilliliterUnitProvided_usesEnteredMillilitersAsCalculationAmount() {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(330.0);
        dto.setPortionUnit(FoodPortionUnit.MILLILITER);
        dto.setMealType("snack");
        dto.setLogDate(LocalDateTime.now());

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(foodItem));
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.save(any(FoodLogsEntity.class))).thenAnswer(invocation -> {
            FoodLogsEntity entity = invocation.getArgument(0);
            entity.setId(12L);
            return entity;
        });

        FoodLogsDto result = foodLogsService.addFoodLog(dto, "test@test.com");

        assertEquals(FoodPortionUnit.MILLILITER, result.getPortionUnit());
        assertEquals(330.0, result.getNormalizedPortionGrams());
        verify(foodLogsRepository).save(argThat(entity ->
                entity.getPortionUnit() == FoodPortionUnit.MILLILITER
                        && entity.getNormalizedPortionGrams().equals(330.0)
        ));
    }

    @Test
    void updateFoodLog_recalculatesPortionAndNormalizesMealType() {
        foodItem.setServingSizeGrams(60.0);
        FoodLogsEntity existing = new FoodLogsEntity();
        existing.setId(20L);
        existing.setUser(user);
        existing.setFoodItem(foodItem);

        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(1L);
        dto.setPortionSize(1.5);
        dto.setPortionUnit(FoodPortionUnit.PIECE);
        dto.setMealType("dinner");
        dto.setLogDate(LocalDateTime.of(2026, 5, 20, 19, 0));

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findByIdAndUser(20L, user)).thenReturn(Optional.of(existing));
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(foodItem));
        when(foodLogsRepository.save(existing)).thenReturn(existing);

        FoodLogsDto result = foodLogsService.updateFoodLog(20L, dto, "test@test.com");

        assertEquals(FoodPortionUnit.PIECE, result.getPortionUnit());
        assertEquals(90.0, result.getNormalizedPortionGrams());
        assertEquals("DINNER", result.getMealType());
        assertEquals(dto.getLogDate(), result.getLogDate());
    }

    @Test
    void copyMeal_clonesSourceLogsToTargetDate() {
        FoodLogsEntity source = new FoodLogsEntity();
        source.setUser(user);
        source.setFoodItem(foodItem);
        source.setPortionSize(2.0);
        source.setPortionUnit(FoodPortionUnit.SERVING);
        source.setNormalizedPortionGrams(120.0);
        source.setMealType("BREAKFAST");
        source.setLogDate(LocalDateTime.of(2026, 5, 21, 7, 45));
        FoodLogCopyMealRequestDto request = new FoodLogCopyMealRequestDto();
        request.setSourceDate(LocalDate.of(2026, 5, 21));
        request.setTargetDate(LocalDate.of(2026, 5, 22));
        request.setMealType("breakfast");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(
                user,
                "BREAKFAST",
                LocalDate.of(2026, 5, 21).atStartOfDay(),
                LocalDate.of(2026, 5, 22).atStartOfDay()
        )).thenReturn(List.of(source));
        when(foodLogsRepository.save(any(FoodLogsEntity.class))).thenAnswer(invocation -> {
            FoodLogsEntity saved = invocation.getArgument(0);
            saved.setId(44L);
            return saved;
        });

        List<FoodLogsDto> result = foodLogsService.copyMeal("test@test.com", request);

        assertEquals(1, result.size());
        assertEquals(LocalDateTime.of(2026, 5, 22, 7, 45), result.get(0).getLogDate());
        assertEquals(120.0, result.get(0).getNormalizedPortionGrams());
        verify(foodItemRepository).save(foodItem);
    }

    @Test
    void getFoodLogsHistory_returnsOrderedDateRangeLogs() {
        FoodLogsEntity entity = new FoodLogsEntity();
        entity.setId(30L);
        entity.setUser(user);
        entity.setFoodItem(foodItem);
        entity.setPortionSize(100.0);
        entity.setPortionUnit(FoodPortionUnit.GRAM);
        entity.setNormalizedPortionGrams(100.0);
        entity.setMealType("LUNCH");
        entity.setLogDate(LocalDateTime.of(2026, 5, 18, 12, 0));
        LocalDateTime start = LocalDate.of(2026, 5, 18).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 5, 20).atStartOfDay();

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, start, end))
                .thenReturn(List.of(entity));

        List<FoodLogsDto> result = foodLogsService.getFoodLogsHistory("test@test.com", start, end);

        assertEquals(1, result.size());
        assertEquals("LUNCH", result.get(0).getMealType());
        assertEquals(entity.getLogDate(), result.get(0).getLogDate());
    }

    @Test
    void getMealSummaries_returnsAllMealBucketsAndTotals() {
        FoodLogsEntity breakfast = new FoodLogsEntity();
        breakfast.setUser(user);
        breakfast.setFoodItem(foodItem);
        breakfast.setPortionSize(150.0);
        breakfast.setNormalizedPortionGrams(150.0);
        breakfast.setMealType("BREAKFAST");
        breakfast.setLogDate(LocalDateTime.of(2026, 5, 21, 8, 0));
        foodItem.setCarbs(1.1);
        foodItem.setFat(11.0);

        LocalDateTime start = LocalDate.of(2026, 5, 21).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 5, 22).atStartOfDay();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, start, end))
                .thenReturn(List.of(breakfast));

        List<FoodLogMealSummaryDto> result = foodLogsService.getMealSummaries("test@test.com", start, end);

        assertEquals(4, result.size());
        assertEquals("BREAKFAST", result.get(0).getMealType());
        assertEquals(232.5, result.get(0).getTotalCalories());
        assertEquals(19.5, result.get(0).getTotalProtein());
        assertEquals(0.0, result.get(1).getTotalCalories());
    }

    @Test
    void getMealSummaries_usesNormalizedPortionGramsForUserEnteredGramAndMlAmounts() {
        FoodItemEntity milk = new FoodItemEntity();
        milk.setId(2L);
        milk.setName("Milk");
        milk.setCalories(60.0);
        milk.setProtein(3.2);
        milk.setFat(3.0);
        milk.setCarbs(4.8);

        FoodLogsEntity gramsLog = new FoodLogsEntity();
        gramsLog.setUser(user);
        gramsLog.setFoodItem(foodItem);
        gramsLog.setPortionSize(50.0);
        gramsLog.setPortionUnit(FoodPortionUnit.GRAM);
        gramsLog.setNormalizedPortionGrams(50.0);
        gramsLog.setMealType("BREAKFAST");
        gramsLog.setLogDate(LocalDateTime.of(2026, 5, 21, 8, 0));

        FoodLogsEntity mlLog = new FoodLogsEntity();
        mlLog.setUser(user);
        mlLog.setFoodItem(milk);
        mlLog.setPortionSize(200.0);
        mlLog.setPortionUnit(FoodPortionUnit.MILLILITER);
        mlLog.setNormalizedPortionGrams(200.0);
        mlLog.setMealType("BREAKFAST");
        mlLog.setLogDate(LocalDateTime.of(2026, 5, 21, 8, 5));

        LocalDateTime start = LocalDate.of(2026, 5, 21).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 5, 22).atStartOfDay();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, start, end))
                .thenReturn(List.of(gramsLog, mlLog));

        List<FoodLogMealSummaryDto> result = foodLogsService.getMealSummaries("test@test.com", start, end);

        assertEquals(197.5, result.get(0).getTotalCalories());
        assertEquals(12.9, result.get(0).getTotalProtein());
        assertEquals(6.0, result.get(0).getTotalFat());
        assertEquals(9.6, result.get(0).getTotalCarbs());
    }

    @Test
    void getRecentMeals_returnsMealOccurrencesWithSourceDate() {
        FoodLogsEntity breakfast = new FoodLogsEntity();
        breakfast.setUser(user);
        breakfast.setFoodItem(foodItem);
        breakfast.setPortionSize(100.0);
        breakfast.setNormalizedPortionGrams(100.0);
        breakfast.setMealType("BREAKFAST");
        breakfast.setLogDate(LocalDateTime.of(2026, 5, 21, 8, 0));
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findRecentMealKeys(eq(1L), eq("REJECTED"), any()))
                .thenReturn(List.<Object[]>of(new Object[]{Date.valueOf("2026-05-21"), "BREAKFAST"}));
        when(foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(
                user,
                "BREAKFAST",
                LocalDate.of(2026, 5, 21).atStartOfDay(),
                LocalDate.of(2026, 5, 22).atStartOfDay()
        )).thenReturn(List.of(breakfast));

        List<FoodLogRecentMealDto> result = foodLogsService.getRecentMeals("test@test.com", 10);

        assertEquals(LocalDate.of(2026, 5, 21), result.get(0).getSourceDate());
        assertEquals("BREAKFAST", result.get(0).getMealType());
        assertEquals(155.0, result.get(0).getTotalCalories());
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
