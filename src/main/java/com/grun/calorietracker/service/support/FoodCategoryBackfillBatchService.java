package com.grun.calorietracker.service.support;

import com.grun.calorietracker.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class FoodCategoryBackfillBatchService {
    private final FoodItemRepository products;
    private final FoodCategoryImportResolutionService resolver;
    private final ExternalFoodImportFence fence;

    public record Batch(int selected, int scanned, long nextId,
                        FoodCategoryImportResolutionService.ResolutionSummary resolution) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public Batch process(long afterId, int limit, String actor) {
        if (afterId < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Category batch requires a nonnegative cursor and limit1..1000");
        }
        fence.acquire();
        var ids = products.findCategoryResolutionBackfillIds(afterId, PageRequest.of(0, limit));
        if (ids.isEmpty()) return new Batch(0, 0, afterId,
                new FoodCategoryImportResolutionService.ResolutionSummary(0, 0, 0));
        var current = products.findByIdIn(ids, Sort.by("id").ascending());
        var resolved = resolver.resolveAfterImport(current, actor);
        return new Batch(ids.size(), current.size(), ids.get(ids.size() - 1), resolved);
    }
}
