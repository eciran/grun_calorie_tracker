package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.MealTemplateDto;
import com.grun.calorietracker.dto.QuickLogSuggestionDto;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.MealTemplateService;
import com.grun.calorietracker.service.QuickLogSuggestionService;
import com.grun.calorietracker.service.UserProductLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuickLogSuggestionServiceImpl implements QuickLogSuggestionService {

    private final FoodLogsService foodLogsService;
    private final MealTemplateService mealTemplateService;
    private final UserProductLibraryService userProductLibraryService;

    @Override
    public QuickLogSuggestionDto getSuggestions(String email, LocalDate targetDate, LocalTime localTime, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String mealType = suggestedMealType(localTime == null ? LocalTime.now() : localTime);

        QuickLogSuggestionDto dto = new QuickLogSuggestionDto();
        dto.setTargetDate(targetDate == null ? LocalDate.now() : targetDate);
        dto.setSuggestedMealType(mealType);
        dto.setRecentMeals(foodLogsService.getRecentMeals(email, safeLimit));
        dto.setMealTemplates(sortTemplates(mealTemplateService.getTemplates(email, 0, safeLimit), mealType, safeLimit));
        dto.setRecentProducts(userProductLibraryService.getRecentProducts(email, safeLimit));
        dto.setFavoriteProducts(userProductLibraryService.getFavoriteProducts(email, 0, safeLimit).stream().limit(safeLimit).toList());
        return dto;
    }

    private List<MealTemplateDto> sortTemplates(List<MealTemplateDto> templates, String mealType, int limit) {
        return templates.stream()
                .sorted(Comparator
                        .comparing((MealTemplateDto template) -> !mealType.equalsIgnoreCase(nullToEmpty(template.getMealType())))
                        .thenComparing(MealTemplateDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private String suggestedMealType(LocalTime time) {
        int hour = time.getHour();
        if (hour >= 5 && hour < 11) {
            return "BREAKFAST";
        }
        if (hour >= 11 && hour < 16) {
            return "LUNCH";
        }
        if (hour >= 16 && hour < 22) {
            return "DINNER";
        }
        return "SNACK";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
