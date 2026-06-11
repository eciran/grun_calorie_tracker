package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UnitPreference;
import com.grun.calorietracker.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    private String password;

    private Integer age;

    private String gender;

    private Double height;

    private Double weight;

    private Double bodyFatPercentage;

    private Double bmi;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    private MarketRegion marketRegion;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false)
    private PreferredLanguage preferredLanguage = PreferredLanguage.EN;

    @Column(name = "time_zone", nullable = false)
    private String timeZone = "Europe/Dublin";

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_preference", nullable = false)
    private UnitPreference unitPreference = UnitPreference.METRIC;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = true;

    @Column(name = "password_set", nullable = false)
    private Boolean passwordSet = true;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "account_enabled", nullable = false)
    private Boolean accountEnabled = true;

    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "login_locked_until")
    private LocalDateTime loginLockedUntil;

    @Column(name = "last_failed_login_at")
    private LocalDateTime lastFailedLoginAt;

    @Column(name = "push_notifications_enabled", nullable = false)
    private Boolean pushNotificationsEnabled = true;

    @Column(name = "meal_reminders_enabled", nullable = false)
    private Boolean mealRemindersEnabled = true;

    @Column(name = "hydration_reminders_enabled", nullable = false)
    private Boolean hydrationRemindersEnabled = true;

}
