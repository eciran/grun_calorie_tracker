package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.PromoProviderRedemptionCommand;

public interface PromoProviderRedemptionService {

    void recordVerifiedPurchase(PromoProviderRedemptionCommand command);
}
