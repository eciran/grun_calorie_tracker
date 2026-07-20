package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.UserNutritionPreferenceDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserNutritionPreferenceEntity;
import com.grun.calorietracker.repository.UserNutritionPreferenceRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.UserNutritionPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserNutritionPreferenceServiceImpl
        implements UserNutritionPreferenceService {

    private final UserRepository userRepository;
    private final UserNutritionPreferenceRepository preferenceRepository;

    @Override
    @Transactional(readOnly = true)
    public UserNutritionPreferenceDto get(String email) {
        UserEntity user = user(email);
        return preferenceRepository.findByUser(user)
                .map(this::toDto)
                .orElseGet(UserNutritionPreferenceDto::new);
    }

    @Override
    @Transactional
    public UserNutritionPreferenceDto update(
            String email, UserNutritionPreferenceDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Nutrition preferences are required.");
        }
        UserEntity user = user(email);
        UserNutritionPreferenceEntity entity = preferenceRepository.findByUser(user)
                .orElseGet(() -> {
                    UserNutritionPreferenceEntity created =
                            new UserNutritionPreferenceEntity();
                    created.setUser(user);
                    return created;
                });

        entity.setAllergens(request.getAllergens() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(request.getAllergens()));
        entity.setExcludedFoods(cleanList(request.getExcludedFoods(), 30));
        entity.setDietaryPreferences(cleanList(
                request.getDietaryPreferences(), 20));
        return toDto(preferenceRepository.save(entity));
    }

    private UserEntity user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Authenticated user was not found."));
    }

    private List<String> cleanList(List<String> values, int maxItems) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String cleaned = value.trim()
                    .replaceAll("[\\p{Cntrl}]", " ")
                    .replaceAll("\\s+", " ");
            if (cleaned.isBlank()) {
                continue;
            }
            if (cleaned.length() > 80) {
                throw new IllegalArgumentException(
                        "Nutrition preference values cannot exceed 80 characters.");
            }
            unique.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
            if (unique.size() > maxItems) {
                throw new IllegalArgumentException(
                        "Too many nutrition preference values.");
            }
        }
        return new ArrayList<>(unique.values());
    }

    private UserNutritionPreferenceDto toDto(
            UserNutritionPreferenceEntity entity) {
        UserNutritionPreferenceDto dto = new UserNutritionPreferenceDto();
        dto.setAllergens(new LinkedHashSet<>(entity.getAllergens()));
        dto.setExcludedFoods(new ArrayList<>(entity.getExcludedFoods()));
        dto.setDietaryPreferences(
                new ArrayList<>(entity.getDietaryPreferences()));
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
