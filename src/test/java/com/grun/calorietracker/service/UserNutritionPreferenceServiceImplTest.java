package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.UserNutritionPreferenceDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserNutritionPreferenceEntity;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.repository.UserNutritionPreferenceRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.UserNutritionPreferenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserNutritionPreferenceServiceImplTest {

    private UserRepository userRepository;
    private UserNutritionPreferenceRepository preferenceRepository;
    private UserNutritionPreferenceServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        preferenceRepository = mock(UserNutritionPreferenceRepository.class);
        service = new UserNutritionPreferenceServiceImpl(userRepository, preferenceRepository);
        user = new UserEntity();
        user.setId(4L);
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void get_whenProfileDoesNotExist_returnsEmptyProfile() {
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.empty());

        UserNutritionPreferenceDto result = service.get("user@example.com");

        assertTrue(result.getAllergens().isEmpty());
        assertTrue(result.getExcludedFoods().isEmpty());
        assertTrue(result.getDietaryPreferences().isEmpty());
    }

    @Test
    void update_normalizesAndDeduplicatesUserValues() {
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserNutritionPreferenceDto request = new UserNutritionPreferenceDto();
        request.setAllergens(Set.of(RecipeAllergen.MILK, RecipeAllergen.PEANUTS));
        request.setExcludedFoods(List.of("  Pork  ", "pork", "Raw\nOnion"));
        request.setDietaryPreferences(List.of("High protein", "high protein"));

        UserNutritionPreferenceDto result = service.update("user@example.com", request);

        assertEquals(Set.of(RecipeAllergen.MILK, RecipeAllergen.PEANUTS), result.getAllergens());
        assertEquals(List.of("Pork", "Raw Onion"), result.getExcludedFoods());
        assertEquals(List.of("High protein"), result.getDietaryPreferences());
        verify(preferenceRepository).save(argThat(entity -> entity.getUser() == user));
    }

    @Test
    void update_whenValueIsTooLong_rejectsBeforeSave() {
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.of(new UserNutritionPreferenceEntity()));
        UserNutritionPreferenceDto request = new UserNutritionPreferenceDto();
        request.setExcludedFoods(List.of("x".repeat(81)));

        assertThrows(IllegalArgumentException.class,
                () -> service.update("user@example.com", request));

        verify(preferenceRepository, never()).save(any());
    }
}
