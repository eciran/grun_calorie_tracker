package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;

import java.util.List;

public interface FoodProductReviewService {
    List<FoodProductDto> getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus);
    FoodProductReviewPageDto getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus, int page, int size);
    default FoodProductDto updateProductReview(Long id, FoodProductReviewRequestDto request) {
        return updateProductReview(id, request, null);
    }
    FoodProductDto updateProductReview(Long id, FoodProductReviewRequestDto request, String reviewedBy);
    FoodProductReviewAuditPageDto getProductReviewAudits(Long productId, int page, int size);
    FoodProductDuplicateGroupPageDto getDuplicateProductGroups(int page, int size);
    default FoodProductMergeResponseDto mergeDuplicateProducts(FoodProductMergeRequestDto request) {
        return mergeDuplicateProducts(request, null);
    }
    FoodProductMergeResponseDto mergeDuplicateProducts(FoodProductMergeRequestDto request, String reviewedBy);
}
