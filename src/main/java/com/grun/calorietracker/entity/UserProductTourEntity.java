package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.ProductTourStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_product_tours", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_product_tours_user_key_version",
        columnNames = {"user_id", "tour_key", "tour_version"}))
@Data
@NoArgsConstructor
public class UserProductTourEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "tour_key", nullable = false, length = 80)
    private String tourKey;

    @Column(name = "tour_version", nullable = false, length = 40)
    private String tourVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProductTourStatus status;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
