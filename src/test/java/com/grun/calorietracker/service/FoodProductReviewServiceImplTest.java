package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.service.impl.FoodProductReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodProductReviewServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private FoodLogsRepository foodLogsRepository;

    @Mock
    private UserFavoriteRepository userFavoriteRepository;

    @Mock
    private FoodProductReviewAuditRepository foodProductReviewAuditRepository;

    @InjectMocks
    private FoodProductReviewServiceImpl foodProductReviewService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getProductsForReview_whenNoFilters_usesImportedReviewDefaults() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Imported Product");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        when(foodItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        FoodProductReviewPageDto result = foodProductReviewService.getProductsForReview(null, null, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getId());
        assertEquals("Imported Product", result.getContent().get(0).getProductName());
        assertEquals(0, result.getPage());
        assertEquals(1L, result.getTotalElements());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(foodItemRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Sort.Order reviewPriorityOrder = pageableCaptor.getValue().getSort().getOrderFor("reviewPriority");
        Sort.Order usageCountOrder = pageableCaptor.getValue().getSort().getOrderFor("usageCount");
        assertNotNull(reviewPriorityOrder);
        assertNotNull(usageCountOrder);
        assertEquals(Sort.NullHandling.NATIVE, reviewPriorityOrder.getNullHandling());
        assertEquals(Sort.NullHandling.NATIVE, usageCountOrder.getNullHandling());
    }

    @Test
    void updateProductReview_updatesCuratedFieldsAndStatuses() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Raw Name");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setProductName("Verified Product");
        request.setDisplayImageUrl("https://cdn.grun.app/products/1.jpg");
        request.setVerificationStatus(VerificationStatus.VERIFIED);
        request.setImageSource(ImageSource.ADMIN_UPLOAD);
        request.setImageStatus(ImageStatus.APPROVED);
        request.setReviewNote("Verified from product label.");

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodProductDto result = foodProductReviewService.updateProductReview(1L, request, "admin@grun.app");

        assertEquals("Verified Product", result.getProductName());
        assertEquals("https://cdn.grun.app/products/1.jpg", result.getDisplayImageUrl());
        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());
        assertEquals(ImageSource.ADMIN_UPLOAD, result.getImageSource());
        assertEquals(ImageStatus.APPROVED, result.getImageStatus());
        assertNotNull(result.getLastReviewedAt());
        assertNotNull(result.getQualityScore());
        assertNotNull(result.getReviewPriority());
        verify(foodItemRepository).save(product);

        ArgumentCaptor<List<FoodProductReviewAuditEntity>> auditCaptor = ArgumentCaptor.forClass(List.class);
        verify(foodProductReviewAuditRepository).saveAll(auditCaptor.capture());
        assertEquals(5, auditCaptor.getValue().size());
        assertEquals("admin@grun.app", auditCaptor.getValue().get(0).getReviewedBy());
        assertEquals("productName", auditCaptor.getValue().get(0).getFieldName());
        assertEquals("Raw Name", auditCaptor.getValue().get(0).getOldValue());
        assertEquals("Verified Product", auditCaptor.getValue().get(0).getNewValue());
        assertEquals("Verified from product label.", auditCaptor.getValue().get(0).getNote());
    }

    @Test
    void updateProductReview_whenNoFieldChanged_doesNotCreateAudit() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Verified Product");
        product.setDisplayImageUrl("https://cdn.grun.app/products/1.jpg");
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setImageSource(ImageSource.ADMIN_UPLOAD);
        product.setImageStatus(ImageStatus.APPROVED);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setProductName("Verified Product");
        request.setDisplayImageUrl("https://cdn.grun.app/products/1.jpg");
        request.setVerificationStatus(VerificationStatus.VERIFIED);
        request.setImageSource(ImageSource.ADMIN_UPLOAD);
        request.setImageStatus(ImageStatus.APPROVED);

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        foodProductReviewService.updateProductReview(1L, request, "admin@grun.app");

        verify(foodProductReviewAuditRepository, never()).saveAll(any());
    }

    @Test
    void updateProductReview_whenVerifiedProductNameIsBlank_throwsIllegalArgumentException() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("   ");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setVerificationStatus(VerificationStatus.VERIFIED);

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> foodProductReviewService.updateProductReview(1L, request));
        verify(foodItemRepository, never()).save(any(FoodItemEntity.class));
        verify(foodProductReviewAuditRepository, never()).saveAll(any());
    }

    @Test
    void updateProductReview_whenApprovedImageUrlIsBlank_throwsIllegalArgumentException() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Nutella");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setDisplayImageUrl(" ");
        request.setImageStatus(ImageStatus.APPROVED);

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> foodProductReviewService.updateProductReview(1L, request));
        verify(foodItemRepository, never()).save(any(FoodItemEntity.class));
        verify(foodProductReviewAuditRepository, never()).saveAll(any());
    }

    @Test
    void updateProductReview_whenDisplayImageUrlIsInvalid_throwsIllegalArgumentException() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Nutella");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setDisplayImageUrl("ftp://cdn.grun.app/products/1.jpg");

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> foodProductReviewService.updateProductReview(1L, request));
        verify(foodItemRepository, never()).save(any(FoodItemEntity.class));
        verify(foodProductReviewAuditRepository, never()).saveAll(any());
    }

    @Test
    void updateProductReview_whenRejectingProductWithoutNote_throwsIllegalArgumentException() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Nutella");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setVerificationStatus(VerificationStatus.REJECTED);

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> foodProductReviewService.updateProductReview(1L, request));
        verify(foodItemRepository, never()).save(any(FoodItemEntity.class));
        verify(foodProductReviewAuditRepository, never()).saveAll(any());
    }

    @Test
    void updateProductReview_whenRejectingImageWithoutNote_throwsIllegalArgumentException() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Nutella");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setImageStatus(ImageStatus.REJECTED);

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> foodProductReviewService.updateProductReview(1L, request));
        verify(foodItemRepository, never()).save(any(FoodItemEntity.class));
        verify(foodProductReviewAuditRepository, never()).saveAll(any());
    }

    @Test
    void getProductReviewAudits_returnsPaginatedAuditEntries() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);

        FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
        audit.setId(10L);
        audit.setFoodItem(product);
        audit.setReviewedBy("admin@grun.app");
        audit.setActionType(FoodProductReviewAuditAction.STATUS_CHANGE);
        audit.setFieldName("verificationStatus");
        audit.setOldValue("RAW_IMPORTED");
        audit.setNewValue("VERIFIED");

        when(foodItemRepository.existsById(1L)).thenReturn(true);
        when(foodProductReviewAuditRepository.findByFoodItemId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(audit)));

        FoodProductReviewAuditPageDto result = foodProductReviewService.getProductReviewAudits(1L, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(10L, result.getContent().get(0).getId());
        assertEquals(1L, result.getContent().get(0).getFoodItemId());
        assertEquals("admin@grun.app", result.getContent().get(0).getReviewedBy());
        assertEquals(FoodProductReviewAuditAction.STATUS_CHANGE, result.getContent().get(0).getActionType());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(foodProductReviewAuditRepository).findByFoodItemId(eq(1L), pageableCaptor.capture());
        assertNotNull(pageableCaptor.getValue().getSort().getOrderFor("createdAt"));
        assertNotNull(pageableCaptor.getValue().getSort().getOrderFor("id"));
    }

    @Test
    void getDuplicateProductGroups_returnsGroupedProductsByNormalizedBarcode() {
        FoodItemEntity firstProduct = new FoodItemEntity();
        firstProduct.setId(1L);
        firstProduct.setName("Nutella");
        firstProduct.setBarcode("3017620422003");
        firstProduct.setNormalizedBarcode("3017620422003");

        FoodItemEntity secondProduct = new FoodItemEntity();
        secondProduct.setId(2L);
        secondProduct.setName("Nutella Duplicate");
        secondProduct.setBarcode("301-762-0422003");
        secondProduct.setNormalizedBarcode("3017620422003");

        when(foodItemRepository.findDuplicateNormalizedBarcodes(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of("3017620422003")));
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class)))
                .thenReturn(List.of(firstProduct, secondProduct));

        FoodProductDuplicateGroupPageDto result = foodProductReviewService.getDuplicateProductGroups(0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals("3017620422003", result.getContent().get(0).getNormalizedBarcode());
        assertEquals(2, result.getContent().get(0).getProductCount());
        assertEquals("Nutella", result.getContent().get(0).getProducts().get(0).getProductName());

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(foodItemRepository).findByNormalizedBarcodeIn(any(), sortCaptor.capture());

        Sort.Order qualityScoreOrder = sortCaptor.getValue().getOrderFor("qualityScore");
        Sort.Order usageCountOrder = sortCaptor.getValue().getOrderFor("usageCount");
        assertNotNull(qualityScoreOrder);
        assertNotNull(usageCountOrder);
        assertEquals(Sort.NullHandling.NATIVE, qualityScoreOrder.getNullHandling());
        assertEquals(Sort.NullHandling.NATIVE, usageCountOrder.getNullHandling());
    }

    @Test
    void mergeDuplicateProducts_reassignsReferencesAndDeletesDuplicates() {
        FoodItemEntity targetProduct = new FoodItemEntity();
        targetProduct.setId(1L);
        targetProduct.setName("Nutella");
        targetProduct.setNormalizedBarcode("3017620422003");
        targetProduct.setUsageCount(5L);

        FoodItemEntity duplicateProduct = new FoodItemEntity();
        duplicateProduct.setId(2L);
        duplicateProduct.setName("Nutella Duplicate");
        duplicateProduct.setNormalizedBarcode("3017620422003");
        duplicateProduct.setUsageCount(3L);

        FoodProductMergeRequestDto request = new FoodProductMergeRequestDto(1L, List.of(2L));

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(targetProduct));
        when(foodItemRepository.findAllById(List.of(2L))).thenReturn(List.of(duplicateProduct));
        when(userFavoriteRepository.deleteConflictingFavoritesBeforeMerge(1L, List.of(2L))).thenReturn(1);
        when(userFavoriteRepository.reassignFoodItemReferences(targetProduct, List.of(2L))).thenReturn(2);
        when(foodLogsRepository.reassignFoodItemReferences(targetProduct, List.of(2L))).thenReturn(4);
        when(foodItemRepository.save(targetProduct)).thenReturn(targetProduct);

        FoodProductMergeResponseDto result = foodProductReviewService.mergeDuplicateProducts(request, "admin@grun.app");

        assertEquals(1L, result.getTargetProduct().getId());
        assertEquals(8L, result.getTargetProduct().getUsageCount());
        assertEquals(List.of(2L), result.getMergedProductIds());
        assertEquals(4, result.getReassignedFoodLogCount());
        assertEquals(2, result.getReassignedFavoriteCount());
        assertEquals(1, result.getRemovedConflictingFavoriteCount());

        ArgumentCaptor<FoodProductReviewAuditEntity> auditCaptor =
                ArgumentCaptor.forClass(FoodProductReviewAuditEntity.class);
        verify(foodProductReviewAuditRepository).save(auditCaptor.capture());
        assertEquals(FoodProductReviewAuditAction.MERGE, auditCaptor.getValue().getActionType());
        assertEquals("duplicateProductIds", auditCaptor.getValue().getFieldName());
        assertEquals("2", auditCaptor.getValue().getOldValue());
        assertEquals("1", auditCaptor.getValue().getNewValue());
        assertEquals("admin@grun.app", auditCaptor.getValue().getReviewedBy());
        assertEquals(
                "reassignedFoodLogs=4; reassignedFavorites=2; removedConflictingFavorites=1",
                auditCaptor.getValue().getNote()
        );
        verify(foodItemRepository).deleteAll(List.of(duplicateProduct));
    }
}
