package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductNutritionCorrectionImportResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueBackfillResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FoodProductReviewService {
    List<FoodProductDto> getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus);
    FoodProductReviewPageDto getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus, int page, int size);
    FoodProductReviewPageDto getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus, MarketRegion marketRegion, int page, int size);
    FoodProductReviewPageDto getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus, MarketRegion marketRegion, FoodCatalogType catalogType, int page, int size);
    FoodProductReviewPageDto getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus, MarketRegion marketRegion, FoodCatalogType catalogType, FoodDataSource dataSource, int page, int size);
    FoodProductReviewPageDto getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus, MarketRegion marketRegion, FoodCatalogType catalogType, FoodDataSource dataSource, FoodProductQualityIssue qualityIssue, int page, int size);
    default FoodProductDto updateProductReview(Long id, FoodProductReviewRequestDto request) {
        return updateProductReview(id, request, null);
    }
    FoodProductDto updateProductReview(Long id, FoodProductReviewRequestDto request, String reviewedBy);
    FoodProductReviewAuditPageDto getProductReviewAudits(Long productId, int page, int size);
    List<FoodProductQualityIssueDto> getProductQualityIssues(Long productId, boolean activeOnly);
    FoodProductDuplicateGroupPageDto getDuplicateProductGroups(int page, int size);
    default FoodProductMergeResponseDto mergeDuplicateProducts(FoodProductMergeRequestDto request) {
        return mergeDuplicateProducts(request, null);
    }
    FoodProductMergeResponseDto mergeDuplicateProducts(FoodProductMergeRequestDto request, String reviewedBy);
    FoodProductQualityIssueBackfillResultDto backfillQualityIssues(int pageSize, String triggeredBy);
    FoodProductNutritionCorrectionImportResultDto importNutritionCorrections(MultipartFile file, String reviewedBy);
}
