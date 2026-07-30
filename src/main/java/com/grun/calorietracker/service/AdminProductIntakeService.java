package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminProductIntakePageDto;
import com.grun.calorietracker.dto.AdminProductIntakeAssignmentDto;
import com.grun.calorietracker.dto.AdminProductIntakeActionDto;
import com.grun.calorietracker.dto.AdminProductIntakeManualRequestDto;
import com.grun.calorietracker.dto.AdminProductIntakeDetailDto;
import com.grun.calorietracker.enums.AdminProductIntakeQueue;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ProductIntakeApplyField;
import java.util.Set;

public interface AdminProductIntakeService {
    AdminProductIntakePageDto list(String adminEmail, AdminProductIntakeQueue queue,
                                   FoodProductReviewCaseStatus status, MarketRegion marketRegion,
                                   int page, int size);
    AdminProductIntakeAssignmentDto claim(Long caseId, String actorEmail);
    AdminProductIntakeAssignmentDto release(Long caseId, String actorEmail);
    AdminProductIntakeAssignmentDto reassign(Long caseId, String actorEmail, String targetAdminEmail);
    AdminProductIntakeActionDto requestBetterEvidence(Long caseId, String actorEmail, String note);
    AdminProductIntakeActionDto decideEvidence(Long caseId, String actorEmail, boolean approved, String note);
    AdminProductIntakeActionDto attachExistingProduct(Long caseId, String actorEmail, Long foodItemId);
    AdminProductIntakeActionDto applyExistingProduct(Long caseId, String actorEmail, Set<ProductIntakeApplyField> fields, boolean confirmed);
    AdminProductIntakeActionDto publishCandidate(Long caseId, String actorEmail, String reason, String correlationId, boolean confirmed);
    AdminProductIntakeActionDto createManual(String actorEmail, AdminProductIntakeManualRequestDto request);
    AdminProductIntakeDetailDto detail(Long caseId);
}