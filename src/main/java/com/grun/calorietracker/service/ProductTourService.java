package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProductTourDto;
import com.grun.calorietracker.enums.ProductTourStatus;

public interface ProductTourService {
    ProductTourDto get(String email, String tourKey, String version);

    ProductTourDto recordDecision(
            String email, String tourKey, String version, ProductTourStatus status);
}
