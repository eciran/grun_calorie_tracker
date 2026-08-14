package com.grun.calorietracker.service;

import com.grun.calorietracker.config.*;
import com.grun.calorietracker.dto.TestFeedbackScreenshotUploadRequestDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.repository.TestFeedbackSubmissionRepository;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.impl.TestFeedbackScreenshotServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TestFeedbackScreenshotServiceImplTest {
 @Test void authorizesOwnedPrivateScreenshotWithRetentionMetadata(){
  TestFeedbackProperties properties=new TestFeedbackProperties(); properties.setEnabled(true);
  FoodContributionStorageProperties storageProperties=new FoodContributionStorageProperties();
  storageProperties.getS3().setPrefix("pending/product-intakes");
  @SuppressWarnings("unchecked") ObjectProvider<FoodProductDirectUploadStorage> provider=mock(ObjectProvider.class);
  FoodProductDirectUploadStorage storage=mock(FoodProductDirectUploadStorage.class);
  TestFeedbackSubmissionRepository repository=mock(TestFeedbackSubmissionRepository.class);
  when(provider.getIfAvailable()).thenReturn(storage);
  UserEntity user=new UserEntity(); user.setId(9L); user.setEmail("tester@example.com");
  TestFeedbackSubmissionEntity entity=new TestFeedbackSubmissionEntity(); entity.setId(4L); entity.setUser(user);
  when(repository.findByIdAndUserEmailIgnoreCase(4L,"tester@example.com")).thenReturn(Optional.of(entity));
  when(storage.authorizeUpload(any(),any())).thenReturn(new FoodProductDirectUploadStorage.UploadAuthorization(
    URI.create("https://example.test/upload"),"PUT",Map.of("content-type","image/jpeg"),Instant.now().plusSeconds(600)));
  var service=new TestFeedbackScreenshotServiceImpl(properties,storageProperties,provider,repository);
  var result=service.authorize("tester@example.com","preview",4L,
    new TestFeedbackScreenshotUploadRequestDto("image/jpeg",120L,"a".repeat(64)));
  assertThat(result.method()).isEqualTo("PUT");
  assertThat(entity.getScreenshotStorageKey()).contains("/test-feedback/9/4/");
  assertThat(entity.getScreenshotExpiresAt()).isAfter(LocalDateTime.now());
  verify(repository).save(entity);
 }

 @Test void replacesPendingScreenshotAuthorizationWhenMobileRetries(){
  TestFeedbackProperties properties=new TestFeedbackProperties(); properties.setEnabled(true);
  FoodContributionStorageProperties storageProperties=new FoodContributionStorageProperties();
  storageProperties.getS3().setPrefix("pending/product-intakes");
  @SuppressWarnings("unchecked") ObjectProvider<FoodProductDirectUploadStorage> provider=mock(ObjectProvider.class);
  FoodProductDirectUploadStorage storage=mock(FoodProductDirectUploadStorage.class);
  TestFeedbackSubmissionRepository repository=mock(TestFeedbackSubmissionRepository.class);
  when(provider.getIfAvailable()).thenReturn(storage);
  UserEntity user=new UserEntity(); user.setId(9L); user.setEmail("tester@example.com");
  TestFeedbackSubmissionEntity entity=new TestFeedbackSubmissionEntity(); entity.setId(4L); entity.setUser(user);
  entity.setScreenshotStorageKey("pending/product-intakes/test-feedback/9/4/old.jpg");
  entity.setScreenshotContentType("image/jpeg"); entity.setScreenshotSizeBytes(100L);
  entity.setScreenshotSha256("b".repeat(64)); entity.setScreenshotExpiresAt(LocalDateTime.now().plusMinutes(5));
  when(repository.findByIdAndUserEmailIgnoreCase(4L,"tester@example.com")).thenReturn(Optional.of(entity));
  when(storage.authorizeUpload(any(),any())).thenReturn(new FoodProductDirectUploadStorage.UploadAuthorization(
    URI.create("https://example.test/retry-upload"),"PUT",Map.of("content-type","image/jpeg"),Instant.now().plusSeconds(600)));
  var service=new TestFeedbackScreenshotServiceImpl(properties,storageProperties,provider,repository);

  var result=service.authorize("tester@example.com","preview",4L,
    new TestFeedbackScreenshotUploadRequestDto("image/jpeg",120L,"a".repeat(64)));

  assertThat(result.uploadUrl()).isEqualTo("https://example.test/retry-upload");
  assertThat(entity.getScreenshotStorageKey()).contains("/test-feedback/9/4/").doesNotEndWith("old.jpg");
  verify(storage).delete("pending/product-intakes/test-feedback/9/4/old.jpg");
 }
}
