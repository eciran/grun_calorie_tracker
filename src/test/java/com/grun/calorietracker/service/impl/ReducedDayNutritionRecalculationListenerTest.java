package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.FastingDayRuleType;
import com.grun.calorietracker.event.FoodDiaryChangedEvent;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.AdvancedFastingExecutionService;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.Optional;
import static org.mockito.Mockito.*;

class ReducedDayNutritionRecalculationListenerTest {
    UserRepository users=mock(UserRepository.class);
    FastingProgramOccurrenceRepository occurrences=mock(FastingProgramOccurrenceRepository.class);
    AdvancedFastingExecutionService execution=mock(AdvancedFastingExecutionService.class);
    ReducedDayNutritionRecalculationListener listener=new ReducedDayNutritionRecalculationListener(users,occurrences,execution);
    UserEntity user; LocalDate date;

    @BeforeEach void setup(){ reset(users,occurrences,execution); user=new UserEntity(); user.setId(1L); user.setEmail("user@grun.app"); date=LocalDate.of(2026,7,20); when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user)); }

    @Test void recalculatesMaterializedReducedDayAfterDiaryCommit(){ FastingProgramOccurrenceEntity occurrence=new FastingProgramOccurrenceEntity(); occurrence.setRuleType(FastingDayRuleType.REDUCED_CALORIE); when(occurrences.findByUserAndOccurrenceDate(user,date)).thenReturn(Optional.of(occurrence)); listener.onFoodDiaryChanged(new FoodDiaryChangedEvent(user.getEmail(),date)); verify(execution).recalculate(user.getEmail(),date); }

    @Test void ignoresNormalDayAndDoesNotMaterializeOccurrence(){ FastingProgramOccurrenceEntity occurrence=new FastingProgramOccurrenceEntity(); occurrence.setRuleType(FastingDayRuleType.NORMAL); when(occurrences.findByUserAndOccurrenceDate(user,date)).thenReturn(Optional.of(occurrence)); listener.onFoodDiaryChanged(new FoodDiaryChangedEvent(user.getEmail(),date)); verifyNoInteractions(execution); }
}