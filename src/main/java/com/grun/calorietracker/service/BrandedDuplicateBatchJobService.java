package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.FoodBrandedDuplicateBatchJobEntity;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodBrandedDuplicateBatchJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import jakarta.annotation.PostConstruct;

@Service
public class BrandedDuplicateBatchJobService {
    private static final List<String> ACTIVE = List.of("QUEUED", "DISCOVERING", "RUNNING", "PAUSE_REQUESTED");
    private final FoodBrandedDuplicateBatchJobRepository repository;
    private final BrandedProductDuplicateAnalysisService analysisService;
    private final BrandedDuplicateSurvivorApplicationService survivorService;
    private final Executor executor;

    public BrandedDuplicateBatchJobService(
            FoodBrandedDuplicateBatchJobRepository repository,
            BrandedProductDuplicateAnalysisService analysisService,
            BrandedDuplicateSurvivorApplicationService survivorService,
            @Qualifier("brandedDuplicateBatchExecutor") Executor executor
    ) {
        this.repository = repository;
        this.analysisService = analysisService;
        this.survivorService = survivorService;
        this.executor = executor;
    }

    @PostConstruct
    void recoverInterruptedJobs() {
        repository.findByStatusIn(ACTIVE).forEach(job -> {
            job.setStatus("PAUSED");
            job.setPauseRequested(false);
            job.setLastError("Application restarted while this job was active. Resume to continue from the saved page.");
            touch(job);
            repository.save(job);
        });
    }

    public synchronized BrandedDuplicateBatchJobDto start(BrandedDuplicateBatchJobRequestDto request, String actor) {
        if (repository.existsByStatusIn(ACTIVE)) {
            throw new IllegalStateException("Another branded duplicate batch job is already active.");
        }
        FoodBrandedDuplicateBatchJobEntity job = new FoodBrandedDuplicateBatchJobEntity();
        job.setStatus("QUEUED");
        job.setQueryFilter(normalizeQuery(request == null ? null : request.query()));
        job.setRequestedBy(actor(actor));
        job.setSafeOnly(true);
        job.setPageSize(100);
        FoodBrandedDuplicateBatchJobEntity saved = repository.saveAndFlush(job);
        executor.execute(() -> run(saved.getId()));
        return toDto(saved);
    }

    public BrandedDuplicateBatchJobDto get(Long id) {
        return toDto(find(id));
    }

