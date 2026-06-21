package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueBackfillResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.service.impl.FoodProductReviewServiceImpl;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

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

    @Mock
    private FoodProductQualityIssueRepository foodProductQualityIssueRepository;

    @Mock
    private FoodProductQualityIssueTracker foodProductQualityIssueTracker;

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
        assertEquals("admin@grun.app", result.getReviewedBy());
        assertNotNull(result.getLastReviewedAt());
        assertNotNull(result.getQualityScore());
        assertNotNull(result.getReviewPriority());
        verify(foodItemRepository).save(product);
        verify(foodProductQualityIssueTracker).syncReviewIssues(product, "admin@grun.app");

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
        verify(foodProductQualityIssueTracker).syncReviewIssues(product, "admin@grun.app");
    }

    @Test
    void updateProductReview_updatesNutritionAndServingFieldsWithAudit() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Raw Product");
        product.setCalories(100.0);
        product.setProtein(1.0);
        product.setFat(2.0);
        product.setCarbs(3.0);
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setCalories(150.0);
        request.setProtein(12.5);
        request.setFat(4.0);
        request.setCarbs(22.0);
        request.setFiber(3.5);
        request.setSugar(5.0);
        request.setSodium(0.25);
        request.setPotassium(0.36);
        request.setCalcium(0.18);
        request.setIron(0.005);
        request.setVitaminA(0.001);
        request.setSaturatedFat(1.5);
        request.setTransFat(0.0);
        request.setServingSizeGrams(125.0);
        request.setServingUnit("g");
        request.setReviewNote("Corrected from product label.");

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodProductDto result = foodProductReviewService.updateProductReview(1L, request, "admin@grun.app");

        assertEquals(150.0, result.getCalories());
        assertEquals(12.5, result.getProtein());
        assertEquals(4.0, result.getFat());
        assertEquals(22.0, result.getCarbs());
        assertEquals(3.5, result.getFiber());
        assertEquals(5.0, result.getSugar());
        assertEquals(0.25, result.getSodium());
        assertEquals(0.36, result.getPotassium());
        assertEquals(0.18, result.getCalcium());
        assertEquals(0.005, result.getIron());
        assertEquals(0.001, result.getVitaminA());
        assertEquals(1.5, result.getSaturatedFat());
        assertEquals(0.0, result.getTransFat());
        assertEquals(125.0, result.getServingSize());
        assertEquals("g", result.getServingUnit());

        ArgumentCaptor<List<FoodProductReviewAuditEntity>> auditCaptor = ArgumentCaptor.forClass(List.class);
        verify(foodProductReviewAuditRepository).saveAll(auditCaptor.capture());
        assertEquals(15, auditCaptor.getValue().size());
        assertEquals("calories", auditCaptor.getValue().get(0).getFieldName());
        assertEquals("100.0", auditCaptor.getValue().get(0).getOldValue());
        assertEquals("150.0", auditCaptor.getValue().get(0).getNewValue());
        verify(foodProductQualityIssueTracker).syncReviewIssues(product, "admin@grun.app");
    }

    @Test
    void updateProductReview_whenNutritionValueIsNegative_throwsIllegalArgumentException() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Raw Product");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setCalories(-1.0);

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class,
                () -> foodProductReviewService.updateProductReview(1L, request, "admin@grun.app"));
        verify(foodItemRepository, never()).save(any(FoodItemEntity.class));
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
    void updateProductReview_whenApprovedImageUrlIsBlank_allowsOptionalImageMetadata() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Nutella");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setDisplayImageUrl(" ");
        request.setImageStatus(ImageStatus.APPROVED);

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(product)).thenReturn(product);

        FoodProductDto result = foodProductReviewService.updateProductReview(1L, request);

        assertEquals(ImageStatus.APPROVED, result.getImageStatus());
        verify(foodItemRepository).save(product);
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
    void updateProductReview_whenRejectingImageWithoutNote_allowsOptionalImageMetadata() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Nutella");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setImageStatus(ImageStatus.REJECTED);

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(product)).thenReturn(product);

        FoodProductDto result = foodProductReviewService.updateProductReview(1L, request);

        assertEquals(ImageStatus.REJECTED, result.getImageStatus());
        verify(foodItemRepository).save(product);
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
        verify(foodProductQualityIssueTracker).syncReviewIssues(targetProduct, "admin@grun.app");
    }

    @Test
    void backfillQualityIssues_scansProductsInBatchesAndSyncsIssues() {
        FoodItemEntity first = new FoodItemEntity();
        first.setId(1L);
        first.setName("Coke Zero");
        first.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        first.setBarcode("5449000214799");
        first.setNormalizedBarcode("5449000214799");
        first.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        first.setImageStatus(ImageStatus.NEEDS_REVIEW);
        first.setMarketRegion(MarketRegion.UK_IE);
        first.setExternalImageUrl("https://images.openfoodfacts.org/images/products/544/900/021/4799/front_en.363.400.jpg");
        first.setCalories(1.0);
        first.setProtein(0.0);
        first.setFat(0.0);
        first.setCarbs(0.0);
        first.setServingSizeGrams(100.0);
        FoodItemEntity second = new FoodItemEntity();
        second.setId(2L);

        when(foodItemRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(first),
                        PageRequest.of(0, 1, Sort.by("id").ascending()),
                        2
                ))
                .thenReturn(new PageImpl<>(
                        List.of(second),
                        PageRequest.of(1, 1, Sort.by("id").ascending()),
                        2
                ));

        FoodProductQualityIssueBackfillResultDto result =
                foodProductReviewService.backfillQualityIssues(1, "admin@grun.app");

        assertEquals(2L, result.getScannedProducts());
        assertEquals(2, result.getProcessedBatches());
        assertEquals(1, result.getPageSize());
        assertEquals(60, first.getQualityScore());
        verify(foodProductQualityIssueTracker).syncReviewIssues(first, "admin@grun.app");
        verify(foodProductQualityIssueTracker).syncReviewIssues(second, "admin@grun.app");
    }

    @Test
    void getProductQualityIssues_whenActiveOnly_returnsActiveIssues() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);

        FoodProductQualityIssueEntity issue = new FoodProductQualityIssueEntity();
        issue.setId(7L);
        issue.setFoodItem(product);
        issue.setIssueType(FoodProductQualityIssue.SUSPICIOUS_MACROS);
        issue.setReason("Macro value is suspiciously high.");
        issue.setResolved(false);

        when(foodItemRepository.existsById(1L)).thenReturn(true);
        when(foodProductQualityIssueRepository.findByFoodItemIdAndResolvedFalse(1L)).thenReturn(List.of(issue));

        List<FoodProductQualityIssueDto> result = foodProductReviewService.getProductQualityIssues(1L, true);

        assertEquals(1, result.size());
        assertEquals(FoodProductQualityIssue.SUSPICIOUS_MACROS, result.get(0).getIssueType());
        assertEquals("Macro value is suspiciously high.", result.get(0).getReason());
    }

    @Test
    void importNutritionCorrections_updatesProductMatchedByBarcode() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("Raw Product");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "corrections.csv",
                "text/csv",
                "barcode,calories,protein,display_image_url\n3017620422003,539,6.3,https://cdn.grun.app/products/3017620422003.jpg\n".getBytes()
        );

        when(foodItemRepository.findByNormalizedBarcode("3017620422003")).thenReturn(Optional.of(product));
        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = foodProductReviewService.importNutritionCorrections(file, "admin@grun.app");

        assertEquals(1, result.getTotalRows());
        assertEquals(1, result.getUpdatedRows());
        assertEquals(0, result.getSkippedRows());
        assertEquals(539.0, product.getCalories());
        assertEquals(6.3, product.getProtein());
        assertEquals("https://cdn.grun.app/products/3017620422003.jpg", product.getDisplayImageUrl());
        verify(foodProductQualityIssueTracker).syncReviewIssues(product, "admin@grun.app");
    }
    @Test
    void importNutritionCorrections_whenMarkVerified_marksImportedProductVerified() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(2L);
        product.setName("Corrected Product");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "corrections.csv",
                "text/csv",
                "barcode,product_name,calories\n3017620422003,Corrected Product,539\n".getBytes()
        );

        when(foodItemRepository.findByNormalizedBarcode("3017620422003")).thenReturn(Optional.of(product));
        when(foodItemRepository.findById(2L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = foodProductReviewService.importNutritionCorrections(file, "admin@grun.app", false, true);

        assertEquals(1, result.getUpdatedRows());
        assertEquals(VerificationStatus.VERIFIED, product.getVerificationStatus());
        assertEquals(539.0, product.getCalories());
    }
}
