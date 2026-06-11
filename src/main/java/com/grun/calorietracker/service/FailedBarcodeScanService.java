package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.MarketRegion;

public interface FailedBarcodeScanService {

    void recordFailedScan(String barcode, MarketRegion marketRegion, String email);
}
