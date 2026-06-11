package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.FailedBarcodeScanEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.repository.FailedBarcodeScanRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FailedBarcodeScanService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FailedBarcodeScanServiceImpl implements FailedBarcodeScanService {

    private final FailedBarcodeScanRepository failedBarcodeScanRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void recordFailedScan(String barcode, MarketRegion marketRegion, String email) {
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(barcode);
        if (normalizedBarcode == null) {
            return;
        }
        UserEntity user = email == null ? null : userRepository.findByEmail(email).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        FailedBarcodeScanEntity entity = failedBarcodeScanRepository
                .findByBarcodeAndMarketRegionAndUser(normalizedBarcode, marketRegion, user)
                .orElseGet(() -> {
                    FailedBarcodeScanEntity created = new FailedBarcodeScanEntity();
                    created.setBarcode(normalizedBarcode);
                    created.setMarketRegion(marketRegion);
                    created.setUser(user);
                    created.setScanCount(0L);
                    created.setFirstScannedAt(now);
                    return created;
                });
        entity.setScanCount((entity.getScanCount() == null ? 0L : entity.getScanCount()) + 1L);
        entity.setLastScannedAt(now);
        failedBarcodeScanRepository.save(entity);
    }
}
