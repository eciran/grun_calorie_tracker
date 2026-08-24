package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminProductIntakeSummaryDto;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.AdminProductIntakeQueue;
import com.grun.calorietracker.enums.FoodProductResolutionMode;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductReviewRiskLevel;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.impl.AdminProductIntakeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminProductIntakeServiceImplTest {
    private final FoodProductReviewCaseRepository repository = mock(FoodProductReviewCaseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FoodItemRepository foodItemRepository = mock(FoodItemRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final FoodProductReviewCaseAssetRepository assetRepository = mock(FoodProductReviewCaseAssetRepository.class);
    private final FoodProductReviewCaseService reviewCaseService = mock(FoodProductReviewCaseService.class);
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final AdminProductIntakeServiceImpl service = new AdminProductIntakeServiceImpl(repository, userRepository, foodItemRepository, notificationRepository, assetRepository, reviewCaseService, objectMapper, 24);

    @ParameterizedTest
    @EnumSource(AdminProductIntakeQueue.class)
    void everyQueueReturnsPaginatedPrivacySafeSummaries(AdminProductIntakeQueue queue) {
        FoodProductReviewCaseEntity entity = reviewCase();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(java.util.List.of(entity), invocation.getArgument(1), 1));

        var result = service.list("catalog@grun.app", queue, null, MarketRegion.UK_IE, -1, 500);

        assertEquals(1, result.totalElements());
        assertEquals(0, result.page());
        assertEquals(100, result.size());
        assertEquals(91L, result.content().get(0).id());
        assertEquals(MarketRegion.UK_IE, result.content().get(0).marketRegion());
    }

    @Test
    void summaryContractCannotExposeStorageKeysOrSignedUrls() {
        Set<String> fields = Arrays.stream(AdminProductIntakeSummaryDto.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertFalse(fields.stream().anyMatch(name -> name.contains("url") || name.contains("storage")
                || name.contains("checksum") || name.contains("sha256")));
    }

    private FoodProductReviewCaseEntity reviewCase() {
        FoodProductReviewCaseEntity entity = new FoodProductReviewCaseEntity();
        entity.setId(91L);
        entity.setSource(FoodProductReviewCaseSource.USER_OCR);
        entity.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        entity.setMarketRegion(MarketRegion.UK_IE);
        entity.setNormalizedBarcode("5012345678900");
        entity.setResolutionMode(FoodProductResolutionMode.NEW_CANDIDATE);
        entity.setRiskLevel(FoodProductReviewRiskLevel.HIGH);
        entity.setReviewedBy("catalog@grun.app");
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 30, 9, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 7, 30, 9, 5));
        return entity;
    }
}