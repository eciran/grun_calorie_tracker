package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.FoodProductUploadSessionRepository;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.impl.FoodProductEvidenceRetentionServiceImpl;
import com.grun.calorietracker.service.support.FoodProductIntakeMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodProductEvidenceRetentionServiceImplTest {
 @Mock FoodProductReviewCaseAssetRepository assets; @Mock FoodProductUploadSessionRepository sessions;
 @Mock FoodProductReviewCaseRepository cases; @Mock FoodProductDirectUploadStorage storage;
 @Mock FoodProductIntakeMetrics metrics;
 FoodProductEvidenceRetentionServiceImpl service;
 @BeforeEach void setup(){service=new FoodProductEvidenceRetentionServiceImpl(new FoodContributionStorageProperties(),assets,sessions,cases,storage,metrics);}
 @Test void lockedBatchDeletesAndMarksCompletion(){var a=asset(1L,"pending/product-intakes/a");when(assets.lockCleanupBatch(any(),any(Pageable.class))).thenReturn(List.of(a));assertEquals(1,service.cleanupExpiredAndWithdrawn());verify(storage).delete(a.getStorageKey());assertEquals(FoodProductAssetDeletionState.DELETED,a.getDeletionState());assertNotNull(a.getDeletedAt());assertEquals(1,a.getDeletionAttemptCount());}
 @Test void failedDeleteRemainsRetryable(){var a=asset(2L,"pending/product-intakes/b");when(assets.lockCleanupBatch(any(),any(Pageable.class))).thenReturn(List.of(a));doThrow(new IllegalStateException("temporary provider failure")).when(storage).delete(a.getStorageKey());assertEquals(0,service.cleanupExpiredAndWithdrawn());assertEquals(FoodProductAssetDeletionState.FAILED,a.getDeletionState());assertEquals("temporary provider failure",a.getLastDeletionError());}
 @Test void gdprPurgeDeletesObjectsAndReferences(){var a=asset(3L,"pending/product-intakes/c");var b=asset(4L,"pending/product-intakes/d");when(assets.findAllByUploadSessionCreatedById(9L)).thenReturn(List.of(a,b));service.purgeForUser(9L);verify(storage).delete(a.getStorageKey());verify(storage).delete(b.getStorageKey());verify(assets).deleteAllInBatch(List.of(a,b));verify(sessions).deleteAllByCreatedById(9L);verify(cases).anonymizeSubmittedByUserId(9L);}
 private FoodProductReviewCaseAssetEntity asset(Long id,String key){var a=new FoodProductReviewCaseAssetEntity();a.setId(id);a.setStorageKey(key);a.setDeletionState(FoodProductAssetDeletionState.ACTIVE);a.setDeletionAttemptCount(0);return a;}
}
