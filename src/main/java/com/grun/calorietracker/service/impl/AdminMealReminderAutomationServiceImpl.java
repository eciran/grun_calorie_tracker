package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.*;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.reminder.*;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminMealReminderAutomationServiceImpl implements AdminMealReminderAutomationService {
 private final MealReminderPolicyRepository policies;
 private final MealReminderDryRunDecisionRepository decisions;
 private final MealReminderOccurrenceRepository occurrences;
 private final MealReminderInteractionRepository interactions;
 private final MealReminderDeliveryAttemptRepository attempts;
 private final MealReminderOutboxRepository outboxes;
 private final MealReminderAdminTestSendRepository testSends;
 private final UserRepository users;
 private final NotificationDefinitionRepository definitions;
 private final MealReminderCandidateWorker candidateWorker;
 private final MealReminderDeliveryProperties deployment;
 private final PushProperties push;
 private final AdminAuditService audit;
 private final Clock analyticsClock;

 @Override @Transactional(readOnly=true) public AdminMealReminderPolicyDto config(){return dto(active());}

 @Override @Transactional public AdminMealReminderPolicyDto createDraft(AdminMealReminderPolicyRequestDto r,String admin,String cid){
  validate(r); Instant now=analyticsClock.instant(); MealReminderPolicyEntity e=new MealReminderPolicyEntity();
  e.setPolicyVersion("meal-reminder-"+UUID.randomUUID()); e.setStatus(MealReminderPolicyStatus.DRAFT);
  apply(e,r); e.setCreatedBy(admin);e.setUpdatedBy(admin);e.setCreatedAt(now);e.setUpdatedAt(now);
  e=policies.save(e); audit.record(admin,AdminAuditActionType.MEAL_REMINDER_POLICY_DRAFT,
   AdminAuditTargetType.MEAL_REMINDER_AUTOMATION,e.getId().toString(),null,safeAudit(e),cid); return dto(e);
 }

 @Override @Transactional public AdminMealReminderPolicyDto updateDraft(Long id,AdminMealReminderPolicyRequestDto r,String admin,String cid){
  validate(r); MealReminderPolicyEntity e=policies.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Draft not found."));
  if(e.getStatus()!=MealReminderPolicyStatus.DRAFT)throw new IllegalArgumentException("Only drafts can be edited.");
  if(r.version()==null||!r.version().equals(e.getVersion()))throw new OptimisticLockException("Policy version conflict.");
  Map<String,Object> before=safeAudit(e);apply(e,r);e.setUpdatedBy(admin);e.setUpdatedAt(analyticsClock.instant());
  e=policies.save(e);audit.record(admin,AdminAuditActionType.MEAL_REMINDER_POLICY_DRAFT,
   AdminAuditTargetType.MEAL_REMINDER_AUTOMATION,id.toString(),before,safeAudit(e),cid);return dto(e);
 }

 @Override @Transactional(readOnly=true) public List<AdminMealReminderPolicyDto> history(int size){
  return policies.findAllByOrderByCreatedAtDesc(PageRequest.of(0,Math.min(Math.max(size,1),50))).stream().map(this::dto).toList();
 }

 @Override public Map<String,Object> preview(AdminMealReminderPreviewRequestDto r){
  Instant at=r.evaluatedAt(); Map<MealReminderContract.Meal,MealReminderContract.MealState> states=new EnumMap<>(MealReminderContract.Meal.class);
  for(var meal:MealReminderContract.Meal.values())states.put(meal,r.mealStates().getOrDefault(meal,MealReminderContract.MealState.UNKNOWN));
  Map<MealReminderContract.Meal,DailyMealReminderSnapshot.MealTotals> totals=new EnumMap<>(MealReminderContract.Meal.class);
  for(var meal:MealReminderContract.Meal.values())totals.put(meal,new DailyMealReminderSnapshot.MealTotals(0,0,0,0));
  Double remaining=r.targetCalories()==null||r.consumedCalories()==null?null:r.targetCalories()-r.consumedCalories();
  DailyMealReminderSnapshot snapshot=new DailyMealReminderSnapshot(null,at,r.localDate(),r.timeZone(),null,null,
   "ADMIN_SCENARIO","ADMIN_SCENARIO",r.targetCalories(),r.consumedCalories(),0d,r.consumedCalories(),remaining,
   true,r.targetCalories()!=null&&r.targetCalories()>0,totals,states,r.fastingActive()?DailyMealReminderSnapshot.ActivityState.ACTIVE:DailyMealReminderSnapshot.ActivityState.INACTIVE,
   r.pushEnabled(),r.mealRemindersEnabled(),null,null);
  MealReminderPolicy source=toPolicy(active());
  MealReminderPolicy simulation=new MealReminderPolicy(source.version(),MealReminderContract.Mode.DRY_RUN,true,source.kcalEnabled(),source.mealTimes(),source.slotAge(),source.maxDailyOccurrences(),source.maxRollingOccurrences(),source.maxDailyCatchups(),source.minimumReminderGap(),source.routineReminderGap(),source.defaultQuietStart(),source.defaultQuietEnd());
  MealReminderDecision d=new MealReminderDecisionEngine(Clock.fixed(at,ZoneOffset.UTC)).evaluate(snapshot,simulation,MealReminderRuntimeState.eligible(r.language()));
  return decisionMap(d);
 }

 @Override @Transactional(readOnly=true) public List<Map<String,Object>> decisions(int size){
  return decisions.findAllByOrderByEvaluatedAtDesc(PageRequest.of(0,Math.min(Math.max(size,1),100))).stream().map(e->{
   Map<String,Object> value=new LinkedHashMap<>();value.put("id",e.getId());value.put("subjectRef",e.getSubjectRef());
   value.put("evaluatedAt",e.getEvaluatedAt());value.put("policyVersion",e.getPolicyVersion());value.put("candidate",e.getCandidate());
   value.put("slot",e.getSlot());value.put("shouldSend",e.isShouldSend());value.put("reason",e.getReason());value.put("kcalReason",e.getKcalReason());return value;
  }).toList();
 }

 @Override @Transactional(readOnly=true) public Map<String,Object> summary(){
  MealReminderPolicyEntity active=active();long accepted=attempts.countByStatus(MealReminderAttemptStatus.PROVIDER_ACCEPTED)
   +attempts.countByStatus(MealReminderAttemptStatus.RECEIPT_DELIVERED)+attempts.countByStatus(MealReminderAttemptStatus.RECEIPT_FAILED);
  long opens=interactions.countByEventType(MealReminderInteractionType.OPEN);long correlatedLogs=interactions.countByEventType(MealReminderInteractionType.MEAL_LOG_CONVERSION);
  long conversions=interactions.countDistinctOccurrencesByEventType(MealReminderInteractionType.MEAL_LOG_CONVERSION);
  long optOuts=interactions.countByEventType(MealReminderInteractionType.OPT_OUT);Map<String,Object> value=new LinkedHashMap<>();
  value.put("mode",effectiveMode(active));value.put("deploymentGateEnabled",deployment.isDeliveryEnabled());value.put("pushGateEnabled",push.isEnabled());
  value.put("emergencyStopped",active.isEmergencyStopped());value.put("occurrenceCount",occurrences.count());value.put("outboxCount",outboxes.count());
  value.put("providerAcceptedCount",accepted);value.put("receiptDeliveredCount",attempts.countByStatus(MealReminderAttemptStatus.RECEIPT_DELIVERED));
  value.put("receiptFailedCount",attempts.countByStatus(MealReminderAttemptStatus.RECEIPT_FAILED));value.put("uniqueOpenCount",opens);
  value.put("mealLogWithin2hCount",conversions);value.put("correlatedMealLogCount",correlatedLogs);value.put("reminderOptOutCount",optOuts);
  value.put("uniqueOpenRate",ratio(opens,accepted));value.put("mealLogWithin2hRate",ratio(conversions,opens));value.put("reminderOptOutRate",ratio(optOuts,accepted));
  value.put("measurementNote","Meal logs within two hours are correlated with an open; this does not claim causality.");return value;
 }

 @Override @Transactional public AdminMealReminderPolicyDto emergencyStop(String reason,String admin,String cid){
  MealReminderPolicyEntity e=policies.findByIdForUpdate(active().getId()).orElseThrow();
  e.setEmergencyStopped(true);e.setStopReason(reason.trim());e.setUpdatedBy(admin);e.setUpdatedAt(analyticsClock.instant());policies.save(e);
  audit.record(admin,AdminAuditActionType.MEAL_REMINDER_EMERGENCY_STOP,AdminAuditTargetType.MEAL_REMINDER_AUTOMATION,
   e.getId().toString(),null,Map.of("reason",e.getStopReason()),cid);return dto(e);
 }

 @Override @Transactional public void publishApproved(Long id,String checker,String cid,boolean rollback){
  MealReminderPolicyEntity target=policies.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Policy not found."));
  if(target.getStatus()==MealReminderPolicyStatus.ACTIVE)return;
  MealReminderPolicyEntity old=active();old.setStatus(MealReminderPolicyStatus.ARCHIVED);old.setUpdatedAt(analyticsClock.instant());policies.saveAndFlush(old);
  target.setStatus(MealReminderPolicyStatus.ACTIVE);target.setPublishedAt(analyticsClock.instant());target.setUpdatedAt(target.getPublishedAt());target.setUpdatedBy(checker);policies.save(target);
  audit.record(checker,rollback?AdminAuditActionType.MEAL_REMINDER_POLICY_ROLLBACK:AdminAuditActionType.MEAL_REMINDER_POLICY_PUBLISH,
   AdminAuditTargetType.MEAL_REMINDER_AUTOMATION,id.toString(),Map.of("previousPolicy",old.getPolicyVersion()),safeAudit(target),cid);
 }

 @Override @Transactional public void reopenApproved(Long id,String checker,String cid){
  MealReminderPolicyEntity e=policies.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Policy not found."));
  if(e.getStatus()!=MealReminderPolicyStatus.ACTIVE)throw new IllegalArgumentException("Only active policy can be reopened.");
  e.setEmergencyStopped(false);e.setStopReason(null);e.setUpdatedBy(checker);e.setUpdatedAt(analyticsClock.instant());policies.save(e);
  audit.record(checker,AdminAuditActionType.MEAL_REMINDER_REOPEN,AdminAuditTargetType.MEAL_REMINDER_AUTOMATION,id.toString(),null,Map.of("reopened",true),cid);
 }

 @Override @Transactional public void publishDefinitionApproved(Long id,AdminNotificationDefinitionRequestDto r,String checker,String cid){
  NotificationDefinitionEntity e=definitions.findById(id).orElseThrow(()->new IllegalArgumentException("Definition not found."));
  String key=r.getKey()==null?"":r.getKey().trim().toLowerCase(Locale.ROOT);
  if(!e.isProtectedDefinition()||!e.getKey().startsWith("meal_reminder_")||!e.getKey().equals(key))throw new IllegalArgumentException("Only matching protected meal reminder definitions can be published.");
  if(!r.isEnabled()||r.getChannel()!=NotificationCampaignChannel.IN_APP_AND_PUSH||!"diary".equals(r.getTargetRoute())||!"INFO".equals(r.getSeverity()))throw new IllegalArgumentException("Meal reminder route, channel, severity and enabled state are protected.");
  validateCopy(key,r.getTitleEn(),r.getMessageEn(),r.getTitleTr(),r.getMessageTr());
  e.setDisplayName(r.getDisplayName().trim());e.setDescription(r.getDescription()==null?null:r.getDescription().trim());e.setTitleEn(r.getTitleEn().trim());e.setMessageEn(r.getMessageEn().trim());e.setTitleTr(r.getTitleTr().trim());e.setMessageTr(r.getMessageTr().trim());e.setUpdatedBy(checker);e.setUpdatedAt(LocalDateTime.ofInstant(analyticsClock.instant(),ZoneOffset.UTC));definitions.save(e);
  audit.record(checker,AdminAuditActionType.MEAL_REMINDER_DEFINITION_PUBLISH,AdminAuditTargetType.MEAL_REMINDER_AUTOMATION,id.toString(),null,Map.of("key",key,"route","diary"),cid);
 }

 @Override @Transactional public void testSendApproved(Long userId,String checker,String cid){
  MealReminderPolicyEntity active=active();Set<Long> pilot=parsePilot(active.getPilotUserIds());
  if(!pilot.contains(userId))throw new IllegalArgumentException("Test sends are limited to the approved pilot cohort.");
  UserEntity user=users.findById(userId).orElseThrow(()->new IllegalArgumentException("Test user not found."));
  if(!Boolean.TRUE.equals(user.getPushNotificationsEnabled())||!Boolean.TRUE.equals(user.getMealRemindersEnabled()))throw new IllegalArgumentException("User notification preferences are disabled.");
  Instant now=analyticsClock.instant();if(testSends.countByUserIdAndRequestedAtAfter(userId,now.minus(Duration.ofHours(1)))>=1)throw new IllegalArgumentException("Test send rate limit exceeded.");
  testSends.deleteByRequestedAtBefore(now.minus(Duration.ofDays(30)));MealReminderAdminTestSendEntity log=new MealReminderAdminTestSendEntity();log.setUser(user);log.setRequestedBy(checker);log.setRequestedAt(now);testSends.save(log);
  candidateWorker.evaluate(userId,now);audit.record(checker,AdminAuditActionType.MEAL_REMINDER_TEST_SEND,AdminAuditTargetType.MEAL_REMINDER_AUTOMATION,userId.toString(),null,Map.of("approvedTestAccount",true),cid);
 }

 private MealReminderPolicyEntity active(){return policies.findFirstByStatus(MealReminderPolicyStatus.ACTIVE).orElseThrow(()->new IllegalStateException("Active meal reminder policy is missing."));}
 private void validate(AdminMealReminderPolicyRequestDto r){toPolicy(r,"validation");if(r.mode()==MealReminderContract.Mode.PILOT&&r.pilotUserIds().isEmpty())throw new IllegalArgumentException("PILOT requires at least one approved user.");}
 private void apply(MealReminderPolicyEntity e,AdminMealReminderPolicyRequestDto r){e.setMode(r.mode());e.setKcalEnabled(r.kcalEnabled());e.setBreakfastTime(r.breakfastTime());e.setLunchTime(r.lunchTime());e.setDinnerTime(r.dinnerTime());e.setSlotAgeMinutes(r.slotAgeMinutes());e.setMaxDaily(r.maxDaily());e.setMaxRolling(r.maxRolling());e.setMaxCatchups(r.maxCatchups());e.setMinimumGapMinutes(r.minimumGapMinutes());e.setRoutineGapMinutes(r.routineGapMinutes());e.setQuietStart(r.quietStart());e.setQuietEnd(r.quietEnd());e.setPilotUserIds(r.pilotUserIds().stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));}
 private MealReminderPolicy toPolicy(MealReminderPolicyEntity e){return new MealReminderPolicy(e.getPolicyVersion(),effectiveMode(e),deployment.isDeliveryEnabled(),e.isKcalEnabled(),Map.of(MealReminderContract.Meal.BREAKFAST,e.getBreakfastTime(),MealReminderContract.Meal.LUNCH,e.getLunchTime(),MealReminderContract.Meal.DINNER,e.getDinnerTime()),Duration.ofMinutes(e.getSlotAgeMinutes()),e.getMaxDaily(),e.getMaxRolling(),e.getMaxCatchups(),Duration.ofMinutes(e.getMinimumGapMinutes()),Duration.ofMinutes(e.getRoutineGapMinutes()),e.getQuietStart(),e.getQuietEnd());}
 private MealReminderPolicy toPolicy(AdminMealReminderPolicyRequestDto r,String version){return new MealReminderPolicy(version,r.mode(),deployment.isDeliveryEnabled(),r.kcalEnabled(),Map.of(MealReminderContract.Meal.BREAKFAST,r.breakfastTime(),MealReminderContract.Meal.LUNCH,r.lunchTime(),MealReminderContract.Meal.DINNER,r.dinnerTime()),Duration.ofMinutes(r.slotAgeMinutes()),r.maxDaily(),r.maxRolling(),r.maxCatchups(),Duration.ofMinutes(r.minimumGapMinutes()),Duration.ofMinutes(r.routineGapMinutes()),r.quietStart(),r.quietEnd());}
 private MealReminderContract.Mode effectiveMode(MealReminderPolicyEntity e){return e.isEmergencyStopped()?MealReminderContract.Mode.OFF:e.getMode();}
 private Set<Long> parsePilot(String value){if(value==null||value.isBlank())return Set.of();Set<Long> result=new LinkedHashSet<>();for(String item:value.split(","))result.add(Long.valueOf(item));return Set.copyOf(result);}
 private AdminMealReminderPolicyDto dto(MealReminderPolicyEntity e){return new AdminMealReminderPolicyDto(e.getId(),e.getVersion(),e.getPolicyVersion(),e.getStatus(),effectiveMode(e),deployment.isDeliveryEnabled(),push.isEnabled(),e.isKcalEnabled(),e.getBreakfastTime(),e.getLunchTime(),e.getDinnerTime(),e.getSlotAgeMinutes(),e.getMaxDaily(),e.getMaxRolling(),e.getMaxCatchups(),e.getMinimumGapMinutes(),e.getRoutineGapMinutes(),e.getQuietStart(),e.getQuietEnd(),parsePilot(e.getPilotUserIds()),e.isEmergencyStopped(),e.getStopReason(),e.getUpdatedBy(),e.getUpdatedAt(),e.getPublishedAt());}
 private Map<String,Object> safeAudit(MealReminderPolicyEntity e){return Map.of("policyVersion",e.getPolicyVersion(),"mode",effectiveMode(e),"pilotSize",parsePilot(e.getPilotUserIds()).size(),"maxDaily",e.getMaxDaily());}
 private Map<String,Object> decisionMap(MealReminderDecision d){Map<String,Object> value=new LinkedHashMap<>();value.put("policyVersion",d.policyVersion());value.put("evaluatedAt",d.evaluatedAt());value.put("localDate",d.localDate());value.put("candidate",d.candidate());value.put("slot",d.slot());value.put("shouldSend",d.shouldSend());value.put("reason",d.reason());value.put("kcalReason",d.kcalReason());value.put("message",d.message());value.put("copy",d.renderedCopy());return value;}
 private void validateCopy(String key,String... values){for(String value:values)if(value==null||value.isBlank())throw new IllegalArgumentException("TR and EN title/body are required.");Set<String> allowed=key.equals("meal_reminder_dinner_kcal")?Set.of("remainingKcal"):Set.of();java.util.regex.Pattern p=java.util.regex.Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");for(String value:values){var m=p.matcher(value);while(m.find())if(!allowed.contains(m.group(1)))throw new IllegalArgumentException("Unsupported meal reminder placeholder: "+m.group(1));}if(key.equals("meal_reminder_dinner_kcal")&&!contains(values,"{remainingKcal}"))throw new IllegalArgumentException("Dinner kcal copy requires remainingKcal.");}
 private boolean contains(String[] values,String token){for(String value:values)if(value.contains(token))return true;return false;}
 private double ratio(long numerator,long denominator){return denominator<=0?0d:Math.round((10000d*numerator/denominator))/100d;}
}
