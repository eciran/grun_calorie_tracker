package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ProductionVerificationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "production_verification_runs")
@Data
@NoArgsConstructor
public class ProductionVerificationRunEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 32) private String provider;
    @Column(nullable = false, length = 24) private String environment;
    @Column(nullable = false, length = 64) private String scenario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ProductionVerificationStatus status;
    @Column(name = "evidence_reference", nullable = false, length = 240) private String evidenceReference;
    @Column(nullable = false, length = 500) private String summary;
    @Column(name = "executed_by", nullable = false, length = 320) private String executedBy;
    @Column(name = "executed_at", nullable = false) private Instant executedAt;
    @Column(name = "valid_until") private Instant validUntil;
}
