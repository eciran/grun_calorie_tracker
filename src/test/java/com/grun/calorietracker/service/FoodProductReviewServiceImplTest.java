package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.impl.FoodProductReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodProductReviewServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;

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

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodProductDto result = foodProductReviewService.updateProductReview(1L, request);

        assertEquals("Verified Product", result.getProductName());
        assertEquals("https://cdn.grun.app/products/1.jpg", result.getDisplayImageUrl());
        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());
        assertEquals(ImageSource.ADMIN_UPLOAD, result.getImageSource());
        assertEquals(ImageStatus.APPROVED, result.getImageStatus());
        assertNotNull(result.getLastReviewedAt());
        assertNotNull(result.getQualityScore());
        assertNotNull(result.getReviewPriority());
        verify(foodItemRepository).save(product);
    }
}
