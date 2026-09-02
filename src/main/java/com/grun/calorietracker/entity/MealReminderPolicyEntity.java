package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MealReminderPolicyStatus;
import com.grun.calorietracker.service.reminder.MealReminderContract;
import jakarta.persistence.*;
import lombok.Data;
import java.time.*;

@Entity @Table(name="meal_reminder_policies") @Data
public class MealReminderPolicyEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version @Column(nullable=false) private Long version=0L;
 @Column(name="policy_version",nullable=false,unique=true,length=80) private String policyVersion;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private MealReminderPolicyStatus status;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private MealReminderContract.Mode mode;
 @Column(name="kcal_enabled",nullable=false) private boolean kcalEnabled;
 @Column(name="breakfast_time",nullable=false) private LocalTime breakfastTime;
 @Column(name="lunch_time",nullable=false) private LocalTime lunchTime;
 @Column(name="dinner_time",nullable=false) private LocalTime dinnerTime;
 @Column(name="slot_age_minutes",nullable=false) private int slotAgeMinutes;
 @Column(name="max_daily",nullable=false) private int maxDaily;
 @Column(name="max_rolling",nullable=false) private int maxRolling;
 @Column(name="max_catchups",nullable=false) private int maxCatchups;
 @Column(name="minimum_gap_minutes",nullable=false) private int minimumGapMinutes;
 @Column(name="routine_gap_minutes",nullable=false) private int routineGapMinutes;
 @Column(name="quiet_start",nullable=false) private LocalTime quietStart;
 @Column(name="quiet_end",nullable=false) private LocalTime quietEnd;
 @Column(name="pilot_user_ids",nullable=false,length=4000) private String pilotUserIds="";
 @Column(name="emergency_stopped",nullable=false) private boolean emergencyStopped;
 @Column(name="stop_reason",length=500) private String stopReason;
 @Column(name="created_by",nullable=false,length=255) private String createdBy;
 @Column(name="updated_by",nullable=false,length=255) private String updatedBy;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Column(name="published_at") private Instant publishedAt;
}
