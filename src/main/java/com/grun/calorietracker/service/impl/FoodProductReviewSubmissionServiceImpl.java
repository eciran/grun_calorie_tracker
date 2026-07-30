package com.grun.calorietracker.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import com.grun.calorietracker.service.support.ProductIntakeRolloutPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
@ConditionalOnProperty(prefix="grun.food-contribution-storage",name="provider",havingValue="S3")
public class FoodProductReviewSubmissionServiceImpl implements FoodProductReviewSubmissionService {
 private final UserRepository users; private final FoodProductUploadSessionRepository sessions;
 private final FoodProductReviewCaseAssetRepository assets; private final FoodProductReviewCaseRepository reviewCases; private final FoodProductReviewCaseService cases;
 private final ObjectMapper json;
 private final ProductIntakeRolloutPolicy rolloutPolicy;
 @Override @Transactional
 public synchronized FoodProductReviewSubmitResponseDto submit(String email,String sessionId,FoodProductReviewSubmitRequestDto r){
  UserEntity user=users.findByEmail(email).orElseThrow(()->new InvalidCredentialsException("Invalid credential"));
  rolloutPolicy.requireAvailable(user);
  var session=sessions.findById(sessionId).orElseThrow(()->new IllegalArgumentException("Upload session was not found."));
  if(!session.getCreatedBy().getId().equals(user.getId())) throw new InvalidCredentialsException("Invalid credential");
  if(session.getStatus()!=FoodProductUploadSessionStatus.FINALIZED) throw new RequestConflictException("Evidence must be finalized.");
  var evidence=assets.findAllByUploadSessionIdOrderByAssetTypeAsc(sessionId);
  if(evidence.size()!=2||evidence.stream().anyMatch(a->a.getUploadState()!=FoodProductAssetUploadState.VERIFIED))
   throw new RequestConflictException("Two verified evidence assets are required.");
  var review=cases.finalizeCase(new FoodProductReviewCaseCommand(r.idempotencyKey(),FoodProductReviewCaseSource.USER_OCR,
   sessionId,user,r.barcode(),r.marketRegion(),null,r.productName(),r.brand(),r.calories(),r.protein(),r.fat(),r.carbs(),
   r.fiber(),r.sugar(),r.sodium(),r.nutritionBasis(),r.riskLevel(),1,encode(r.submittedFields()),
   encode(r.fieldConfidence()),encode(r.correctionSummary()),r.consentVersion(),r.temporaryEvidenceAllowed(),r.publicMediaAllowed()));
  evidence.forEach(a->{if(a.getReviewCase()!=null&&!a.getReviewCase().getId().equals(review.getId()))
   throw new RequestConflictException("Evidence is already attached."); a.setReviewCase(review);});
  assets.saveAll(evidence);
  return new FoodProductReviewSubmitResponseDto(review.getId(),review.getStatus(),sessionId);
 }
 @Override @Transactional
 public synchronized FoodProductReviewSubmitResponseDto resubmitEvidence(String email,Long caseId,String sessionId){
  UserEntity user=users.findByEmail(email).orElseThrow(()->new InvalidCredentialsException("Invalid credential"));
  rolloutPolicy.requireAvailable(user);
  var review=reviewCases.findById(caseId).orElseThrow(()->new IllegalArgumentException("Review case was not found."));
  if(review.getSubmittedBy()==null||!review.getSubmittedBy().getId().equals(user.getId())) throw new InvalidCredentialsException("Invalid credential");
  if(review.getStatus()!=FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION) throw new RequestConflictException("Review case is not waiting for updated evidence.");
  var session=sessions.findById(sessionId).orElseThrow(()->new IllegalArgumentException("Upload session was not found."));
  if(!session.getCreatedBy().getId().equals(user.getId())) throw new InvalidCredentialsException("Invalid credential");
  if(session.getStatus()!=FoodProductUploadSessionStatus.FINALIZED) throw new RequestConflictException("Evidence must be finalized.");
  var evidence=assets.findAllByUploadSessionIdOrderByAssetTypeAsc(sessionId);
  if(evidence.size()!=2||evidence.stream().anyMatch(a->a.getUploadState()!=FoodProductAssetUploadState.VERIFIED)) throw new RequestConflictException("Two verified evidence assets are required.");
  if(evidence.stream().anyMatch(a->a.getReviewCase()!=null)) throw new RequestConflictException("Evidence is already attached.");
  assets.expireReviewCaseAssets(caseId,java.time.LocalDateTime.now());
  evidence.forEach(a->a.setReviewCase(review)); assets.saveAll(evidence);
  var submitted=cases.transition(caseId,FoodProductReviewCaseStatus.SUBMITTED,email,"Updated evidence submitted by user");
  return new FoodProductReviewSubmitResponseDto(submitted.getId(),submitted.getStatus(),sessionId);
 } private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid review metadata.",e);}}
}
