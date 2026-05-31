package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.RetentionPolicyKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "retention_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_key", nullable = false, unique = true, length = 80)
    private RetentionPolicyKey policyKey;

    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;

    @Column(name = "legal_basis", nullable = false, length = 255)
    private String legalBasis;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
