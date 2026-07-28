package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "fasting_program_versions", uniqueConstraints = @UniqueConstraint(name = "uk_fasting_program_version", columnNames = {"program_id","version_number"}))
@Data @NoArgsConstructor @AllArgsConstructor
public class FastingProgramVersionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "program_id", nullable = false) private FastingProgramEntity program;
    @Column(name = "version_number", nullable = false, updatable = false) private Integer versionNumber;
    @Column(name = "safety_policy_version", nullable = false, updatable = false, length = 50) private String safetyPolicyVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void create(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
