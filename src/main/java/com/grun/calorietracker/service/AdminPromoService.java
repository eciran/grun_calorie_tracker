package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.*;

public interface AdminPromoService {
    AdminPromoPageDto list(String search, PromoStatus status, PromoType type, PromoStore store, int page, int size);
    AdminPromoDto get(Long id);
    AdminPromoDto create(AdminPromoRequestDto request, String adminEmail, String correlationId);
    AdminPromoDto update(Long id, AdminPromoRequestDto request, String adminEmail, String correlationId);
    AdminPromoPreviewDto preview(Long id);
    AdminPromoDto activate(Long id, String adminEmail, String correlationId);
    AdminPromoDto deactivate(Long id, String reason, String adminEmail, String correlationId);
    AdminPromoReconciliationDto reconcile(Long id, String adminEmail, String correlationId);
    AdminPromoRedemptionDto recordRedemption(Long id, AdminPromoRedemptionRequestDto request,
                                             String adminEmail, String correlationId);
    AdminPromoMetricsDto metrics(Long promoId);
    AdminPromotionOperationsAnalyticsDto analytics(int windowDays);
    AdminPromoRedemptionPageDto redemptions(Long promoId, PromoRedemptionStatus status, int page, int size);
}
