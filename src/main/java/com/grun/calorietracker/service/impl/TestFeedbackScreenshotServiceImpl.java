package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.*;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.TestFeedbackSubmissionEntity;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.TestFeedbackSubmissionRepository;
import com.grun.calorietracker.service.TestFeedbackScreenshotService;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class TestFeedbackScreenshotServiceImpl implements TestFeedbackScreenshotService {
 private final TestFeedbackProperties properties;
 private final FoodContributionStorageProperties storageProperties;
 private final ObjectProvider<FoodProductDirectUploadStorage> storageProvider;
 private final TestFeedbackSubmissionRepository repository;

 @Transactional
 public TestFeedbackScreenshotUploadDto authorize(String email,String environment,Long id,TestFeedbackScreenshotUploadRequestDto request){
  requireEnabled(environment); var entity=owned(id,email); var storage=storage();
  if(entity.getScreenshotStorageKey()!=null&&entity.getScreenshotDeletedAt()==null) throw new IllegalStateException("A screenshot is already attached to this feedback.");
  String key=prefix()+"/test-feedback/"+entity.getUser().getId()+"/"+entity.getId()+"/"+UUID.randomUUID()+extension(request.contentType());
  var auth=storage.authorizeUpload(new FoodProductDirectUploadStorage.UploadObject(key,request.contentType().toLowerCase(Locale.ROOT),request.sizeBytes(),request.sha256().toLowerCase(Locale.ROOT)),properties.getScreenshotUploadUrlTtl());
  entity.setScreenshotStorageKey(key); entity.setScreenshotContentType(request.contentType().toLowerCase(Locale.ROOT));
  entity.setScreenshotSizeBytes(request.sizeBytes()); entity.setScreenshotSha256(request.sha256().toLowerCase(Locale.ROOT));
  entity.setScreenshotExpiresAt(LocalDateTime.now().plus(properties.getScreenshotPendingTtl())); repository.save(entity);
  return new TestFeedbackScreenshotUploadDto(auth.url().toString(),auth.method(),auth.requiredHeaders(),auth.expiresAt());
 }
 @Transactional
 public void complete(String email,String environment,Long id){
  requireEnabled(environment); var entity=owned(id,email);
  if(entity.getScreenshotStorageKey()==null) throw new IllegalStateException("Screenshot upload was not authorized.");
  var object=storage().inspect(entity.getScreenshotStorageKey());
  if(!entity.getScreenshotContentType().equalsIgnoreCase(object.contentType())||!entity.getScreenshotSizeBytes().equals(object.sizeBytes())||object.sha256()==null||!entity.getScreenshotSha256().equalsIgnoreCase(object.sha256())){
   safeDelete(entity.getScreenshotStorageKey()); clear(entity); throw new IllegalArgumentException("Uploaded screenshot metadata does not match the authorization.");
  }
  entity.setScreenshotAttachedAt(LocalDateTime.now()); entity.setScreenshotExpiresAt(LocalDateTime.now().plusDays(properties.getScreenshotRetentionDays())); repository.save(entity);
 }
 @Transactional(readOnly=true)
 public TestFeedbackScreenshotReadDto authorizeAdminRead(Long id){
  var entity=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Test feedback not found."));
  if(entity.getScreenshotStorageKey()==null||entity.getScreenshotAttachedAt()==null||entity.getScreenshotDeletedAt()!=null||entity.getScreenshotExpiresAt().isBefore(LocalDateTime.now())) throw new ResourceNotFoundException("Feedback screenshot not found.");
  var auth=storage().authorizeRead(entity.getScreenshotStorageKey(),properties.getScreenshotAdminReadUrlTtl());
  return new TestFeedbackScreenshotReadDto(auth.url().toString(),auth.expiresAt());
 }
 @Transactional
 @Scheduled(fixedDelayString="${grun.test-feedback.screenshot-cleanup-interval:1h}")
 public int cleanupExpired(){
  var rows=repository.findTop100ByScreenshotStorageKeyIsNotNullAndScreenshotDeletedAtIsNullAndScreenshotExpiresAtBefore(LocalDateTime.now()); int deleted=0;
  for(var entity:rows) try{safeDelete(entity.getScreenshotStorageKey());entity.setScreenshotDeletedAt(LocalDateTime.now());repository.save(entity);deleted++;}catch(RuntimeException ignored){}
  return deleted;
 }
 private TestFeedbackSubmissionEntity owned(Long id,String email){return repository.findByIdAndUserEmailIgnoreCase(id,email).orElseThrow(()->new ResourceNotFoundException("Test feedback not found."));}
 private void requireEnabled(String environment){if(!properties.isEnabledFor(environment))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found");}
 private FoodProductDirectUploadStorage storage(){var value=storageProvider.getIfAvailable();if(value==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Private screenshot storage is not configured.");return value;}
 private String prefix(){String value=storageProperties.getS3().getPrefix();if(value==null||value.isBlank())return "pending/product-intakes";return value.trim().replaceAll("^/+|/+$","");}
 private String extension(String type){return switch(type.toLowerCase(Locale.ROOT)){case "image/png"->".png";case "image/webp"->".webp";default->".jpg";};}
 private void safeDelete(String key){storage().delete(key);}
 private void clear(TestFeedbackSubmissionEntity e){e.setScreenshotStorageKey(null);e.setScreenshotContentType(null);e.setScreenshotSizeBytes(null);e.setScreenshotSha256(null);e.setScreenshotAttachedAt(null);e.setScreenshotExpiresAt(null);repository.save(e);}
}