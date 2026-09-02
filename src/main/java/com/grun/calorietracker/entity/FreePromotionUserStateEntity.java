package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "free_promotion_user_state")
@Data
public class FreePromotionUserStateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;
    @Column(nullable = false) private Integer sessionNumber;
    @Column(length = 100) private String lastSessionId;
    private Instant lastSessionAt;
    @Column(nullable = false) private Instant updatedAt;
}
