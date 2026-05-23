package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.MealTemplateApplyRequestDto;
import com.grun.calorietracker.dto.MealTemplateCreateRequestDto;
import com.grun.calorietracker.dto.MealTemplateDto;
import com.grun.calorietracker.dto.MealTemplateItemRequestDto;
import com.grun.calorietracker.dto.MealTemplateUpdateRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.MealTemplateEntity;
import com.grun.calorietracker.entity.MealTemplateItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.MealTemplateRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.MealTemplateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
    private MealTemplateRepository mealTemplateRepository;
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
}
