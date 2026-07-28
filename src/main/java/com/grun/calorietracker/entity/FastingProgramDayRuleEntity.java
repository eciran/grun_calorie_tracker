package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FastingDayRuleType;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name = "fasting_program_day_rules", uniqueConstraints = @UniqueConstraint(name = "uk_fasting_program_version_weekday", columnNames = {"program_version_id","day_of_week"}))
@Data @NoArgsConstructor @AllArgsConstructor
public class FastingProgramDayRuleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "program_version_id", nullable = false) private FastingProgramVersionEntity programVersion;
    @Enumerated(EnumType.STRING) @Column(name = "day_of_week", nullable = false, updatable = false, length = 12) private DayOfWeek dayOfWeek;
    @Enumerated(EnumType.STRING) @Column(name = "rule_type", nullable = false, updatable = false, length = 24) private FastingDayRuleType ruleType;
    @Column(name = "fasting_minutes", updatable = false) private Integer fastingMinutes;
    @Column(name = "preferred_start_time", updatable = false) private LocalTime preferredStartTime;
    @Column(name = "reduced_calorie_target", updatable = false) private Integer reducedCalorieTarget;
}
