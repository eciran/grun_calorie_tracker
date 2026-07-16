package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.MealPlanTrackingServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MealPlanTrackingServiceImplTest {

    @Mock private MealPlanRepository mealPlanRepository;
    @Mock private MealPlanItemRepository mealPlanItemRepository;
    @Mock private MealPlanItemConsumptionRepository consumptionRepository;
    @Mock private FoodLogsRepository foodLogsRepository;
    @Mock private RecipeLogRepository recipeLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private FoodLogsService foodLogsService;
    @Mock private RecipeLogService recipeLogService;
    @Mock private MealPlanService mealPlanService;
    @Mock private UserTimeZoneSupport userTimeZoneSupport;

    private MealPlanTrackingServiceImpl service;
    private UserEntity user;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        service = new MealPlanTrackingServiceImpl(
                mealPlanRepository, mealPlanItemRepository, consumptionRepository,
                foodLogsRepository, recipeLogRepository, userRepository,
                foodLogsService, recipeLogService, mealPlanService, userTimeZoneSupport);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@grun.app");
        today = LocalDate.of(2026, 7, 15);
        lenient().when(userTimeZoneSupport.today(user)).thenReturn(today);
    }

    @Test
    void activate_deactivatesPreviousPlanAndKeepsOneActive() {
        MealPlanEntity previous = plan(10L, MealPlanStatus.ACTIVE);
        MealPlanEntity target = plan(11L, MealPlanStatus.DRAFT);
        MealPlanDto expected = new MealPlanDto();
        expected.setId(11L);
        expected.setStatus(MealPlanStatus.ACTIVE);
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(11L, user)).thenReturn(Optional.of(target));
        when(mealPlanRepository.findByUserAndStatus(user, MealPlanStatus.ACTIVE)).thenReturn(List.of(previous));
        when(mealPlanService.getMealPlan(user.getEmail(), 11L)).thenReturn(expected);

        MealPlanDto result = service.activate(user.getEmail(), 11L);

        assertThat(previous.getStatus()).isEqualTo(MealPlanStatus.DRAFT);
        assertThat(target.getStatus()).isEqualTo(MealPlanStatus.ACTIVE);
        assertThat(result.getId()).isEqualTo(11L);
        verify(mealPlanRepository).save(target);
    }

    @Test
    void getActiveForDate_returnsMealsWithExistingConsumptionState() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        plan.setName("Training week");
        MealPlanItemDto itemDto = new MealPlanItemDto();
        itemDto.setId(101L);
        itemDto.setPlanDate(today);
        itemDto.setMealType("LUNCH");
        MealPlanDto planDto = new MealPlanDto();
        planDto.setItems(List.of(itemDto));
        MealPlanItemEntity item = snapshotItem(plan, 101L, today);
        MealPlanItemConsumptionEntity consumed = consumption(item, "meal-key-101");
        consumed.setId(501L);
        consumed.setStatus(MealPlanItemConsumptionStatus.SKIPPED);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(mealPlanRepository.findActiveForDate(user, MealPlanStatus.ACTIVE, today)).thenReturn(Optional.of(plan));
        when(mealPlanService.getMealPlan(user.getEmail(), 11L)).thenReturn(planDto);
        when(consumptionRepository.findByMealPlanItemMealPlanIdAndUserOrderByCreatedAtDesc(11L, user))
                .thenReturn(List.of(consumed));

        MealPlanTodayDto result = service.getActiveForDate(user.getEmail(), today);

        assertThat(result.getPlanId()).isEqualTo(11L);
        assertThat(result.getMeals()).hasSize(1);
        assertThat(result.getMeals().get(0).getMealType()).isEqualTo("LUNCH");
        assertThat(result.getMeals().get(0).getItems().get(0).getConsumption().getStatus())
                .isEqualTo(MealPlanItemConsumptionStatus.SKIPPED);
    }

    @Test
    void logSnapshot_scalesNutritionAndRecordsPartialConsumption() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        MealPlanItemEntity item = snapshotItem(plan, 101L, today);
        item.setPortionSize(150.0);
        item.setSnapshotCalories(300.0);
        item.setSnapshotProtein(30.0);
        item.setSnapshotCarbs(24.0);
        item.setSnapshotFat(12.0);
        item.setSnapshotFiber(6.0);
        MealPlanItemLogRequestDto request = logRequest(120.0, FoodPortionUnit.GRAM, today.atTime(12, 30));
        FoodLogsDto savedDto = snapshotLogDto(701L, 240.0, 24.0, 19.2, 9.6);
        FoodLogsEntity savedEntity = new FoodLogsEntity();
        savedEntity.setId(701L);
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(consumptionRepository.findByUserAndIdempotencyKey(user, "meal-key-101")).thenReturn(Optional.empty());
        when(mealPlanItemRepository.findOwnedForUpdate(101L, 11L, user)).thenReturn(Optional.of(item));
        when(consumptionRepository.findByMealPlanItemAndUser(item, user)).thenReturn(Optional.empty());
        when(foodLogsService.addAiEstimateFoodLog(any(FoodLogsDto.class), eq(user.getEmail()))).thenReturn(savedDto);
        when(foodLogsRepository.findByIdAndUser(701L, user)).thenReturn(Optional.of(savedEntity));
        when(consumptionRepository.save(any())).thenAnswer(invocation -> {
            MealPlanItemConsumptionEntity value = invocation.getArgument(0);
            value.setId(501L);
            value.setCreatedAt(today.atStartOfDay());
            return value;
        });

        MealPlanItemConsumptionDto result = service.logItem(
                user.getEmail(), 11L, 101L, "meal-key-101", request);

        ArgumentCaptor<FoodLogsDto> diaryRequest = ArgumentCaptor.forClass(FoodLogsDto.class);
        verify(foodLogsService).addAiEstimateFoodLog(diaryRequest.capture(), eq(user.getEmail()));
        assertThat(diaryRequest.getValue().getSource()).isEqualTo(FoodLogSource.MEAL_PLAN);
        assertThat(diaryRequest.getValue().getSnapshotCalories()).isEqualTo(240.0);
        assertThat(diaryRequest.getValue().getSnapshotProtein()).isEqualTo(24.0);
        assertThat(result.getStatus()).isEqualTo(MealPlanItemConsumptionStatus.PARTIALLY_CONSUMED);
        assertThat(result.getQuantityVariance()).isEqualTo(-30.0);
        assertThat(result.getQuantityVariancePercent()).isEqualTo(-20.0);
        assertThat(result.getConsumedNutrition().getCalories()).isEqualTo(240.0);
    }

    @Test
    void logSnapshot_scalesNutritionForAmountAbovePlan() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        MealPlanItemEntity item = snapshotItem(plan, 101L, today);
        item.setPortionSize(150.0);
        FoodLogsDto savedDto = snapshotLogDto(702L, 360.0, 36.0, 28.8, 14.4);
        FoodLogsEntity savedEntity = new FoodLogsEntity();
        savedEntity.setId(702L);
        prepareNewDecision(item, "meal-key-102");
        when(foodLogsService.addAiEstimateFoodLog(any(FoodLogsDto.class), eq(user.getEmail()))).thenReturn(savedDto);
        when(foodLogsRepository.findByIdAndUser(702L, user)).thenReturn(Optional.of(savedEntity));
        when(consumptionRepository.save(any())).thenAnswer(invocation -> {
            MealPlanItemConsumptionEntity value = invocation.getArgument(0);
            value.setId(502L);
            value.setCreatedAt(today.atStartOfDay());
            return value;
        });

        MealPlanItemConsumptionDto result = service.logItem(
                user.getEmail(), 11L, 101L, "meal-key-102",
                logRequest(180.0, FoodPortionUnit.GRAM, today.atTime(12, 0)));

        ArgumentCaptor<FoodLogsDto> diaryRequest = ArgumentCaptor.forClass(FoodLogsDto.class);
        verify(foodLogsService).addAiEstimateFoodLog(diaryRequest.capture(), eq(user.getEmail()));
        assertThat(diaryRequest.getValue().getSnapshotCalories()).isEqualTo(360.0);
        assertThat(result.getStatus()).isEqualTo(MealPlanItemConsumptionStatus.LOGGED);
        assertThat(result.getQuantityVariance()).isEqualTo(30.0);
        assertThat(result.getQuantityVariancePercent()).isEqualTo(20.0);
    }

    @Test
    void logItem_rejectsItemOutsideAuthenticatedOwnership() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(consumptionRepository.findByUserAndIdempotencyKey(user, "meal-key-404")).thenReturn(Optional.empty());
        when(mealPlanItemRepository.findOwnedForUpdate(404L, 11L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.logItem(
                user.getEmail(), 11L, 404L, "meal-key-404",
                logRequest(150.0, FoodPortionUnit.GRAM, today.atTime(12, 0))))
                .isInstanceOf(com.grun.calorietracker.exception.ResourceNotFoundException.class);
        verifyNoInteractions(foodLogsService, recipeLogService);
    }
    @Test
    void logSnapshot_replaysSameIdempotencyKeyWithoutSecondDiaryWrite() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        MealPlanItemEntity item = snapshotItem(plan, 101L, today);
        MealPlanItemConsumptionEntity existing = consumption(item, "meal-key-101");
        existing.setId(501L);
        existing.setStatus(MealPlanItemConsumptionStatus.LOGGED);
        existing.setConsumedQuantity(150.0);
        existing.setConsumedUnit(FoodPortionUnit.GRAM);
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(consumptionRepository.findByUserAndIdempotencyKey(user, "meal-key-101"))
                .thenReturn(Optional.of(existing));

        MealPlanItemConsumptionDto result = service.logItem(
                user.getEmail(), 11L, 101L, "meal-key-101",
                logRequest(150.0, FoodPortionUnit.GRAM, today.atTime(12, 0)));

        assertThat(result.getId()).isEqualTo(501L);
        verifyNoInteractions(foodLogsService);
        verify(mealPlanItemRepository, never()).findOwnedForUpdate(anyLong(), anyLong(), any());
    }

    @Test
    void logSnapshot_rejectsDifferentUnitBeforeDiaryWrite() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        MealPlanItemEntity item = snapshotItem(plan, 101L, today);
        prepareNewDecision(item, "meal-key-101");

        assertThatThrownBy(() -> service.logItem(
                user.getEmail(), 11L, 101L, "meal-key-101",
                logRequest(1.0, FoodPortionUnit.SERVING, today.atTime(12, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("planned unit");
        verifyNoInteractions(foodLogsService);
    }

    @Test
    void skipItem_rejectsFuturePlanItem() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        MealPlanItemEntity item = snapshotItem(plan, 101L, today.plusDays(1));
        prepareNewDecision(item, "meal-key-101");

        assertThatThrownBy(() -> service.skipItem(
                user.getEmail(), 11L, 101L, "meal-key-101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
        verify(consumptionRepository, never()).save(any());
    }

    @Test
    void skipItem_rejectsSecondDecisionWithAnotherKey() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        MealPlanItemEntity item = snapshotItem(plan, 101L, today);
        MealPlanItemConsumptionEntity existing = consumption(item, "first-key-101");
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(consumptionRepository.findByUserAndIdempotencyKey(user, "second-key-101")).thenReturn(Optional.empty());
        when(mealPlanItemRepository.findOwnedForUpdate(101L, 11L, user)).thenReturn(Optional.of(item));
        when(consumptionRepository.findByMealPlanItemAndUser(item, user)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.skipItem(
                user.getEmail(), 11L, 101L, "second-key-101"))
                .isInstanceOf(RequestConflictException.class);
        verify(consumptionRepository, never()).save(any());
    }

    @Test
    void logCatalogItem_rejectsServingWithoutVerifiedConversion() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        FoodItemEntity food = new FoodItemEntity();
        food.setId(31L);
        food.setServingSizeGrams(null);
        MealPlanItemEntity item = new MealPlanItemEntity();
        item.setId(101L);
        item.setMealPlan(plan);
        item.setPlanDate(today);
        item.setMealType("LUNCH");
        item.setItemType(MealPlanItemType.FOOD_ITEM);
        item.setFoodItem(food);
        item.setPortionSize(1.0);
        item.setPortionUnit(FoodPortionUnit.SERVING);
        prepareNewDecision(item, "meal-key-101");

        assertThatThrownBy(() -> service.logItem(
                user.getEmail(), 11L, 101L, "meal-key-101",
                logRequest(1.0, FoodPortionUnit.SERVING, today.atTime(12, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verified conversion");
        verifyNoInteractions(foodLogsService);
    }

    @Test
    void skipItem_recordsDecisionWithoutDiaryWrite() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        MealPlanItemEntity item = snapshotItem(plan, 101L, today);
        prepareNewDecision(item, "meal-key-101");
        when(consumptionRepository.save(any())).thenAnswer(invocation -> {
            MealPlanItemConsumptionEntity value = invocation.getArgument(0);
            value.setId(501L);
            value.setCreatedAt(today.atStartOfDay());
            return value;
        });

        MealPlanItemConsumptionDto result = service.skipItem(
                user.getEmail(), 11L, 101L, "meal-key-101");

        assertThat(result.getStatus()).isEqualTo(MealPlanItemConsumptionStatus.SKIPPED);
        assertThat(result.getFoodLogId()).isNull();
        assertThat(result.getRecipeLogId()).isNull();
        verifyNoInteractions(foodLogsService, recipeLogService);
    }

    @Test
    void replaceItem_linksOwnedSameDayDiaryRecord() {
        MealPlanEntity plan = plan(11L, MealPlanStatus.ACTIVE);
        MealPlanItemEntity item = snapshotItem(plan, 101L, today);
        prepareNewDecision(item, "meal-key-101");
        FoodLogsEntity replacement = new FoodLogsEntity();
        replacement.setId(801L);
        replacement.setUser(user);
        replacement.setPortionSize(180.0);
        replacement.setPortionUnit(FoodPortionUnit.GRAM);
        replacement.setLogDate(today.atTime(13, 0));
        replacement.setSnapshotCalories(360.0);
        replacement.setSnapshotProtein(36.0);
        replacement.setSnapshotCarbs(28.0);
        replacement.setSnapshotFat(14.0);
        when(foodLogsRepository.findByIdAndUser(801L, user)).thenReturn(Optional.of(replacement));
        when(consumptionRepository.save(any())).thenAnswer(invocation -> {
            MealPlanItemConsumptionEntity value = invocation.getArgument(0);
            value.setId(501L);
            value.setCreatedAt(today.atStartOfDay());
            return value;
        });
        MealPlanItemReplaceRequestDto request = new MealPlanItemReplaceRequestDto();
        request.setFoodLogId(801L);

        MealPlanItemConsumptionDto result = service.replaceItem(
                user.getEmail(), 11L, 101L, "meal-key-101", request);

        assertThat(result.getStatus()).isEqualTo(MealPlanItemConsumptionStatus.REPLACED);
        assertThat(result.getFoodLogId()).isEqualTo(801L);
        assertThat(result.getConsumedNutrition().getCalories()).isEqualTo(360.0);
        verifyNoInteractions(foodLogsService, recipeLogService);
    }
    private void prepareNewDecision(MealPlanItemEntity item, String key) {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(consumptionRepository.findByUserAndIdempotencyKey(user, key)).thenReturn(Optional.empty());
        when(mealPlanItemRepository.findOwnedForUpdate(item.getId(), item.getMealPlan().getId(), user))
                .thenReturn(Optional.of(item));
        when(consumptionRepository.findByMealPlanItemAndUser(item, user)).thenReturn(Optional.empty());
    }

    private MealPlanEntity plan(Long id, MealPlanStatus status) {
        MealPlanEntity value = new MealPlanEntity();
        value.setId(id);
        value.setUser(user);
        value.setName("Plan");
        value.setStartDate(today.minusDays(1));
        value.setEndDate(today.plusDays(5));
        value.setStatus(status);
        return value;
    }

    private MealPlanItemEntity snapshotItem(MealPlanEntity plan, Long id, LocalDate date) {
        MealPlanItemEntity item = new MealPlanItemEntity();
        item.setId(id);
        item.setMealPlan(plan);
        item.setPlanDate(date);
        item.setMealType("LUNCH");
        item.setItemType(MealPlanItemType.AI_SNAPSHOT);
        item.setSnapshotName("Chicken rice bowl");
        item.setPortionSize(150.0);
        item.setPortionUnit(FoodPortionUnit.GRAM);
        item.setSnapshotCalories(300.0);
        item.setSnapshotProtein(30.0);
        item.setSnapshotCarbs(24.0);
        item.setSnapshotFat(12.0);
        return item;
    }

    private MealPlanItemConsumptionEntity consumption(MealPlanItemEntity item, String key) {
        MealPlanItemConsumptionEntity value = new MealPlanItemConsumptionEntity();
        value.setMealPlanItem(item);
        value.setUser(user);
        value.setIdempotencyKey(key);
        value.setPlannedQuantity(item.getPortionSize());
        value.setPlannedUnit(item.getPortionUnit());
        return value;
    }

    private MealPlanItemLogRequestDto logRequest(double quantity, FoodPortionUnit unit, LocalDateTime at) {
        MealPlanItemLogRequestDto value = new MealPlanItemLogRequestDto();
        value.setConsumedQuantity(quantity);
        value.setConsumedUnit(unit);
        value.setLoggedAt(at);
        return value;
    }

    private FoodLogsDto snapshotLogDto(Long id, double calories, double protein, double carbs, double fat) {
        FoodLogsDto value = new FoodLogsDto();
        value.setId(id);
        value.setSnapshotCalories(calories);
        value.setSnapshotProtein(protein);
        value.setSnapshotCarbs(carbs);
        value.setSnapshotFat(fat);
        return value;
    }
}
