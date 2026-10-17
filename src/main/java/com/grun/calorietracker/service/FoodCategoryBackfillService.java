package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodCategoryBackfillResultDto;
import com.grun.calorietracker.dto.FoodCategoryBackfillPreviewDto;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.support.FoodCategoryImportResolutionService;
import com.grun.calorietracker.service.support.FoodCategoryBackfillBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodCategoryBackfillService {
    private static final int MAX_BATCH_SIZE = 1000;

    private final FoodItemRepository foodItemRepository;
    private final FoodProductQualityIssueRepository qualityIssueRepository;
    private final FoodCategoryBackfillBatchService batchService;

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
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Category backfill must run outside an outer transaction; each batch owns its transaction");
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        int batchLimit = Math.max(1, Math.min(maxBatches, 1000));
        long cursor = Math.max(0L, afterId);
        long scanned = 0L;
        long assigned = 0L;
        long preserved = 0L;
        long review = 0L;
        int batches = 0;

        while (batches < batchLimit) {
            var batch = batchService.process(cursor, batchSize, actor);
            if (batch.selected() == 0) break;
            var result = batch.resolution();
            scanned += batch.scanned();
            assigned += result.assignedProducts();
            preserved += result.preservedProducts();
            review += result.reviewProducts();
            cursor = batch.nextId();
            batches++;

            if (batch.selected() < batchSize) break;
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
