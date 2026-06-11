package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_barcode_scans")
@Data
public class FailedBarcodeScanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MarketRegion marketRegion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false)
    private Long scanCount;

    @Column(nullable = false)
    private LocalDateTime firstScannedAt;

    @Column(nullable = false)
    private LocalDateTime lastScannedAt;
}
