package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "runtime_operation_records")
public class RuntimeOperationRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuntimeOperationRecordType recordType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuntimeOperationStatus status;
    @Column(nullable = false, length = 120)
    private String operationKey;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 1000)
    private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime nextRunAt;
    @Column(nullable = false)
    private Boolean retryable;
    @Column(nullable = false)
    private Integer retryCount;
    private Long parentRecordId;
    @Column(nullable = false, length = 255)
    private String createdBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