    public List<BrandedDuplicateBatchJobDto> recent() {
        return repository.findTop20ByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    public BrandedDuplicateBatchJobDto pause(Long id) {
        FoodBrandedDuplicateBatchJobEntity job = find(id);
        if (!ACTIVE.contains(job.getStatus())) {
            throw new IllegalStateException("Only an active batch job can be paused.");
        }
        job.setPauseRequested(true);
        job.setStatus("PAUSE_REQUESTED");
        touch(job);
        return toDto(repository.saveAndFlush(job));
    }

    public synchronized BrandedDuplicateBatchJobDto resume(Long id) {
        if (repository.existsByStatusIn(ACTIVE)) {
            throw new IllegalStateException("Another branded duplicate batch job is already active.");
        }
        FoodBrandedDuplicateBatchJobEntity job = find(id);
        if (!"PAUSED".equals(job.getStatus()) && !"FAILED".equals(job.getStatus())) {
            throw new IllegalStateException("Only a paused or failed batch job can be resumed.");
        }
        job.setPauseRequested(false);
        job.setStatus("QUEUED");
        job.setLastError(null);
        touch(job);
        FoodBrandedDuplicateBatchJobEntity saved = repository.saveAndFlush(job);
        executor.execute(() -> run(saved.getId()));
        return toDto(saved);
    }

    void run(Long id) {
        try {
            FoodBrandedDuplicateBatchJobEntity job = find(id);
            if (job.isPauseRequested()) { markPaused(job); return; }
            job.setStatus(job.getNextPage() == 0 ? "DISCOVERING" : "RUNNING");
            if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
            save(job);

            while (true) {
                job = find(id);
                if (job.isPauseRequested()) { markPaused(job); return; }
                BrandedProductDuplicateGroupPageDto page = analysisService.getCandidates(
                        job.getNextPage(), job.getPageSize(), job.getQueryFilter(), null
                );
                if (job.getNextPage() == 0) job.setTotalGroups(page.totalElements());
                job.setStatus("RUNNING");
                save(job);

                for (int itemIndex = job.getNextItemIndex(); itemIndex < page.content().size(); itemIndex++) {
                    BrandedProductDuplicateGroupDto group = page.content().get(itemIndex);
                    job = find(id);
                    if (job.isPauseRequested()) { markPaused(job); return; }
                    job.setProcessedGroups(job.getProcessedGroups() + 1);
                    job.setNextItemIndex(itemIndex + 1);
                    BrandedDuplicateRecommendationDto recommendation = group.recommendation();
                    if (recommendation == null || !"COLLAPSE_DUPLICATES".equals(recommendation.action())
                            || recommendation.confidence() == null || recommendation.confidence() < 99
                            || recommendation.suggestedSurvivorProductId() == null) {
                        job.setSkippedGroups(job.getSkippedGroups() + 1);
                        save(job);
                        continue;
                    }
                    job.setEligibleGroups(job.getEligibleGroups() + 1);
                    if (group.searchCollapse() != null && group.searchCollapse().active()) {
                        job.setSkippedGroups(job.getSkippedGroups() + 1);
                        save(job);
                        continue;
                    }
                    try {
                        survivorService.selectAndApply(new BrandedDuplicateSurvivorApplyRequestDto(
                                group.brandKey(), group.nameKey(), recommendation.suggestedSurvivorProductId(),
                                group.candidateFingerprint()
                        ), job.getRequestedBy());
                        job.setAppliedGroups(job.getAppliedGroups() + 1);
                    } catch (RuntimeException exception) {
                        job.setFailedGroups(job.getFailedGroups() + 1);
                        job.setLastError(limit(exception.getMessage()));
                    }
                    save(job);
                }

                job = find(id);
                job.setNextPage(job.getNextPage() + 1);
                job.setNextItemIndex(0);
                save(job);
                if (page.last() || job.getNextPage() >= page.totalPages()) break;
            }
            job = find(id);
            job.setStatus("COMPLETED");
            job.setCompletedAt(LocalDateTime.now());
            job.setPauseRequested(false);
            save(job);
        } catch (RuntimeException exception) {
            FoodBrandedDuplicateBatchJobEntity job = repository.findById(id).orElse(null);
            if (job != null) {
                job.setStatus("FAILED");
                job.setLastError(limit(exception.getMessage()));
                save(job);
            }
        }
    }

    private void markPaused(FoodBrandedDuplicateBatchJobEntity job) {
        job.setStatus("PAUSED");
        job.setPauseRequested(false);
        save(job);
    }

    private FoodBrandedDuplicateBatchJobEntity find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branded duplicate batch job was not found."));
    }

    private void save(FoodBrandedDuplicateBatchJobEntity job) {
        touch(job);
        repository.saveAndFlush(job);
    }

    private void touch(FoodBrandedDuplicateBatchJobEntity job) { job.setUpdatedAt(LocalDateTime.now()); }
    private String normalizeQuery(String query) { return query == null || query.isBlank() ? null : query.trim(); }
    private String actor(String actor) { return actor == null || actor.isBlank() ? "unknown-admin" : actor.trim(); }
    private String limit(String value) { return value == null ? "Unexpected batch error" : value.substring(0, Math.min(2000, value.length())); }

    private BrandedDuplicateBatchJobDto toDto(FoodBrandedDuplicateBatchJobEntity job) {
        int progress = job.getTotalGroups() <= 0 ? 0 : (int) Math.min(100,
                Math.round(job.getProcessedGroups() * 100d / job.getTotalGroups()));
        if ("COMPLETED".equals(job.getStatus())) progress = 100;
        return new BrandedDuplicateBatchJobDto(
                job.getId(), job.getStatus(), job.getQueryFilter(), job.isSafeOnly(), job.getPageSize(), job.getNextPage(), job.getNextItemIndex(),
                job.getTotalGroups(), job.getEligibleGroups(), job.getProcessedGroups(), job.getAppliedGroups(),
                job.getSkippedGroups(), job.getFailedGroups(), job.isPauseRequested(), progress,
                job.getRequestedBy(), text(job.getCreatedAt()), text(job.getStartedAt()), text(job.getUpdatedAt()),
                text(job.getCompletedAt()), job.getLastError()
        );
    }
    private String text(LocalDateTime value) { return value == null ? null : value.toString(); }
}
