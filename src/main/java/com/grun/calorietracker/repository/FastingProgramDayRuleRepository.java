package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingProgramDayRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.DayOfWeek;
import java.util.*;

public interface FastingProgramDayRuleRepository extends JpaRepository<FastingProgramDayRuleEntity, Long> {
    List<FastingProgramDayRuleEntity> findAllByProgramVersionIdOrderByDayOfWeek(Long versionId);
    Optional<FastingProgramDayRuleEntity> findByProgramVersionIdAndDayOfWeek(Long versionId, DayOfWeek dayOfWeek);
}
