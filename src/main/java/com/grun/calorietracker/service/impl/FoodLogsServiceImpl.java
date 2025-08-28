package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FoodLogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodLogsServiceImpl implements FoodLogsService {

    private final FoodLogsRepository foodLogsRepository;
    private final FoodItemRepository foodItemRepository;
    private final UserRepository userRepository;

    @Override
    public FoodLogsDto addFoodLog(FoodLogsDto dto, String email) {
        FoodItemEntity foodItem = foodItemRepository.findById(dto.getFoodItemId())
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        FoodLogsEntity entity = new FoodLogsEntity();
        entity.setUser(user);
        entity.setFoodItem(foodItem);
        entity.setPortionSize(dto.getPortionSize());
        entity.setMealType(dto.getMealType());
        entity.setLogDate(dto.getLogDate());

        FoodLogsEntity saved = foodLogsRepository.save(entity);
        return toDto(saved);
    }

    @Override
    public List<FoodLogsDto> getFoodLogs(String email, String date) {
        List<FoodLogsEntity> logs;
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        if (date != null) {
            LocalDate targetDate = LocalDate.parse(date);
            logs = foodLogsRepository.findByUserAndLogDateBetween(
                    user,
                    targetDate.atStartOfDay(),
                    targetDate.plusDays(1).atStartOfDay()
            );
        } else {
            logs = foodLogsRepository.findByUser(user);
        }
        return logs.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public FoodLogsDto getFoodLogById(Long id, String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        FoodLogsEntity entity = foodLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Food log not found"));
        return toDto(entity);
    }

    @Override
    public void deleteFoodLog(Long id, String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        FoodLogsEntity entity = foodLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Food log not found"));
        foodLogsRepository.delete(entity);
    }

    @Override
    public List<FoodLogDailyStatsDto> getDailyStats(String email, LocalDateTime start, LocalDateTime end) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        return null;
       // return foodLogsRepository.getDailyStatsByUserAndDateBetween(user, start, end);
    }

    private FoodLogsDto toDto(FoodLogsEntity entity) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setId(entity.getId());
        dto.setFoodItemId(entity.getFoodItem().getId());
        dto.setFoodName(entity.getFoodItem().getName());
        dto.setPortionSize(entity.getPortionSize());
        dto.setMealType(entity.getMealType());
        dto.setLogDate(entity.getLogDate());
        return dto;
    }
}
