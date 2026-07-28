package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FastingProgramStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name = "fasting_programs")
@Data @NoArgsConstructor @AllArgsConstructor
public class FastingProgramEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserEntity user;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FastingProgramStatus status = FastingProgramStatus.DRAFT;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_until") private LocalDate effectiveUntil;
    @Column(name = "current_version_number", nullable = false) private Integer currentVersionNumber = 1;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create(){ var now=LocalDateTime.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void update(){ updatedAt=LocalDateTime.now(); }
}
