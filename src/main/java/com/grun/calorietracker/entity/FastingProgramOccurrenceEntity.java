package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name="fasting_program_occurrences", uniqueConstraints=@UniqueConstraint(name="uk_fasting_occurrence_user_date", columnNames={"user_id","occurrence_date"}))
@Data @NoArgsConstructor @AllArgsConstructor
public class FastingProgramOccurrenceEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private UserEntity user;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="program_id",nullable=false) private FastingProgramEntity program;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="program_version_id",nullable=false) private FastingProgramVersionEntity programVersion;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="day_rule_id",nullable=false) private FastingProgramDayRuleEntity dayRule;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="fasting_session_id") private FastingSessionEntity fastingSession;
 @Column(name="occurrence_date",nullable=false) private LocalDate occurrenceDate;
 @Enumerated(EnumType.STRING) @Column(name="rule_type",nullable=false,length=24) private FastingDayRuleType ruleType;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private FastingOccurrenceStatus status;
 @Enumerated(EnumType.STRING) @Column(name="adherence_status",nullable=false,length=24) private FastingAdherenceStatus adherenceStatus;
 private LocalDateTime plannedStartAt; private LocalDateTime plannedEndAt; private Integer plannedFastingMinutes;
 private Integer plannedCalorieTarget; private Double actualCalories; private LocalDateTime evaluatedAt;
 @Enumerated(EnumType.STRING) @Column(length=32) private FastingSkipReason skipReason;
 @Column(length=500) private String reasonNote;
 @Column(nullable=false,updatable=false) private LocalDateTime createdAt; @Column(nullable=false) private LocalDateTime updatedAt;
 @PrePersist void create(){ var now=LocalDateTime.now(); createdAt=now; updatedAt=now; }
 @PreUpdate void update(){ updatedAt=LocalDateTime.now(); }
}
