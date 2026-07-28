package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdvancedFastingReminderServiceImpl implements AdvancedFastingReminderService {
 private final FastingProgramRepository programRepository;
 private final FastingProgramOccurrenceRepository occurrenceRepository;
 private final FastingReminderDeliveryRepository deliveryRepository;
 private final AdvancedFastingReminderSettingsRepository settingsRepository;
 private final UserRepository userRepository;
 private final AdvancedFastingExecutionService executionService;
 private final NotificationRepository notificationRepository;
 private final PushDeliveryService pushDeliveryService;
 private final UserTimeZoneSupport timeZoneSupport;
 @Value("${grun.fasting.advanced-reminders.enabled:true}") private boolean enabled;
 @Autowired(required=false) private AdvancedFastingGovernanceService governanceService;
 @Autowired(required=false) private MeterRegistry meterRegistry;

 @Override @Scheduled(fixedDelayString="${grun.fasting.advanced-reminders.scan-interval-ms:300000}") @Transactional
 public int createDueReminderNotifications(){
  if(!enabled||!Boolean.TRUE.equals(config().getReminderEnabled()))return 0; int delivered=0;
  for(FastingProgramEntity program:programRepository.findAllByStatus(FastingProgramStatus.ACTIVE)){
   UserEntity user=program.getUser(); LocalDate today=timeZoneSupport.today(user); LocalDateTime now=timeZoneSupport.now(user);
   for(int offset=-1;offset<=1;offset++){
    LocalDate date=today.plusDays(offset);
    if((program.getEffectiveFrom()!=null&&date.isBefore(program.getEffectiveFrom()))||(program.getEffectiveUntil()!=null&&date.isAfter(program.getEffectiveUntil())))continue;
    try{ executionService.getOrCreate(user.getEmail(),date); }catch(RuntimeException ignored){ increment("grun.fasting.scheduler.failures"); continue; }
    var occurrence=occurrenceRepository.findByUserAndOccurrenceDate(user,date).orElse(null);
    if(occurrence!=null) delivered+=dispatchDue(occurrence,now);
   }
  }
  return delivered;
 }
 int dispatchDue(FastingProgramOccurrenceEntity occurrence,LocalDateTime now){
  if(occurrence.getStatus()==FastingOccurrenceStatus.SKIPPED)return 0;
  AdvancedFastingReminderSettingsEntity settings=settings(occurrence.getUser());
  if(!Boolean.TRUE.equals(settings.getEnabled()))return 0;
  List<ReminderEvent> events=events(occurrence,settings);
  int sent=0; for(ReminderEvent event:events){ if(isDue(event,now)&&deliver(occurrence,event,now,settings))sent++; } return sent;
 }
 private List<ReminderEvent> events(FastingProgramOccurrenceEntity occurrence,AdvancedFastingReminderSettingsEntity settings){
  List<ReminderEvent> result=new ArrayList<>(); AdvancedFastingOperationsConfigEntity config=config();
  if(occurrence.getRuleType()==FastingDayRuleType.FAST&&occurrence.getPlannedStartAt()!=null){
   if(Boolean.TRUE.equals(settings.getPreStartEnabled())) result.add(new ReminderEvent(AdvancedFastingReminderType.PRE_START,occurrence.getPlannedStartAt().minusMinutes(settings.getPreStartMinutes())));
   if(Boolean.TRUE.equals(settings.getStartEnabled())) result.add(new ReminderEvent(AdvancedFastingReminderType.START,occurrence.getPlannedStartAt()));
   FastingSessionEntity session=occurrence.getFastingSession();
   if(session==null){
    if(Boolean.TRUE.equals(settings.getMissedPlanEnabled())) result.add(new ReminderEvent(AdvancedFastingReminderType.MISSED_PLAN,occurrence.getPlannedStartAt().plusMinutes(config.getMissedPlanMinutes())));
   } else if(session.getStatus()==FastingSessionStatus.ACTIVE&&session.getTargetEndAt()!=null&&Boolean.TRUE.equals(settings.getNearingCompletionEnabled())) result.add(new ReminderEvent(AdvancedFastingReminderType.NEARING_COMPLETION,session.getTargetEndAt().minusMinutes(settings.getNearingCompletionMinutes())));
   else if(session.getStatus()==FastingSessionStatus.COMPLETED&&session.getEndedAt()!=null&&Boolean.TRUE.equals(settings.getCompletionEnabled())) result.add(new ReminderEvent(AdvancedFastingReminderType.COMPLETION,session.getEndedAt()));
  } else if(occurrence.getRuleType()==FastingDayRuleType.REDUCED_CALORIE){
   if(Boolean.TRUE.equals(settings.getStartEnabled())) result.add(new ReminderEvent(AdvancedFastingReminderType.START,occurrence.getOccurrenceDate().atTime(8,0)));
   if(occurrence.getStatus()==FastingOccurrenceStatus.COMPLETED&&Boolean.TRUE.equals(settings.getCompletionEnabled())) result.add(new ReminderEvent(AdvancedFastingReminderType.COMPLETION,occurrence.getOccurrenceDate().plusDays(1).atTime(0,5)));
  }
  return result;
 }
 private boolean isDue(ReminderEvent event,LocalDateTime now){ return !event.at().isAfter(now)&&!event.at().isBefore(now.minusHours(36)); }
 private boolean deliver(FastingProgramOccurrenceEntity occurrence,ReminderEvent event,LocalDateTime now,AdvancedFastingReminderSettingsEntity settings){
  String key=occurrence.getProgram().getId()+":"+occurrence.getProgramVersion().getVersionNumber()+":"+occurrence.getOccurrenceDate()+":"+event.type();
  FastingReminderDeliveryEntity delivery=deliveryRepository.findByOccurrenceKey(key).orElseGet(()->newDelivery(occurrence,event,key));
  if(delivery.getStatus()==FastingReminderDeliveryStatus.SENT||delivery.getStatus()==FastingReminderDeliveryStatus.SUPPRESSED)return false;
  if(delivery.getStatus()==FastingReminderDeliveryStatus.FAILED&&delivery.getAttemptCount()>=config().getMaxRetryAttempts())return false;
  if(delivery.getNextAttemptAt()!=null&&delivery.getNextAttemptAt().isAfter(now))return false;
  UserEntity user=occurrence.getUser();
  if(!Boolean.TRUE.equals(user.getPushNotificationsEnabled())||!Boolean.TRUE.equals(user.getFastingRemindersEnabled())||!Boolean.TRUE.equals(settings.getEnabled())){
   delivery.setStatus(FastingReminderDeliveryStatus.SUPPRESSED); delivery.setLastError("Notification preference disabled"); deliveryRepository.save(delivery); return false;
  }
  if(inQuietHours(user,now.toLocalTime())){
   delivery.setStatus(FastingReminderDeliveryStatus.DEFERRED); delivery.setNextAttemptAt(nextQuietEnd(user,now)); deliveryRepository.save(delivery); return false;
  }
  try{
   NotificationEntity notification=delivery.getNotification();
   if(notification==null){ notification=notificationRepository.save(notification(occurrence,event,user,now)); delivery.setNotification(notification); }
   PushDeliveryResultDto result=pushDeliveryService.deliver(notification); delivery.setAttemptCount(delivery.getAttemptCount()+1);
   if(result.getFailed()>0){ increment("grun.fasting.push.failures"); delivery.setStatus(FastingReminderDeliveryStatus.FAILED); delivery.setLastError("Push provider delivery failed"); delivery.setNextAttemptAt(now.plusMinutes(retryMinutes(delivery.getAttemptCount()))); }
   else { delivery.setStatus(FastingReminderDeliveryStatus.SENT); delivery.setDeliveredAt(now); delivery.setNextAttemptAt(null); delivery.setLastError(null); }
   deliveryRepository.save(delivery); return delivery.getStatus()==FastingReminderDeliveryStatus.SENT;
  }catch(RuntimeException ex){ increment("grun.fasting.push.failures"); delivery.setAttemptCount(delivery.getAttemptCount()+1); delivery.setStatus(FastingReminderDeliveryStatus.FAILED); delivery.setLastError(limit(ex.getMessage())); delivery.setNextAttemptAt(now.plusMinutes(retryMinutes(delivery.getAttemptCount()))); deliveryRepository.save(delivery); return false; }
 }
 private FastingReminderDeliveryEntity newDelivery(FastingProgramOccurrenceEntity occurrence,ReminderEvent event,String key){ FastingReminderDeliveryEntity d=new FastingReminderDeliveryEntity(); d.setOccurrence(occurrence); d.setOccurrenceKey(key); d.setReminderType(event.type()); d.setScheduledFor(event.at()); d.setStatus(FastingReminderDeliveryStatus.PENDING); d.setAttemptCount(0); return d; }
 private NotificationEntity notification(FastingProgramOccurrenceEntity occurrence,ReminderEvent event,UserEntity user,LocalDateTime now){ ReminderCopy copy=copy(event.type(),occurrence.getRuleType()); NotificationEntity n=new NotificationEntity(); n.setUser(user); n.setTitle(copy.title()); n.setMessage(copy.message()); n.setType("fasting_reminder"); n.setSeverity("INFO"); n.setSource("ADVANCED_FASTING_REMINDER"); n.setTargetType("FASTING_OCCURRENCE"); n.setTargetId(String.valueOf(occurrence.getId())); n.setTargetRoute("fasting"); n.setPrimaryAction("VIEW_FASTING"); n.setVisibleInApp(true); n.setIsRead(false); n.setCreatedAt(now); return n; }
 private ReminderCopy copy(AdvancedFastingReminderType type,FastingDayRuleType rule){ return switch(type){
  case PRE_START->new ReminderCopy("Fasting window coming up","Your planned fasting window starts soon. You can adjust or skip it if today is not a good fit.");
  case START->rule==FastingDayRuleType.REDUCED_CALORIE?new ReminderCopy("Reduced-calorie day","Today is a planned reduced-calorie day. Follow the target only if it still feels appropriate for you."):new ReminderCopy("Planned fasting window","Your planned fasting window is ready when you are.");
  case NEARING_COMPLETION->new ReminderCopy("Fasting window nearly complete","Your planned fasting window is close to completion.");
  case COMPLETION->new ReminderCopy("Plan update","Your planned fasting activity has been recorded. Review it when convenient.");
  case MISSED_PLAN->new ReminderCopy("Plan check-in","This fasting window was not started. You can leave it as missed or update your plan without penalty."); }; }
 private boolean inQuietHours(UserEntity user,LocalTime time){ LocalTime start=user.getNotificationQuietHoursStart(),end=user.getNotificationQuietHoursEnd(); if(start==null||end==null||start.equals(end))return false; return start.isBefore(end)?(!time.isBefore(start)&&time.isBefore(end)):(!time.isBefore(start)||time.isBefore(end)); }
 private LocalDateTime nextQuietEnd(UserEntity user,LocalDateTime now){ LocalTime end=user.getNotificationQuietHoursEnd(); LocalDate date=now.toLocalTime().isBefore(end)?now.toLocalDate():now.toLocalDate().plusDays(1); return date.atTime(end); }
 private AdvancedFastingOperationsConfigEntity config(){
  if(governanceService!=null)return governanceService.currentOperations();
  AdvancedFastingOperationsConfigEntity c=new AdvancedFastingOperationsConfigEntity(); c.setReminderEnabled(true); c.setPreStartMinutes(30); c.setNearingCompletionMinutes(30); c.setMissedPlanMinutes(60); c.setMaxRetryAttempts(3); return c;
 }
 @Override @Transactional(readOnly=true)
 public AdvancedFastingReminderSettingsDto getSettings(String email){ return dto(settings(user(email))); }
 @Override @Transactional
 public AdvancedFastingReminderSettingsDto updateSettings(String email,AdvancedFastingReminderSettingsDto request){
  UserEntity user=user(email); validate(request); AdvancedFastingReminderSettingsEntity entity=settings(user);
  entity.setEnabled(request.getEnabled()); entity.setPreStartEnabled(request.getPreStartEnabled()); entity.setStartEnabled(request.getStartEnabled());
  entity.setNearingCompletionEnabled(request.getNearingCompletionEnabled()); entity.setCompletionEnabled(request.getCompletionEnabled()); entity.setMissedPlanEnabled(request.getMissedPlanEnabled());
  entity.setPreStartMinutes(request.getPreStartMinutes()); entity.setNearingCompletionMinutes(request.getNearingCompletionMinutes()); entity=settingsRepository.save(entity);
  if(!Boolean.TRUE.equals(entity.getEnabled())) deliveryRepository.suppressUndeliveredForUser(user.getId(),"Advanced fasting reminders disabled");
  return dto(entity);
 }
 private AdvancedFastingReminderSettingsEntity settings(UserEntity user){ return settingsRepository.findByUser(user).orElseGet(()->{ AdvancedFastingReminderSettingsEntity e=new AdvancedFastingReminderSettingsEntity(); e.setUser(user); return e; }); }
 private UserEntity user(String email){ return userRepository.findByEmail(email).orElseThrow(()->new com.grun.calorietracker.exception.InvalidCredentialsException("Invalid credential")); }
 private void validate(AdvancedFastingReminderSettingsDto request){ if(request==null||request.getEnabled()==null||request.getPreStartEnabled()==null||request.getStartEnabled()==null||request.getNearingCompletionEnabled()==null||request.getCompletionEnabled()==null||request.getMissedPlanEnabled()==null||request.getPreStartMinutes()==null||request.getNearingCompletionMinutes()==null) throw new IllegalArgumentException("All advanced fasting reminder settings are required."); if(request.getPreStartMinutes()<5||request.getPreStartMinutes()>180) throw new IllegalArgumentException("preStartMinutes must be between 5 and 180."); if(request.getNearingCompletionMinutes()<5||request.getNearingCompletionMinutes()>120) throw new IllegalArgumentException("nearingCompletionMinutes must be between 5 and 120."); }
 private AdvancedFastingReminderSettingsDto dto(AdvancedFastingReminderSettingsEntity e){ AdvancedFastingReminderSettingsDto d=new AdvancedFastingReminderSettingsDto(); d.setEnabled(e.getEnabled()); d.setPreStartEnabled(e.getPreStartEnabled()); d.setStartEnabled(e.getStartEnabled()); d.setNearingCompletionEnabled(e.getNearingCompletionEnabled()); d.setCompletionEnabled(e.getCompletionEnabled()); d.setMissedPlanEnabled(e.getMissedPlanEnabled()); d.setPreStartMinutes(e.getPreStartMinutes()); d.setNearingCompletionMinutes(e.getNearingCompletionMinutes()); return d; } private void increment(String name){ if(meterRegistry!=null)meterRegistry.counter(name).increment(); }
 private int retryMinutes(int attempt){ return attempt<=1?5:attempt==2?15:30; }
 private String limit(String value){ if(value==null)return "Reminder delivery failed"; return value.length()>500?value.substring(0,500):value; }
 private record ReminderEvent(AdvancedFastingReminderType type,LocalDateTime at){}
 private record ReminderCopy(String title,String message){}
}
