package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FailedBarcodeScanEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FailedBarcodeScanRepository extends JpaRepository<FailedBarcodeScanEntity, Long> {

    Optional<FailedBarcodeScanEntity> findByBarcodeAndMarketRegionAndUser(String barcode, MarketRegion marketRegion, UserEntity user);
}
