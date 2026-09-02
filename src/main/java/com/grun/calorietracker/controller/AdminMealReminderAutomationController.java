package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.AdminApprovalActionType;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/admin/meal-reminder-automation")
@PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
@Tag(name="Admin Meal Reminder Automation",description="Versioned policy, redacted preview and maker-checker release controls.")
public class AdminMealReminderAutomationController {
 private final AdminMealReminderAutomationService service;
 private final AdminApprovalService approvals;
 private final ObjectMapper mapper;

 @GetMapping("/config") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_READ')") public AdminMealReminderPolicyDto config(){return service.config();}
 @GetMapping("/history") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_READ')") public List<AdminMealReminderPolicyDto> history(@RequestParam(defaultValue="25")int size){return service.history(size);}
 @GetMapping("/decisions") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_READ')") public List<Map<String,Object>> decisions(@RequestParam(defaultValue="25")int size){return service.decisions(size);}
 @GetMapping("/summary") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_READ')") public Map<String,Object> summary(){return service.summary();}
 @PostMapping("/preview") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_READ')") @Operation(summary="Preview a synthetic scenario; never sends or reads a user's diary")
 public Map<String,Object> preview(@RequestBody @Valid AdminMealReminderPreviewRequestDto request){return service.preview(request);}
 @PostMapping("/drafts") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')") public AdminMealReminderPolicyDto create(@RequestBody @Valid AdminMealReminderPolicyRequestDto request,@AuthenticationPrincipal UserDetails user,HttpServletRequest http){return service.createDraft(request,user.getUsername(),cid(http));}
 @PutMapping("/drafts/{id}") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')") public AdminMealReminderPolicyDto update(@PathVariable Long id,@RequestBody @Valid AdminMealReminderPolicyRequestDto request,@AuthenticationPrincipal UserDetails user,HttpServletRequest http){return service.updateDraft(id,request,user.getUsername(),cid(http));}
 @PostMapping("/drafts/{id}/publish-request") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')") public ResponseEntity<AdminApprovalRequestDto> publish(@PathVariable Long id,@RequestBody @Valid AdminMealReminderReasonRequestDto request,@AuthenticationPrincipal UserDetails user,HttpServletRequest http){return approval(AdminApprovalActionType.MEAL_REMINDER_POLICY_PUBLISH,id,request,user,http);}
 @PostMapping("/history/{id}/rollback-request") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')") public ResponseEntity<AdminApprovalRequestDto> rollback(@PathVariable Long id,@RequestBody @Valid AdminMealReminderReasonRequestDto request,@AuthenticationPrincipal UserDetails user,HttpServletRequest http){return approval(AdminApprovalActionType.MEAL_REMINDER_POLICY_ROLLBACK,id,request,user,http);}
 @PostMapping("/definitions/{id}/publish-request") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')") public ResponseEntity<AdminApprovalRequestDto> definition(@PathVariable Long id,@RequestBody @Valid AdminMealReminderDefinitionPublishRequestDto request,@AuthenticationPrincipal UserDetails user,HttpServletRequest http){
  AdminApprovalCreateRequestDto dto=new AdminApprovalCreateRequestDto(AdminApprovalActionType.MEAL_REMINDER_DEFINITION_PUBLISH,id.toString(),mapper.valueToTree(request.definition()),request.reason());
  return ResponseEntity.accepted().body(approvals.create(user.getUsername(),dto,cid(http)));
 }
 @PostMapping("/reopen-request") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')") public ResponseEntity<AdminApprovalRequestDto> reopen(@RequestBody @Valid AdminMealReminderReasonRequestDto request,@AuthenticationPrincipal UserDetails user,HttpServletRequest http){return approval(AdminApprovalActionType.MEAL_REMINDER_REOPEN,service.config().id(),request,user,http);}
 @PostMapping("/test-accounts/{userId}/send-request") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')") public ResponseEntity<AdminApprovalRequestDto> testSend(@PathVariable Long userId,@RequestBody @Valid AdminMealReminderReasonRequestDto request,@AuthenticationPrincipal UserDetails user,HttpServletRequest http){return approval(AdminApprovalActionType.MEAL_REMINDER_TEST_SEND,userId,request,user,http);}
 @PostMapping("/emergency-stop") @PreAuthorize("hasAuthority('ADMIN_PERMISSION_GROWTH_MANAGE')") @Operation(summary="Immediate audited stop; reopening still requires approval")
 public AdminMealReminderPolicyDto stop(@RequestBody @Valid AdminMealReminderReasonRequestDto request,@AuthenticationPrincipal UserDetails user,HttpServletRequest http){return service.emergencyStop(request.reason(),user.getUsername(),cid(http));}

 private ResponseEntity<AdminApprovalRequestDto> approval(AdminApprovalActionType action,Long target,AdminMealReminderReasonRequestDto request,UserDetails user,HttpServletRequest http){
  AdminApprovalCreateRequestDto dto=new AdminApprovalCreateRequestDto(action,target.toString(),mapper.createObjectNode(),request.reason());
  return ResponseEntity.accepted().body(approvals.create(user.getUsername(),dto,cid(http)));
 }
 private String cid(HttpServletRequest request){Object value=request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);return value==null?request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER):value.toString();}
}
