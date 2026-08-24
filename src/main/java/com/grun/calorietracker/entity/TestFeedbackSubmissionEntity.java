package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.TestFeedbackPlatform;
import com.grun.calorietracker.enums.TestFeedbackStatus;
import com.grun.calorietracker.enums.TestFeedbackType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_feedback_submissions", uniqueConstraints =
        @UniqueConstraint(name = "uk_test_feedback_user_idempotency", columnNames = {"user_id", "idempotency_key"}))
@Getter
@Setter
public class TestFeedbackSubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 24)
    private TestFeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TestFeedbackStatus status = TestFeedbackStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TestFeedbackPlatform platform;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Column(nullable = false, length = 240)
    private String route;
    @Column(name = "previous_route", length = 240)
    private String previousRoute;
    @Column(length = 2000)
    private String description;
    @Column(name = "app_version", length = 40)
    private String appVersion;
    @Column(name = "build_number", length = 40)
    private String buildNumber;
    @Column(name = "eas_build_id", length = 100)
    private String easBuildId;
    @Column(name = "commit_sha", length = 64)
    private String commitSha;
    @Column(name = "os_version", length = 80)
    private String osVersion;
    @Column(name = "device_model", length = 120)
    private String deviceModel;
    @Column(name = "language_tag", length = 20)
    private String languageTag;
    @Column(name = "market_region", length = 24)
    private String marketRegion;
    @Column(name = "last_http_status")
    private Integer lastHttpStatus;
    @Column(name = "last_http_duration_ms")
    private Long lastHttpDurationMs;
    @Column(name = "last_correlation_id", length = 100)
    private String lastCorrelationId;
    @Column(name = "network_state", length = 32)
    private String networkState;
    @Column(name = "admin_note", length = 2000)
    private String adminNote;
    @Column(name = "screenshot_storage_key", length = 500)
    private String screenshotStorageKey;
    @Column(name = "screenshot_content_type", length = 80)
    private String screenshotContentType;
    @Column(name = "screenshot_size_bytes")
    private Long screenshotSizeBytes;
    @Column(name = "screenshot_sha256", length = 64)
    private String screenshotSha256;
    @Column(name = "screenshot_attached_at")
    private LocalDateTime screenshotAttachedAt;
    @Column(name = "screenshot_expires_at")
    private LocalDateTime screenshotExpiresAt;
    @Column(name = "screenshot_deleted_at")
    private LocalDateTime screenshotDeletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private UserEntity reviewedBy;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
