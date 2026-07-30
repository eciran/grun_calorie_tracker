package com.grun.calorietracker.controller;
import com.grun.calorietracker.dto.*; import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException; import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.FoodProductReviewCaseService; import com.grun.calorietracker.service.FoodProductReviewSubmissionService; import jakarta.validation.constraints.*;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.data.domain.PageRequest; import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/products/review-cases") @RequiredArgsConstructor @Validated
public class FoodProductReviewCaseController {
 private final UserRepository users; private final FoodProductReviewCaseRepository cases; private final FoodProductReviewCaseService service; private final FoodProductReviewSubmissionService submissionService;
 @GetMapping public ResponseEntity<MyProductIntakePageDto> mine(@AuthenticationPrincipal UserDetails p,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="25") @Min(1) @Max(100) int size){
  UserEntity u=user(p); var result=cases.findAllBySubmittedByIdOrderByCreatedAtDesc(u.getId(),PageRequest.of(page,size));
  var rows=result.getContent().stream().map(this::dto).toList();
  return ResponseEntity.ok(new MyProductIntakePageDto(rows,result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages(),result.isFirst(),result.isLast()));}
 @PatchMapping("/{id}/withdraw") public ResponseEntity<MyProductIntakeDto> withdraw(@AuthenticationPrincipal UserDetails p,@PathVariable Long id){
  UserEntity u=user(p); var c=cases.findById(id).orElseThrow(()->new IllegalArgumentException("Review case not found."));
  if(c.getSubmittedBy()==null||!c.getSubmittedBy().getId().equals(u.getId())) throw new InvalidCredentialsException("Invalid credential");
  return ResponseEntity.ok(dto(service.transition(id,com.grun.calorietracker.enums.FoodProductReviewCaseStatus.WITHDRAWN,u.getEmail(),"Withdrawn by submitter")));}
 @PostMapping("/{id}/evidence") public ResponseEntity<FoodProductReviewSubmitResponseDto> resubmitEvidence(@AuthenticationPrincipal UserDetails p,@PathVariable Long id,@Valid @RequestBody FoodProductEvidenceResubmitRequestDto request){
  return ResponseEntity.ok(submissionService.resubmitEvidence(p.getUsername(),id,request.uploadSessionId()));} private UserEntity user(UserDetails p){return users.findByEmail(p.getUsername()).orElseThrow(()->new InvalidCredentialsException("Invalid credential"));}
 private MyProductIntakeDto dto(com.grun.calorietracker.entity.FoodProductReviewCaseEntity c){return new MyProductIntakeDto(c.getId(),c.getOriginalBarcode(),c.getMarketRegion(),c.getStatus(),c.getResolutionMode(),c.getReviewNote(),c.getCreatedAt(),c.getUpdatedAt());}
}
