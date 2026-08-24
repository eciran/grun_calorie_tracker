package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.FoodProductResolutionMode;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductReviewRiskLevel;
import com.grun.calorietracker.enums.MarketRegion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FoodProductReviewCaseLockingTest {

    @Autowired
    private FoodProductReviewCaseRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentDecisionWaitsForTheCurrentCaseWriter() throws Exception {
        Long caseId = new TransactionTemplate(transactionManager).execute(status -> repository.saveAndFlush(caseEntity()).getId());
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                repository.findByIdForAssignment(caseId).orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));

            var second = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                repository.findByIdForAssignment(caseId).orElseThrow();
                secondLocked.countDown();
            }));

            assertFalse(secondLocked.await(250, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertTrue(secondLocked.await(5, TimeUnit.SECONDS));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private FoodProductReviewCaseEntity caseEntity() {
        FoodProductReviewCaseEntity reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setIdempotencyKey("locking-case");
        reviewCase.setSource(FoodProductReviewCaseSource.USER_OCR);
        reviewCase.setMarketRegion(MarketRegion.GLOBAL);
        reviewCase.setResolutionMode(FoodProductResolutionMode.NEW_CANDIDATE);
        reviewCase.setStatus(FoodProductReviewCaseStatus.APPROVED);
        reviewCase.setRiskLevel(FoodProductReviewRiskLevel.MEDIUM);
        reviewCase.setSchemaVersion(1);
        reviewCase.setSubmittedValuesJson("{\"calories\":100}");
        reviewCase.setTemporaryEvidenceAllowed(false);
        reviewCase.setPublicMediaAllowed(false);
        return reviewCase;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release case lock");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding case lock", interrupted);
        }
    }
}
