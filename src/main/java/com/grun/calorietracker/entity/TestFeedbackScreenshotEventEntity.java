package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_feedback_screenshot_events")
@Getter @Setter
public class TestFeedbackScreenshotEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false) private TestFeedbackSubmissionEntity feedback;
    @Column(name = "event_type", nullable = false, length = 48) private String eventType;
    @Column(nullable = false, length = 16) private String outcome;
    @Column(name = "reported_size_bytes") private Long reportedSizeBytes;
    @Column(name = "actual_size_bytes") private Long actualSizeBytes;
    @Column(name = "content_type", length = 80) private String contentType;
    @Column(name = "error_code", length = 80) private String errorCode;
    @Column(length = 1000) private String detail;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
}
