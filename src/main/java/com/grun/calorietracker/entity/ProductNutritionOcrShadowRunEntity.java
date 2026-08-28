package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_nutrition_ocr_shadow_runs")
@Data
public class ProductNutritionOcrShadowRunEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "review_case_id", nullable = false) private Long reviewCaseId;
    @Column(name = "requester_user_id", nullable = false) private Long requesterUserId;
    @Column(name = "correlation_id", length = 128) private String correlationId;
    @Column(name = "parser_version", nullable = false, length = 80) private String parserVersion;
    @Column(name = "model", length = 120) private String model;
    @Column(name = "fallback_invoked", nullable = false) private Boolean fallbackInvoked;
    @Column(name = "v3_fields_json", columnDefinition = "TEXT", nullable = false) private String v3FieldsJson;
    @Column(name = "v4_fields_json", columnDefinition = "TEXT", nullable = false) private String v4FieldsJson;
    @Column(name = "gemini_fields_json", columnDefinition = "TEXT", nullable = false) private String geminiFieldsJson;
    @Column(name = "user_fields_json", columnDefinition = "TEXT", nullable = false) private String userFieldsJson;
    @Column(name = "v3_exact_match_rate", nullable = false) private Double v3ExactMatchRate;
    @Column(name = "v4_exact_match_rate", nullable = false) private Double v4ExactMatchRate;
    @Column(name = "gemini_exact_match_rate", nullable = false) private Double geminiExactMatchRate;
    @Column(name = "v3_basis_exact", nullable = false) private Boolean v3BasisExact;
    @Column(name = "v4_basis_exact", nullable = false) private Boolean v4BasisExact;
    @Column(name = "gemini_basis_exact", nullable = false) private Boolean geminiBasisExact;
    @Column(name = "latency_ms", nullable = false) private Long latencyMs;
    @Column(name = "estimated_cost_usd", nullable = false) private Double estimatedCostUsd;
    @Column(name = "reconciliation_json", columnDefinition = "TEXT", nullable = false) private String reconciliationJson;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
