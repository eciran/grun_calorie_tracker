package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.*;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.TestFeedbackSubmissionEntity;
import com.grun.calorietracker.entity.TestFeedbackScreenshotEventEntity;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.TestFeedbackSubmissionRepository;
import com.grun.calorietracker.repository.TestFeedbackScreenshotEventRepository;
import com.grun.calorietracker.service.TestFeedbackScreenshotService;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class TestFeedbackScreenshotServiceImpl implements TestFeedbackScreenshotService {
 private final TestFeedbackProperties properties;
 private final FoodContributionStorageProperties storageProperties;
 private final ObjectProvider<FoodProductDirectUploadStorage> storageProvider;
 private final TestFeedbackSubmissionRepository repository;
 private final TestFeedbackScreenshotEventRepository eventRepository;

 @Transactional
 public TestFeedbackScreenshotUploadDto authorize(String email,String environment,Long id,TestFeedbackScreenshotUploadRequestDto request){
  requireEnabled(environment); var entity=owned(id,email); var storage=storage();
  if(entity.getScreenshotStorageKey()!=null&&entity.getScreenshotDeletedAt()==null){
   if(entity.getScreenshotAttachedAt()!=null) throw new IllegalStateException("A screenshot is already attached to this feedback.");
   safeDelete(entity.getScreenshotStorageKey()); clear(entity);
  }
  String key=prefix()+"/test-feedback/"+entity.getUser().getId()+"/"+entity.getId()+"/"+UUID.randomUUID()+extension(request.contentType());
  var auth=storage.authorizeUpload(new FoodProductDirectUploadStorage.UploadObject(key,request.contentType().toLowerCase(Locale.ROOT),request.sizeBytes(),request.sha256().toLowerCase(Locale.ROOT)),properties.getScreenshotUploadUrlTtl());
  entity.setScreenshotStorageKey(key); entity.setScreenshotContentType(request.contentType().toLowerCase(Locale.ROOT));
  entity.setScreenshotSizeBytes(request.sizeBytes()); entity.setScreenshotSha256(request.sha256().toLowerCase(Locale.ROOT));
  entity.setScreenshotExpiresAt(LocalDateTime.now().plus(properties.getScreenshotPendingTtl())); repository.save(entity);
  event(entity,"AUTHORIZATION_CREATED","SUCCESS",request.sizeBytes(),null,request.contentType(),null,"storageKey="+key);
  return new TestFeedbackScreenshotUploadDto(auth.url().toString(),auth.method(),auth.requiredHeaders(),auth.expiresAt());
 }
 @Transactional(noRollbackFor=IllegalArgumentException.class)
 public void complete(String email,String environment,Long id){
  requireEnabled(environment); var entity=owned(id,email);
  if(entity.getScreenshotStorageKey()==null) throw new IllegalStateException("Screenshot upload was not authorized.");
  var screenshotStorage=storage();
  FoodProductDirectUploadStorage.StoredObject object;
  try{object=screenshotStorage.inspect(entity.getScreenshotStorageKey());}
  catch(RuntimeException exception){
   event(entity,"S3_OBJECT_INSPECTION_FAILED","ERROR",entity.getScreenshotSizeBytes(),null,entity.getScreenshotContentType(),"S3_OBJECT_UNAVAILABLE",exception.getClass().getSimpleName());
   clear(entity); throw new IllegalArgumentException("Uploaded screenshot could not be inspected.",exception);
  }
  long reportedSizeBytes=entity.getScreenshotSizeBytes();
  boolean contentTypeMatches=entity.getScreenshotContentType().equalsIgnoreCase(object.contentType());
  boolean sizeMatches=entity.getScreenshotSizeBytes().equals(object.sizeBytes());
  long maximumBytes=properties.getScreenshotMaxUploadBytes();
  boolean sizeWithinLimit=object.sizeBytes()>0&&object.sizeBytes()<=maximumBytes;
  boolean metadataShaMatches=object.sha256()!=null&&entity.getScreenshotSha256().equalsIgnoreCase(object.sha256());
  boolean contentShaMatches=false;
  if(sizeWithinLimit){
   try{
    byte[] content=screenshotStorage.readBounded(entity.getScreenshotStorageKey(),maximumBytes+1L);
    contentShaMatches=entity.getScreenshotSha256().equalsIgnoreCase(sha256(content));
   }catch(RuntimeException exception){
    event(entity,"S3_OBJECT_READ_FAILED","ERROR",entity.getScreenshotSizeBytes(),object.sizeBytes(),object.contentType(),"S3_READ_FAILED",exception.getClass().getSimpleName());
    safeDelete(entity.getScreenshotStorageKey()); clear(entity); throw new IllegalArgumentException("Uploaded screenshot could not be verified.",exception);
   }
  }
  if(!contentTypeMatches||!sizeWithinLimit||!contentShaMatches){
   log.warn("test_feedback_screenshot_validation_failed feedbackId={} reportedSizeBytes={} actualSizeBytes={} maxSizeBytes={} contentTypeMatches={} sizeMatches={} sizeWithinLimit={} metadataShaMatches={} contentShaMatches={}",
     id,entity.getScreenshotSizeBytes(),object.sizeBytes(),maximumBytes,contentTypeMatches,sizeMatches,sizeWithinLimit,metadataShaMatches,contentShaMatches);
   String errorCode=!sizeWithinLimit?"INVALID_ACTUAL_SIZE":!contentTypeMatches?"CONTENT_TYPE_MISMATCH":"CONTENT_SHA_MISMATCH";
   event(entity,"UPLOAD_VALIDATION_FAILED","ERROR",entity.getScreenshotSizeBytes(),object.sizeBytes(),object.contentType(),errorCode,
     "contentTypeMatches="+contentTypeMatches+", sizeMatches="+sizeMatches+", metadataShaMatches="+metadataShaMatches+", contentShaMatches="+contentShaMatches);
   safeDelete(entity.getScreenshotStorageKey()); clear(entity); throw new IllegalArgumentException("Uploaded screenshot metadata does not match the authorization.");
  }
  if(!sizeMatches){
   log.info("test_feedback_screenshot_reported_size_mismatch feedbackId={} content_sha_verified=true",id);
   entity.setScreenshotSizeBytes(object.sizeBytes());
  }
  if(!metadataShaMatches){
   log.info("test_feedback_screenshot_metadata_sha_missing_or_mismatched feedbackId={} content_sha_verified=true",id);
  }
  entity.setScreenshotAttachedAt(LocalDateTime.now()); entity.setScreenshotExpiresAt(LocalDateTime.now().plusDays(properties.getScreenshotRetentionDays())); repository.save(entity);
  event(entity,"UPLOAD_VALIDATED","SUCCESS",reportedSizeBytes,object.sizeBytes(),object.contentType(),null,"Content SHA-256 verified.");
 }
 @Transactional
 public TestFeedbackScreenshotReadDto authorizeAdminRead(Long id){
  var entity=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Test feedback not found."));
  if(entity.getScreenshotStorageKey()==null||entity.getScreenshotAttachedAt()==null||entity.getScreenshotDeletedAt()!=null||entity.getScreenshotExpiresAt().isBefore(LocalDateTime.now())) throw new ResourceNotFoundException("Feedback screenshot not found.");
  var auth=storage().authorizeRead(entity.getScreenshotStorageKey(),properties.getScreenshotAdminReadUrlTtl());
  event(entity,"ADMIN_READ_AUTHORIZED","SUCCESS",entity.getScreenshotSizeBytes(),entity.getScreenshotSizeBytes(),entity.getScreenshotContentType(),null,"Temporary read URL issued.");
  return new TestFeedbackScreenshotReadDto(auth.url().toString(),auth.expiresAt());
 }
 @Transactional
 @Scheduled(fixedDelayString="${grun.test-feedback.screenshot-cleanup-interval:1h}")
 public int cleanupExpired(){
  var rows=repository.findTop100ByScreenshotStorageKeyIsNotNullAndScreenshotDeletedAtIsNullAndScreenshotExpiresAtBefore(LocalDateTime.now()); int deleted=0;
  for(var entity:rows) try{safeDelete(entity.getScreenshotStorageKey());entity.setScreenshotDeletedAt(LocalDateTime.now());repository.save(entity);event(entity,"RETENTION_DELETED","SUCCESS",entity.getScreenshotSizeBytes(),entity.getScreenshotSizeBytes(),entity.getScreenshotContentType(),null,"Retention period expired.");deleted++;}catch(RuntimeException ignored){}
  return deleted;
 }
 private TestFeedbackSubmissionEntity owned(Long id,String email){return repository.findByIdAndUserEmailIgnoreCase(id,email).orElseThrow(()->new ResourceNotFoundException("Test feedback not found."));}
 private void requireEnabled(String environment){if(!properties.isEnabledFor(environment))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found");}
 private FoodProductDirectUploadStorage storage(){var value=storageProvider.getIfAvailable();if(value==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Private screenshot storage is not configured.");return value;}
 private String prefix(){String value=storageProperties.getS3().getPrefix();if(value==null||value.isBlank())return "pending/product-intakes";return value.trim().replaceAll("^/+|/+$","");}
 private String extension(String type){return switch(type.toLowerCase(Locale.ROOT)){case "image/png"->".png";case "image/webp"->".webp";default->".jpg";};}
 private void safeDelete(String key){storage().delete(key);}
 private void clear(TestFeedbackSubmissionEntity e){e.setScreenshotStorageKey(null);e.setScreenshotContentType(null);e.setScreenshotSizeBytes(null);e.setScreenshotSha256(null);e.setScreenshotAttachedAt(null);e.setScreenshotExpiresAt(null);repository.save(e);}
 private void event(TestFeedbackSubmissionEntity feedback,String eventType,String outcome,Long reportedSize,Long actualSize,String contentType,String errorCode,String detail){
  var event=new TestFeedbackScreenshotEventEntity(); event.setFeedback(feedback); event.setEventType(eventType); event.setOutcome(outcome);
  event.setReportedSizeBytes(reportedSize); event.setActualSizeBytes(actualSize); event.setContentType(contentType); event.setErrorCode(errorCode); event.setDetail(detail); eventRepository.save(event);
 }
 private String sha256(byte[] content){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
  catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is not available.",exception);}
 }
}
