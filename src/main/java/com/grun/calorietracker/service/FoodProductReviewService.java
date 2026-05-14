package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;

import java.util.List;

public interface FoodProductReviewService {
    List<FoodProductDto> getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus);
    FoodProductReviewPageDto getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus, int page, int size);
    FoodProductDto updateProductReview(Long id, FoodProductReviewRequestDto request);
    FoodProductDuplicateGroupPageDto getDuplicateProductGroups(int page, int size);
    FoodProductMergeResponseDto mergeDuplicateProducts(FoodProductMergeRequestDto request);
}
