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
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodLogsServiceImpl implements FoodLogsService {

    private final FoodLogsRepository foodLogsRepository;
    private final FoodItemRepository foodItemRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
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
        markFoodItemUsed(foodItem);
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

        return foodLogsRepository.getDailyStatsByUserAndDateBetween(user.getId(), start, end)
                .stream()
                .map(this::toDailyStatsDto)
                .collect(Collectors.toList());
    }

    private FoodLogDailyStatsDto toDailyStatsDto(Object[] row) {
        FoodLogDailyStatsDto dto = new FoodLogDailyStatsDto();
        dto.setDate(formatDate(row[0]));
        dto.setTotalCalories(toDouble(row[1]));
        dto.setTotalProtein(toDouble(row[2]));
        dto.setTotalCarbs(toDouble(row[3]));
        dto.setTotalFat(toDouble(row[4]));
        return dto;
    }

    private String formatDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate().toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return value.toString();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private void markFoodItemUsed(FoodItemEntity foodItem) {
        FoodProductQualityRules.markUsed(foodItem);
        foodItemRepository.save(foodItem);
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
