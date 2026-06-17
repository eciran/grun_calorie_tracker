package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FailedBarcodeScanEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface FailedBarcodeScanRepository extends JpaRepository<FailedBarcodeScanEntity, Long> {

    long countByUser(UserEntity user);

    List<FailedBarcodeScanEntity> findByUserOrderByLastScannedAtDesc(UserEntity user);

    Optional<FailedBarcodeScanEntity> findByBarcodeAndMarketRegionAndUser(String barcode, MarketRegion marketRegion, UserEntity user);

    long deleteByUser(UserEntity user);
}
