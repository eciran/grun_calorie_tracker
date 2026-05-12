package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.FoodProductReviewService;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FoodProductReviewServiceImpl implements FoodProductReviewService {

    private final FoodItemRepository foodItemRepository;

    public FoodProductReviewServiceImpl(FoodItemRepository foodItemRepository) {
        this.foodItemRepository = foodItemRepository;
    }

    @Override
    public List<FoodProductDto> getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus) {
        return getProductsForReview(verificationStatus, imageStatus, 0, 100).getContent();
    }

    @Override
    public FoodProductReviewPageDto getProductsForReview(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size), buildReviewSort());
        Page<FoodItemEntity> products = foodItemRepository.findAll(
                buildReviewSpecification(verificationStatus, imageStatus),
                pageable
        );
        return toPageDto(products);
    }

    @Override
    public FoodProductDto updateProductReview(Long id, FoodProductReviewRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Review request must not be empty.");
        }

        FoodItemEntity product = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food product not found with id: " + id));

        String productName = trimToNull(request.getProductName());
        if (productName != null) {
            product.setName(productName);
        }

        String displayImageUrl = trimToNull(request.getDisplayImageUrl());
        if (displayImageUrl != null) {
            product.setDisplayImageUrl(displayImageUrl);
        }

        if (request.getVerificationStatus() != null) {
            product.setVerificationStatus(request.getVerificationStatus());
        }

        if (request.getImageSource() != null) {
            product.setImageSource(request.getImageSource());
        }

        if (request.getImageStatus() != null) {
            product.setImageStatus(request.getImageStatus());
        }

        FoodProductQualityRules.markReviewed(product);

        return FoodItemMapper.mapEntityToDto(foodItemRepository.save(product));
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private Specification<FoodItemEntity> buildReviewSpecification(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus
    ) {
        VerificationStatus effectiveVerificationStatus = verificationStatus == null
                ? VerificationStatus.RAW_IMPORTED
                : verificationStatus;
        ImageStatus effectiveImageStatus = imageStatus == null ? ImageStatus.NEEDS_REVIEW : imageStatus;

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("verificationStatus"), effectiveVerificationStatus));
            predicates.add(criteriaBuilder.equal(root.get("imageStatus"), effectiveImageStatus));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildReviewSort() {
        return Sort.by(
                Sort.Order.desc("reviewPriority").nullsLast(),
                Sort.Order.desc("usageCount").nullsLast(),
                Sort.Order.asc("id")
        );
    }

    private int normalizePageSize(int size) {
        if (size < 1) {
            return 25;
        }
        return Math.min(size, 100);
    }

    private FoodProductReviewPageDto toPageDto(Page<FoodItemEntity> products) {
        FoodProductReviewPageDto dto = new FoodProductReviewPageDto();
        dto.setContent(FoodItemMapper.mapEntityListToDtoList(products.getContent()));
        dto.setPage(products.getNumber());
        dto.setSize(products.getSize());
        dto.setTotalElements(products.getTotalElements());
        dto.setTotalPages(products.getTotalPages());
        dto.setFirst(products.isFirst());
        dto.setLast(products.isLast());
        return dto;
    }
}
