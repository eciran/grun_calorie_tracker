package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedDuplicateBatchJobRequestDto;
import com.grun.calorietracker.entity.FoodBrandedDuplicateBatchJobEntity;
import com.grun.calorietracker.repository.FoodBrandedDuplicateBatchJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrandedDuplicateBatchJobServiceTest {
    private FoodBrandedDuplicateBatchJobRepository repository;
    private AtomicReference<Runnable> submitted;
    private BrandedDuplicateBatchJobService service;

    @BeforeEach
    void setUp() {
        repository = mock(FoodBrandedDuplicateBatchJobRepository.class);
        submitted = new AtomicReference<>();
        Executor executor = submitted::set;
        when(repository.findByStatusIn(any())).thenReturn(List.of());
        service = new BrandedDuplicateBatchJobService(repository,
                mock(BrandedProductDuplicateAnalysisService.class),
                mock(BrandedDuplicateSurvivorApplicationService.class), executor);
    }

    @Test
    void startsDurableBackgroundJob() {
        when(repository.existsByStatusIn(any())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            FoodBrandedDuplicateBatchJobEntity job = invocation.getArgument(0);
            job.setId(41L);
            return job;
        });
        var result = service.start(new BrandedDuplicateBatchJobRequestDto(" cola "), "owner@example.com");
        assertThat(result.id()).isEqualTo(41L);
        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.query()).isEqualTo("cola");
        assertThat(submitted.get()).isNotNull();
    }

    @Test
    void pauseRequestIsPersisted() {
        FoodBrandedDuplicateBatchJobEntity job = job("RUNNING");
        when(repository.findById(8L)).thenReturn(Optional.of(job));
        when(repository.saveAndFlush(job)).thenReturn(job);
        var result = service.pause(8L);
        assertThat(result.status()).isEqualTo("PAUSE_REQUESTED");
        assertThat(result.pauseRequested()).isTrue();
    }

    @Test
    void interruptedJobsBecomeResumableOnStartup() {
        FoodBrandedDuplicateBatchJobEntity job = job("RUNNING");
        when(repository.findByStatusIn(any())).thenReturn(List.of(job));
        service.recoverInterruptedJobs();
        assertThat(job.getStatus()).isEqualTo("PAUSED");
        assertThat(job.getLastError()).contains("restarted");
        verify(repository).save(job);
    }

    private FoodBrandedDuplicateBatchJobEntity job(String status) {
        FoodBrandedDuplicateBatchJobEntity job = new FoodBrandedDuplicateBatchJobEntity();
        job.setId(8L);
        job.setStatus(status);
        job.setRequestedBy("owner@example.com");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return job;
    }
}
