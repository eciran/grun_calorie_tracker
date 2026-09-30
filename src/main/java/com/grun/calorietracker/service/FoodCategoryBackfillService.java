package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodCategoryBackfillResultDto;
import com.grun.calorietracker.dto.FoodCategoryBackfillPreviewDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.support.FoodCategoryImportResolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodCategoryBackfillService {
    private static final int MAX_BATCH_SIZE = 1000;

    private final FoodItemRepository foodItemRepository;
    private final FoodProductQualityIssueRepository qualityIssueRepository;
    private final FoodCategoryImportResolutionService resolutionService;

    public FoodCategoryBackfillPreviewDto preview() {
        long count = foodItemRepository.countCategoryResolutionBackfillCandidates();
        if (count == 0L) return new FoodCategoryBackfillPreviewDto(0L, null, null);
        List<Long> first = foodItemRepository.findCategoryResolutionBackfillIds(
                0L, PageRequest.of(0, 1));
        Long firstId = first.isEmpty() ? null : first.get(0);
        Long lastId = foodItemRepository.findLastCategoryResolutionBackfillCandidateId();
        return new FoodCategoryBackfillPreviewDto(count, firstId, lastId);
    }

    public FoodCategoryBackfillResultDto backfill(int requestedBatchSize, int maxBatches,
                                                  long afterId, String actor) {
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        int batchLimit = Math.max(1, Math.min(maxBatches, 1000));
        long cursor = Math.max(0L, afterId);
        long scanned = 0L;
        long assigned = 0L;
        long preserved = 0L;
        long review = 0L;
        int batches = 0;

        while (batches < batchLimit) {
            List<Long> ids = foodItemRepository.findCategoryResolutionBackfillIds(
                    cursor, PageRequest.of(0, batchSize));
            if (ids.isEmpty()) break;

            List<FoodItemEntity> products = foodItemRepository.findByIdIn(ids, Sort.by("id").ascending());
            FoodCategoryImportResolutionService.ResolutionSummary result =
                    resolutionService.resolveAfterImport(products, actor);
            scanned += products.size();
            assigned += result.assignedProducts();
            preserved += result.preservedProducts();
            review += result.reviewProducts();
            cursor = ids.get(ids.size() - 1);
            batches++;

            if (ids.size() < batchSize) break;
        }

        String resolvedActor = actor == null || actor.isBlank()
                ? "category-backfill-reconciliation" : actor.trim();
        int reconciledIssues = qualityIssueRepository
                .resolveOutOfScopeCanonicalCategoryIssues(resolvedActor);
        return new FoodCategoryBackfillResultDto(
                scanned, assigned, preserved, review, reconciledIssues,
                batches, batchSize, cursor);
    }
}
