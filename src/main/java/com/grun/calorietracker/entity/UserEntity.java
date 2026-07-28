package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.CountryCode;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UnitPreference;
import com.grun.calorietracker.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
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

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String gender;

    private Double height;

    private Double weight;

    private Double bodyFatPercentage;

    private Double bmi;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Column(name = "admin_mfa_enabled", nullable = false)
    private Boolean adminMfaEnabled = false;

    @Column(name = "admin_role_updated_at")
    private Instant adminRoleUpdatedAt;

    @Column(name = "admin_mfa_secret_encrypted", columnDefinition = "TEXT")
    private String adminMfaSecretEncrypted;

    @Column(name = "admin_mfa_enrollment_started_at")
    private Instant adminMfaEnrollmentStartedAt;

    @Column(name = "admin_mfa_verified_at")
    private Instant adminMfaVerifiedAt;

    @Enumerated(EnumType.STRING)
    private MarketRegion marketRegion;

    @Enumerated(EnumType.STRING)
    @Column(name = "country_code", length = 2)
    private CountryCode countryCode;

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

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

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

    @Column(name = "step_reminders_enabled", nullable = false)
    private Boolean stepRemindersEnabled = true;

    @Column(name = "fasting_reminders_enabled", nullable = false)
    private Boolean fastingRemindersEnabled = true;

    @Column(name = "notification_quiet_hours_start")
    private java.time.LocalTime notificationQuietHoursStart;

    @Column(name = "notification_quiet_hours_end")
    private java.time.LocalTime notificationQuietHoursEnd;

    @Column(name = "recipe_suggestions_enabled", nullable = false)
    private Boolean recipeSuggestionsEnabled = true;

    @Column(name = "ai_insights_enabled", nullable = false)
    private Boolean aiInsightsEnabled = true;

    @Column(name = "weekly_reports_enabled", nullable = false)
    private Boolean weeklyReportsEnabled = true;

    @Column(name = "marketing_notifications_enabled", nullable = false)
    private Boolean marketingNotificationsEnabled = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "recent_products_cleared_at")
    private LocalDateTime recentProductsClearedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (Boolean.TRUE.equals(emailVerified) && emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

}
