package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private FoodLogsRepository foodLogsRepository;

    @Mock
    private ExerciseLogRepository exerciseLogRepository;

    @Mock
    private ProgressLogRepository progressLogRepository;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dashboardService = new DashboardServiceImpl(
                userService,
                goalRepository,
                foodLogsRepository,
                exerciseLogRepository,
                progressLogRepository
        );
    }

    @Test
    void getDailySummary_whenNoLogsOrGoal_returnsZeroTotalsAndProfileWeight() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setWeight(82.0);
        LocalDate date = LocalDate.of(2026, 5, 15);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(foodLogsRepository.getSummaryTotalsByUserAndDateBetween(eq(1L), eq(start), eq(end))).thenReturn(List.of());
        when(exerciseLogRepository.getSummaryTotalsByUserAndDateBetween(eq(1L), eq(start), eq(end))).thenReturn(List.of());
        when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(progressLogRepository.findTopByUserOrderByLogDateDesc(user)).thenReturn(Optional.empty());

        DailySummaryDto result = dashboardService.getDailySummary("user@example.com", date);

        assertEquals(date, result.getSummaryDate());
        assertEquals(0, result.getTargetCalories());
        assertEquals(0.0, result.getConsumedCalories());
        assertEquals(0.0, result.getBurnedCalories());
        assertEquals(0.0, result.getRemainingCalories());
        assertEquals(0.0, result.getConsumedProtein());
        assertEquals(0.0, result.getConsumedFat());
        assertEquals(0.0, result.getConsumedCarbs());
        assertEquals(0, result.getTotalExerciseMinutes());
        assertEquals(82.0, result.getCurrentWeight());
    }
}
