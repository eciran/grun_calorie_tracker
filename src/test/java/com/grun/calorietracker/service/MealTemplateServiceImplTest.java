package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.MealTemplateApplyRequestDto;
import com.grun.calorietracker.dto.MealTemplateCreateRequestDto;
import com.grun.calorietracker.dto.MealTemplateDto;
import com.grun.calorietracker.dto.MealTemplateItemRequestDto;
import com.grun.calorietracker.dto.MealTemplateUpdateRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.MealTemplateEntity;
import com.grun.calorietracker.entity.MealTemplateItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.enums.FoodLogSource;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.event.FoodDiaryChangedEvent;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.MealTemplateRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.MealTemplateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealTemplateServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FoodLogsRepository foodLogsRepository;
    @Mock
    private FoodItemRepository foodItemRepository;
    @Mock
    private FoodItemServingOptionRepository foodItemServingOptionRepository;
    @Mock
    private MealTemplateRepository mealTemplateRepository;
    @Mock
    private UserAnalyticsCacheRevisionService analyticsCacheRevisionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private MealTemplateServiceImpl service;

    @Test
    void createFromLoggedMeal_savesReusableTemplateItems() {
        UserEntity user = user();
        FoodItemEntity egg = product();
        FoodLogsEntity source = sourceLog(user, egg);
        MealTemplateCreateRequestDto request = new MealTemplateCreateRequestDto();
        request.setName("Breakfast");
        request.setMealType("breakfast");
        request.setSourceDate(LocalDate.of(2026, 5, 21));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of(source));
        when(mealTemplateRepository.save(any(MealTemplateEntity.class))).thenAnswer(invocation -> {
            MealTemplateEntity template = invocation.getArgument(0);
            template.setId(4L);
            template.setCreatedAt(LocalDateTime.of(2026, 5, 22, 9, 0));
            return template;
        });

        MealTemplateDto result = service.createFromLoggedMeal("user@test.com", request);

        assertEquals(4L, result.getId());
        assertEquals("BREAKFAST", result.getMealType());
        assertEquals("Egg", result.getItems().get(0).getFoodName());
    }

    @Test
    void createFromLoggedMeal_preservesServingOptionIdentity() {
        UserEntity user = user();
        FoodItemEntity egg = product();
        FoodItemServingOptionEntity option = verifiedOption(egg);
        FoodLogsEntity source = sourceLog(user, egg);
        source.setServingOption(option);
        MealTemplateCreateRequestDto request = new MealTemplateCreateRequestDto();
        request.setName("Breakfast");
        request.setMealType("breakfast");
        request.setSourceDate(LocalDate.of(2026, 5, 21));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of(source));
        when(mealTemplateRepository.save(any(MealTemplateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MealTemplateDto result = service.createFromLoggedMeal("user@test.com", request);

        assertEquals(option.getId(), result.getItems().get(0).getServingOptionId());
        assertEquals(option.getLabel(), result.getItems().get(0).getServingOptionLabel());
    }

    @Test
    void getTemplates_clampsPagination() {
        UserEntity user = user();
        MealTemplateEntity template = new MealTemplateEntity();
        template.setId(7L);
        template.setUser(user);
        template.setName("Breakfast");
        template.setMealType("BREAKFAST");
        template.setItems(List.of());
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealTemplateRepository.findByUserOrderByCreatedAtDesc(
                any(UserEntity.class),
                argThat((Pageable pageable) -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 100)
        )).thenReturn(List.of(template));

        List<MealTemplateDto> result = service.getTemplates("user@test.com", -1, 500);

        assertEquals(List.of("Breakfast"), result.stream().map(MealTemplateDto::getName).toList());
    }

    @Test
    void applyTemplate_createsTargetDayFoodLogs() {
        UserEntity user = user();
        FoodItemEntity egg = product();
        MealTemplateEntity template = new MealTemplateEntity();
        template.setId(7L);
        template.setUser(user);
        template.setMealType("BREAKFAST");
        MealTemplateItemEntity item = new MealTemplateItemEntity();
        item.setTemplate(template);
        item.setFoodItem(egg);
        item.setPortionSize(2.0);
        item.setPortionUnit(FoodPortionUnit.SERVING);
        item.setNormalizedPortionGrams(100.0);
        item.setLogTime(LocalTime.of(8, 15));
        template.setItems(List.of(item));
        MealTemplateApplyRequestDto request = new MealTemplateApplyRequestDto();
        request.setTargetDate(LocalDate.of(2026, 5, 22));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealTemplateRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(template));
        when(foodLogsRepository.save(any(FoodLogsEntity.class))).thenAnswer(invocation -> {
            FoodLogsEntity saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        List<FoodLogsDto> result = service.applyTemplate("user@test.com", 7L, request);

        assertEquals(LocalDateTime.of(2026, 5, 22, 8, 15), result.get(0).getLogDate());
        assertEquals("BREAKFAST", result.get(0).getMealType());
        assertEquals(FoodLogSource.TEMPLATE, result.get(0).getSource());
        verify(analyticsCacheRevisionService).bump(user.getId(), AnalyticsMutationSource.FOOD_LOG);
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof FoodDiaryChangedEvent changed
                && changed.email().equals(user.getEmail())
                && changed.date().equals(request.getTargetDate())));
    }

    @Test
    void applyTemplate_preservesVerifiedServingOptionOnCreatedLog() {
        UserEntity user = user();
        FoodItemEntity egg = product();
        FoodItemServingOptionEntity option = verifiedOption(egg);
        MealTemplateEntity template = new MealTemplateEntity();
        template.setId(8L);
        template.setUser(user);
        template.setMealType("BREAKFAST");
        MealTemplateItemEntity item = new MealTemplateItemEntity();
        item.setTemplate(template);
        item.setFoodItem(egg);
        item.setServingOption(option);
        item.setPortionSize(2.0);
        item.setPortionUnit(FoodPortionUnit.SERVING);
        item.setNormalizedPortionGrams(100.0);
        template.setItems(List.of(item));
        MealTemplateApplyRequestDto request = new MealTemplateApplyRequestDto();
        request.setTargetDate(LocalDate.of(2026, 5, 23));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealTemplateRepository.findByIdAndUser(8L, user)).thenReturn(Optional.of(template));
        when(foodLogsRepository.save(any(FoodLogsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<FoodLogsDto> result = service.applyTemplate("user@test.com", 8L, request);

        assertEquals(option.getId(), result.get(0).getServingOptionId());
        verify(foodLogsRepository).save(argThat(log -> log.getServingOption() == option));
    }

    @Test
    void applyTemplate_rejectsServingOptionThatIsNoLongerVerified() {
        UserEntity user = user();
        FoodItemEntity egg = product();
        FoodItemServingOptionEntity option = verifiedOption(egg);
        option.setQualityStatus(FoodServingOptionQualityStatus.NEEDS_REVIEW);
        MealTemplateEntity template = new MealTemplateEntity();
        template.setId(9L);
        template.setUser(user);
        template.setMealType("BREAKFAST");
        MealTemplateItemEntity item = new MealTemplateItemEntity();
        item.setFoodItem(egg);
        item.setServingOption(option);
        item.setPortionSize(1.0);
        item.setPortionUnit(FoodPortionUnit.SERVING);
        item.setNormalizedPortionGrams(50.0);
        template.setItems(List.of(item));
        MealTemplateApplyRequestDto request = new MealTemplateApplyRequestDto();
        request.setTargetDate(LocalDate.of(2026, 5, 23));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealTemplateRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(template));

        assertThrows(IllegalArgumentException.class, () -> service.applyTemplate("user@test.com", 9L, request));
    }

    @Test
    void updateTemplate_replacesMetadataAndItems() {
        UserEntity user = user();
        FoodItemEntity egg = product();
        egg.setServingSizeGrams(50.0);
        MealTemplateEntity template = new MealTemplateEntity();
        template.setId(7L);
        template.setUser(user);
        template.setName("Old");
        template.setMealType("BREAKFAST");
        template.setItems(new java.util.ArrayList<>());
        MealTemplateItemRequestDto itemRequest = new MealTemplateItemRequestDto();
        itemRequest.setFoodItemId(2L);
        itemRequest.setPortionSize(2.0);
        itemRequest.setPortionUnit(FoodPortionUnit.SERVING);
        MealTemplateUpdateRequestDto request = new MealTemplateUpdateRequestDto();
        request.setName("Updated");
        request.setMealType("lunch");
        request.setItems(List.of(itemRequest));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealTemplateRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(template));
        when(foodItemRepository.findById(2L)).thenReturn(Optional.of(egg));
        when(mealTemplateRepository.save(template)).thenReturn(template);

        MealTemplateDto result = service.updateTemplate("user@test.com", 7L, request);

        assertEquals("Updated", result.getName());
        assertEquals("LUNCH", result.getMealType());
        assertEquals(100.0, result.getItems().get(0).getNormalizedPortionGrams());
    }

    @Test
    void updateTemplate_resolvesAndStoresVerifiedServingOption() {
        UserEntity user = user();
        FoodItemEntity egg = product();
        FoodItemServingOptionEntity option = verifiedOption(egg);
        MealTemplateEntity template = new MealTemplateEntity();
        template.setId(10L);
        template.setUser(user);
        template.setItems(new java.util.ArrayList<>());
        MealTemplateItemRequestDto itemRequest = new MealTemplateItemRequestDto();
        itemRequest.setFoodItemId(2L);
        itemRequest.setServingOptionId(9L);
        itemRequest.setPortionSize(2.0);
        itemRequest.setPortionUnit(FoodPortionUnit.SERVING);
        MealTemplateUpdateRequestDto request = new MealTemplateUpdateRequestDto();
        request.setName("Egg meal");
        request.setMealType("breakfast");
        request.setItems(List.of(itemRequest));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealTemplateRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(template));
        when(foodItemRepository.findById(2L)).thenReturn(Optional.of(egg));
        when(foodItemServingOptionRepository.findByIdAndFoodItemAndQualityStatus(
                9L, egg, FoodServingOptionQualityStatus.VERIFIED
        )).thenReturn(Optional.of(option));
        when(mealTemplateRepository.save(template)).thenReturn(template);

        MealTemplateDto result = service.updateTemplate("user@test.com", 10L, request);

        assertEquals(9L, result.getItems().get(0).getServingOptionId());
        assertEquals(100.0, result.getItems().get(0).getNormalizedPortionGrams());
    }

    @Test
    void getTemplates_usesMilliliterReferenceForTemplateNutritionTotals() {
        UserEntity user = user();
        FoodItemEntity drink = product();
        drink.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);
        drink.setCalories(40.0);
        MealTemplateItemEntity item = new MealTemplateItemEntity();
        item.setFoodItem(drink);
        item.setPortionSize(250.0);
        item.setPortionUnit(FoodPortionUnit.MILLILITER);
        item.setNormalizedPortionMilliliters(250.0);
        MealTemplateEntity template = new MealTemplateEntity();
        template.setUser(user);
        template.setItems(List.of(item));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealTemplateRepository.findByUserOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(template));

        List<MealTemplateDto> result = service.getTemplates("user@test.com", 0, 10);

        assertEquals(100.0, result.get(0).getTotalCalories());
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@test.com");
        return user;
    }

    private FoodItemEntity product() {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(2L);
        food.setName("Egg");
        food.setVerificationStatus(VerificationStatus.VERIFIED);
        food.setUsageCount(0L);
        return food;
    }

    private FoodLogsEntity sourceLog(UserEntity user, FoodItemEntity food) {
        FoodLogsEntity log = new FoodLogsEntity();
        log.setUser(user);
        log.setFoodItem(food);
        log.setPortionSize(2.0);
        log.setPortionUnit(FoodPortionUnit.SERVING);
        log.setNormalizedPortionGrams(100.0);
        log.setMealType("BREAKFAST");
        log.setLogDate(LocalDateTime.of(2026, 5, 21, 8, 15));
        return log;
    }

    private FoodItemServingOptionEntity verifiedOption(FoodItemEntity food) {
        FoodItemServingOptionEntity option = new FoodItemServingOptionEntity();
        option.setId(9L);
        option.setFoodItem(food);
        option.setLabel("1 egg");
        option.setUnitType(FoodServingOptionUnit.PIECE);
        option.setQuantity(1.0);
        option.setGramWeight(50.0);
        option.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);
        return option;
    }
}
