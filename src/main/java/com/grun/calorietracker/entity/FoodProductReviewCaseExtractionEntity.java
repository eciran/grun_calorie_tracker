package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "food_product_review_case_extractions")
@Data
@NoArgsConstructor
public class FoodProductReviewCaseExtractionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_case_id", nullable = false, unique = true)
    private FoodProductReviewCaseEntity reviewCase;
    @Column(nullable = false, length = 80)
    private String engine;
    @Column(name = "engine_version", length = 40)
    private String engineVersion;
    @Column(name = "parser_version", nullable = false, length = 40)
    private String parserVersion;
    @Column(length = 20)
    private String locale;
    @Column(name = "recognized_lines_json", nullable = false, columnDefinition = "TEXT")
    private String recognizedLinesJson;
    @Column(name = "parsed_values_json", nullable = false, columnDefinition = "TEXT")
    private String parsedValuesJson;
    @Column(name = "parser_warnings_json", columnDefinition = "TEXT")
    private String parserWarningsJson;
    @Column(name = "raw_payload_expires_at")
    private LocalDateTime rawPayloadExpiresAt;
    @Column(name = "raw_payload_deleted_at")
    private LocalDateTime rawPayloadDeletedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
