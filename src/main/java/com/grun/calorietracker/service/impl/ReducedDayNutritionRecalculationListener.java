package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FastingDayRuleType;
import com.grun.calorietracker.event.FoodDiaryChangedEvent;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.AdvancedFastingExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReducedDayNutritionRecalculationListener {
    private final UserRepository userRepository;
    private final FastingProgramOccurrenceRepository occurrenceRepository;
    private final AdvancedFastingExecutionService executionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFoodDiaryChanged(FoodDiaryChangedEvent event) {
        UserEntity user = userRepository.findByEmail(event.email()).orElse(null);
        if (user == null) return;
        occurrenceRepository.findByUserAndOccurrenceDate(user, event.date())
                .filter(occurrence -> occurrence.getRuleType() == FastingDayRuleType.REDUCED_CALORIE)
                .ifPresent(ignored -> {
                    try {
                        executionService.recalculate(event.email(), event.date());
                    } catch (RuntimeException ex) {
                        log.warn("Reduced-day nutrition recalculation failed userId={} date={}", user.getId(), event.date(), ex);
                    }
                });
    }
}