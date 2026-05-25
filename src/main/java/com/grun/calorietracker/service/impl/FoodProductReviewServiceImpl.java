package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.service.FoodProductReviewService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FoodProductReviewServiceImpl implements FoodProductReviewService {

    private final FoodItemRepository foodItemRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final FoodProductReviewAuditRepository foodProductReviewAuditRepository;

    public FoodProductReviewServiceImpl(
            FoodItemRepository foodItemRepository,
            FoodLogsRepository foodLogsRepository,
            UserFavoriteRepository userFavoriteRepository,
            FoodProductReviewAuditRepository foodProductReviewAuditRepository
    ) {
        this.foodItemRepository = foodItemRepository;
        this.foodLogsRepository = foodLogsRepository;
        this.userFavoriteRepository = userFavoriteRepository;
        this.foodProductReviewAuditRepository = foodProductReviewAuditRepository;
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
    @Transactional
    public FoodProductDto updateProductReview(Long id, FoodProductReviewRequestDto request, String reviewedBy) {
        if (request == null) {
            throw new IllegalArgumentException("Review request must not be empty.");
        }

        FoodItemEntity product = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food product not found with id: " + id));

        List<FoodProductReviewAuditEntity> audits = new ArrayList<>();
        String reviewNote = trimToNull(request.getReviewNote());
        validateRejectionNote(request, reviewNote);

        String productName = trimToNull(request.getProductName());
        if (productName != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.REVIEW_UPDATE,
                    "productName",
                    product.getName(),
                    productName,
                    reviewNote
            );
            product.setName(productName);
        }

        String displayImageUrl = trimToNull(request.getDisplayImageUrl());
        if (displayImageUrl != null) {
            validateDisplayImageUrl(displayImageUrl);
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.IMAGE_CHANGE,
                    "displayImageUrl",
                    product.getDisplayImageUrl(),
                    displayImageUrl,
                    reviewNote
            );
            product.setDisplayImageUrl(displayImageUrl);
        }

        if (request.getVerificationStatus() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.STATUS_CHANGE,
                    "verificationStatus",
                    product.getVerificationStatus(),
                    request.getVerificationStatus(),
                    reviewNote
            );
            product.setVerificationStatus(request.getVerificationStatus());
        }

        if (request.getImageSource() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.IMAGE_CHANGE,
                    "imageSource",
                    product.getImageSource(),
                    request.getImageSource(),
                    reviewNote
            );
            product.setImageSource(request.getImageSource());
        }

        if (request.getImageStatus() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.IMAGE_CHANGE,
                    "imageStatus",
                    product.getImageStatus(),
                    request.getImageStatus(),
                    reviewNote
            );
            product.setImageStatus(request.getImageStatus());
        }

        validateReviewState(product);
        product.setReviewedBy(trimToNull(reviewedBy) == null ? "unknown" : reviewedBy.trim());
        FoodProductQualityRules.markReviewed(product);

        FoodItemEntity savedProduct = foodItemRepository.save(product);
        if (!audits.isEmpty()) {
            foodProductReviewAuditRepository.saveAll(audits);
        }

        return FoodItemMapper.mapEntityToDto(savedProduct);
    }

    @Override
    public FoodProductReviewAuditPageDto getProductReviewAudits(Long productId, int page, int size) {
        if (!foodItemRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Food product not found with id: " + productId);
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<FoodProductReviewAuditEntity> audits =
                foodProductReviewAuditRepository.findByFoodItemId(productId, pageable);

        FoodProductReviewAuditPageDto dto = new FoodProductReviewAuditPageDto();
        dto.setContent(audits.getContent().stream().map(this::toAuditDto).toList());
        dto.setPage(audits.getNumber());
        dto.setSize(audits.getSize());
        dto.setTotalElements(audits.getTotalElements());
        dto.setTotalPages(audits.getTotalPages());
        dto.setFirst(audits.isFirst());
        dto.setLast(audits.isLast());
        return dto;
    }

    @Override
    public FoodProductDuplicateGroupPageDto getDuplicateProductGroups(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        Page<String> duplicateBarcodes = foodItemRepository.findDuplicateNormalizedBarcodes(pageable);
        List<FoodProductDuplicateGroupDto> groups = buildDuplicateGroups(duplicateBarcodes.getContent());

        FoodProductDuplicateGroupPageDto dto = new FoodProductDuplicateGroupPageDto();
        dto.setContent(groups);
        dto.setPage(duplicateBarcodes.getNumber());
        dto.setSize(duplicateBarcodes.getSize());
        dto.setTotalElements(duplicateBarcodes.getTotalElements());
        dto.setTotalPages(duplicateBarcodes.getTotalPages());
        dto.setFirst(duplicateBarcodes.isFirst());
        dto.setLast(duplicateBarcodes.isLast());
        return dto;
    }

    @Override
    @Transactional
    public FoodProductMergeResponseDto mergeDuplicateProducts(FoodProductMergeRequestDto request, String reviewedBy) {
        validateMergeRequest(request);

        FoodItemEntity targetProduct = foodItemRepository.findById(request.getTargetProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target food product not found with id: " + request.getTargetProductId()
                ));
        List<Long> duplicateProductIds = distinctDuplicateProductIds(request);
        if (duplicateProductIds.isEmpty()) {
            throw new IllegalArgumentException("Duplicate product ids must contain at least one valid id.");
        }
        List<FoodItemEntity> duplicateProducts = foodItemRepository.findAllById(duplicateProductIds);
        validateAllDuplicateProductsFound(duplicateProductIds, duplicateProducts);
        validateSameNormalizedBarcode(targetProduct, duplicateProducts);

        int removedFavoriteCount = userFavoriteRepository.deleteConflictingFavoritesBeforeMerge(
                targetProduct.getId(),
                duplicateProductIds
        );
        int reassignedFavoriteCount = userFavoriteRepository.reassignFoodItemReferences(
                targetProduct,
                duplicateProductIds
        );
        int reassignedFoodLogCount = foodLogsRepository.reassignFoodItemReferences(
                targetProduct,
                duplicateProductIds
        );

        targetProduct.setUsageCount(calculateMergedUsageCount(targetProduct, duplicateProducts));
        FoodProductQualityRules.updateQualityAndReviewPriority(targetProduct);
        FoodItemEntity savedTargetProduct = foodItemRepository.save(targetProduct);
        foodProductReviewAuditRepository.save(buildMergeAudit(
                savedTargetProduct,
                duplicateProductIds,
                reviewedBy,
                reassignedFoodLogCount,
                reassignedFavoriteCount,
                removedFavoriteCount
        ));

        foodItemRepository.deleteAll(duplicateProducts);

        return new FoodProductMergeResponseDto(
                FoodItemMapper.mapEntityToDto(savedTargetProduct),
                duplicateProductIds,
                reassignedFoodLogCount,
                reassignedFavoriteCount,
                removedFavoriteCount
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void validateReviewState(FoodItemEntity product) {
        if (product.getVerificationStatus() == VerificationStatus.VERIFIED && trimToNull(product.getName()) == null) {
            throw new IllegalArgumentException("Verified product must have a product name.");
        }

        if (product.getImageStatus() == ImageStatus.APPROVED && trimToNull(product.getDisplayImageUrl()) == null) {
            throw new IllegalArgumentException("Approved product image must have a display image URL.");
        }
    }

    private void validateRejectionNote(FoodProductReviewRequestDto request, String reviewNote) {
        boolean rejectsProduct = request.getVerificationStatus() == VerificationStatus.REJECTED;
        boolean rejectsImage = request.getImageStatus() == ImageStatus.REJECTED;
        if ((rejectsProduct || rejectsImage) && reviewNote == null) {
            throw new IllegalArgumentException("Review note is required when rejecting product data or image.");
        }
    }

    private void validateDisplayImageUrl(String displayImageUrl) {
        try {
            URI uri = new URI(displayImageUrl);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null ||
                    (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Display image URL must be an absolute HTTP or HTTPS URL.");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Display image URL must be a valid URL.");
        }
    }

    private void addAuditIfChanged(
            List<FoodProductReviewAuditEntity> audits,
            FoodItemEntity product,
            String reviewedBy,
            FoodProductReviewAuditAction action,
            String fieldName,
            Object oldValue,
            Object newValue,
            String note
    ) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }

        FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
        audit.setFoodItem(product);
        audit.setReviewedBy(trimToNull(reviewedBy) == null ? "unknown" : reviewedBy.trim());
        audit.setActionType(action);
        audit.setFieldName(fieldName);
        audit.setOldValue(toAuditValue(oldValue));
        audit.setNewValue(toAuditValue(newValue));
        audit.setNote(note);
        audits.add(audit);
    }

    private String toAuditValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private FoodProductReviewAuditDto toAuditDto(FoodProductReviewAuditEntity audit) {
        FoodProductReviewAuditDto dto = new FoodProductReviewAuditDto();
        dto.setId(audit.getId());
        dto.setFoodItemId(audit.getFoodItem().getId());
        dto.setReviewedBy(audit.getReviewedBy());
        dto.setActionType(audit.getActionType());
        dto.setFieldName(audit.getFieldName());
        dto.setOldValue(audit.getOldValue());
        dto.setNewValue(audit.getNewValue());
        dto.setNote(audit.getNote());
        dto.setCreatedAt(audit.getCreatedAt());
        return dto;
    }

    private void validateMergeRequest(FoodProductMergeRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Merge request must not be empty.");
        }
        if (request.getTargetProductId() == null) {
            throw new IllegalArgumentException("Target product id must not be empty.");
        }
        if (request.getDuplicateProductIds() == null || request.getDuplicateProductIds().isEmpty()) {
            throw new IllegalArgumentException("Duplicate product ids must not be empty.");
        }
        if (request.getDuplicateProductIds().contains(request.getTargetProductId())) {
            throw new IllegalArgumentException("Target product id must not be included in duplicate product ids.");
        }
    }

    private FoodProductReviewAuditEntity buildMergeAudit(
            FoodItemEntity targetProduct,
            List<Long> duplicateProductIds,
            String reviewedBy,
            int reassignedFoodLogCount,
            int reassignedFavoriteCount,
            int removedFavoriteCount
    ) {
        FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
        audit.setFoodItem(targetProduct);
        audit.setReviewedBy(trimToNull(reviewedBy) == null ? "unknown" : reviewedBy.trim());
        audit.setActionType(FoodProductReviewAuditAction.MERGE);
        audit.setFieldName("duplicateProductIds");
        audit.setOldValue(duplicateProductIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        audit.setNewValue(String.valueOf(targetProduct.getId()));
        audit.setNote("reassignedFoodLogs=" + reassignedFoodLogCount
                + "; reassignedFavorites=" + reassignedFavoriteCount
                + "; removedConflictingFavorites=" + removedFavoriteCount);
        return audit;
    }

    private List<Long> distinctDuplicateProductIds(FoodProductMergeRequestDto request) {
        return request.getDuplicateProductIds().stream()
                .filter(id -> id != null && !id.equals(request.getTargetProductId()))
                .distinct()
                .toList();
    }

    private void validateAllDuplicateProductsFound(List<Long> duplicateProductIds, List<FoodItemEntity> duplicateProducts) {
        if (duplicateProducts.size() == duplicateProductIds.size()) {
            return;
        }

        List<Long> foundIds = duplicateProducts.stream()
                .map(FoodItemEntity::getId)
                .toList();
        List<Long> missingIds = duplicateProductIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        throw new ResourceNotFoundException("Duplicate food products not found with ids: " + missingIds);
    }

    private void validateSameNormalizedBarcode(FoodItemEntity targetProduct, List<FoodItemEntity> duplicateProducts) {
        String targetNormalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(targetProduct.getNormalizedBarcode());
        if (targetNormalizedBarcode == null) {
            throw new IllegalArgumentException("Target product must have a normalized barcode before merge.");
        }

        boolean allSameBarcode = duplicateProducts.stream()
                .map(FoodItemEntity::getNormalizedBarcode)
                .map(FoodProductNormalizationRules::normalizeBarcode)
                .allMatch(targetNormalizedBarcode::equals);
        if (!allSameBarcode) {
            throw new IllegalArgumentException("All merged products must share the same normalized barcode.");
        }
    }

    private long calculateMergedUsageCount(FoodItemEntity targetProduct, List<FoodItemEntity> duplicateProducts) {
        long targetUsageCount = targetProduct.getUsageCount() == null ? 0L : targetProduct.getUsageCount();
        long duplicateUsageCount = duplicateProducts.stream()
                .map(FoodItemEntity::getUsageCount)
                .mapToLong(value -> value == null ? 0L : value)
                .sum();
        return targetUsageCount + duplicateUsageCount;
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
                Sort.Order.desc("reviewPriority"),
                Sort.Order.desc("usageCount"),
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

    private List<FoodProductDuplicateGroupDto> buildDuplicateGroups(List<String> normalizedBarcodes) {
        if (normalizedBarcodes.isEmpty()) {
            return List.of();
        }

        Sort productSort = Sort.by(
                Sort.Order.asc("normalizedBarcode"),
                Sort.Order.desc("qualityScore"),
                Sort.Order.desc("usageCount"),
                Sort.Order.asc("id")
        );
        List<FoodItemEntity> duplicateProducts = foodItemRepository.findByNormalizedBarcodeIn(
                normalizedBarcodes,
                productSort
        );

        Map<String, List<FoodItemEntity>> productsByBarcode = duplicateProducts.stream()
                .collect(Collectors.groupingBy(
                        FoodItemEntity::getNormalizedBarcode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return normalizedBarcodes.stream()
                .map(normalizedBarcode -> toDuplicateGroup(normalizedBarcode, productsByBarcode.get(normalizedBarcode)))
                .filter(group -> group.getProductCount() > 1)
                .toList();
    }

    private FoodProductDuplicateGroupDto toDuplicateGroup(String normalizedBarcode, List<FoodItemEntity> products) {
        List<FoodProductDto> productDtos = FoodItemMapper.mapEntityListToDtoList(
                products == null ? List.of() : products
        );
        return new FoodProductDuplicateGroupDto(normalizedBarcode, productDtos.size(), productDtos);
    }
}
