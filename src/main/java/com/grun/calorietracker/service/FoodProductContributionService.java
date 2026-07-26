package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductContributionDto;
import com.grun.calorietracker.dto.FoodProductContributionPageDto;
import com.grun.calorietracker.dto.FoodProductContributionRequestDto;
import com.grun.calorietracker.dto.FoodProductContributionReviewRequestDto;
import com.grun.calorietracker.enums.FoodProductContributionStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.service.evidence.FoodContributionEvidenceStorage.EvidenceContent;
import org.springframework.web.multipart.MultipartFile;

public interface FoodProductContributionService {
    FoodProductContributionDto submit(String userEmail, FoodProductContributionRequestDto request, MultipartFile evidenceFile);
    FoodProductContributionPageDto listMine(String userEmail, int page, int size);
    FoodProductContributionPageDto listForReview(FoodProductContributionStatus status, MarketRegion marketRegion, int page, int size);
    FoodProductContributionDto review(Long contributionId, String adminEmail, FoodProductContributionReviewRequestDto request);
    EvidenceContent loadEvidenceForUser(Long contributionId, String userEmail);
    EvidenceContent loadEvidenceForAdmin(Long contributionId);
    byte[] exportApprovedTrEvidenceLedger();
}