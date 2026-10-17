package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.FoodCatalogMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodCatalogMaintenanceServiceImpl implements FoodCatalogMaintenanceService {
    private com.grun.calorietracker.service.support.ExternalFoodImportFence externalFoodImportFence;

    @org.springframework.beans.factory.annotation.Autowired
    void setExternalFoodImportFence(
            com.grun.calorietracker.service.support.ExternalFoodImportFence externalFoodImportFence
    ) {
        this.externalFoodImportFence = externalFoodImportFence;
    }

    private void acquireCatalogWriteFence() {
        if (externalFoodImportFence != null) externalFoodImportFence.acquire();
    }
    private static final EnumSet<FoodDataSource> EXTERNAL_SOURCES = EnumSet.of(
            FoodDataSource.OPEN_FOOD_FACTS, FoodDataSource.USDA_FOODDATA,
            FoodDataSource.EDAMAM, FoodDataSource.NUTRITIONIX
    );

    private final FoodItemRepository foodItemRepository;
    private final FoodProductQualityIssueRepository issueRepository;
    private final ProductCatalogAuditService catalogAuditService;

    @Value("${grun.product-catalog.maintenance.stale-days:180}")
    private int staleDays;
    @Value("${grun.product-catalog.maintenance.batch-size:500}")
    private int batchSize;
    @Value("${grun.product-catalog.maintenance.scheduled-quality-enabled:false}")
    private boolean scheduledQualityEnabled;

    @Override
    @Transactional
    public int refreshStaleSourceQueue() {
        acquireCatalogWriteFence();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(staleDays);
        LocalDateTime now = LocalDateTime.now();
        List<FoodProductQualityIssueEntity> activeStaleIssues = issueRepository
                .findByIssueTypeAndResolvedFalse(FoodProductQualityIssue.STALE_SOURCE, PageRequest.of(0, batchSize));
        for (FoodProductQualityIssueEntity issue : activeStaleIssues) {
            FoodItemEntity product = issue.getFoodItem();
            boolean stillStale = product != null
                    && EXTERNAL_SOURCES.contains(product.getDataSource())
                    && (product.getLastExternalSyncAt() == null || product.getLastExternalSyncAt().isBefore(cutoff));
            if (!stillStale) {
                issue.setResolved(true);
                issue.setResolvedAt(now);
                issue.setResolvedBy("system:product-maintenance");
                issueRepository.save(issue);
            }
        }

        List<FoodItemEntity> staleProducts = foodItemRepository.findStaleExternalProducts(
                EXTERNAL_SOURCES, cutoff, PageRequest.of(0, batchSize));

        for (FoodItemEntity product : staleProducts) {
            FoodProductQualityIssueEntity issue = issueRepository
                    .findByFoodItemIdAndIssueTypeAndResolvedFalse(product.getId(), FoodProductQualityIssue.STALE_SOURCE)
                    .orElseGet(FoodProductQualityIssueEntity::new);
            issue.setFoodItem(product);
            issue.setIssueType(FoodProductQualityIssue.STALE_SOURCE);
            issue.setIdentifier(product.getSourceKey());
            issue.setReason("External source has not been synchronized within the configured freshness window.");
            issue.setLastDetectedAt(now);
            issue.setResolved(false);
            issueRepository.save(issue);
        }
        return staleProducts.size();
    }

    @Override
    public void runScheduledQualityQueue() {
        if (scheduledQualityEnabled) {
            catalogAuditService.scheduledStep();
        }
    }

    @Scheduled(cron = "${grun.product-catalog.maintenance.stale-scan-cron:0 10 2 * * *}")
    public void scheduledStaleSourceQueue() {
        refreshStaleSourceQueue();
    }

    @Scheduled(cron = "${grun.product-catalog.maintenance.quality-scan-cron:0 10 5 * * *}",
            zone = "${grun.product-catalog.audit.zone:Europe/Dublin}")
    public void scheduledQualityQueue() {
        runScheduledQualityQueue();
    }
}
