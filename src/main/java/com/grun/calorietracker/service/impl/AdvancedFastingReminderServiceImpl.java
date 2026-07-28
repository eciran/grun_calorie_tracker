package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdvancedFastingReminderServiceImpl implements AdvancedFastingReminderService {
 private static final int PRE_START_MINUTES=30;
 private final FastingProgramRepository programRepository;
 private final FastingProgramOccurrenceRepository occurrenceRepository;
 private final FastingReminderDeliveryRepository deliveryRepository;
 private final AdvancedFastingExecutionService executionService;
 private final NotificationRepository notificationRepository;
 private final PushDeliveryService pushDeliveryService;
 private final UserTimeZoneSupport timeZoneSupport;
 @Value("${grun.fasting.advanced-reminders.enabled:true}") private boolean enabled;

 @Override @Scheduled(fixedDelayString="${grun.fasting.advanced-reminders.scan-interval-ms:300000}") @Transactional
 public int createDueReminderNotifications(){
  if(!enabled)return 0; int delivered=0;
  for(FastingProgramEntity program:programRepository.findAllByStatus(FastingProgramStatus.ACTIVE)){
   UserEntity user=program.getUser(); LocalDate today=timeZoneSupport.today(user); LocalDateTime now=timeZoneSupport.now(user);
   for(int offset=-1;offset<=1;offset++){
    LocalDate date=today.plusDays(offset);
    if((program.getEffectiveFrom()!=null&&date.isBefore(program.getEffectiveFrom()))||(program.getEffectiveUntil()!=null&&date.isAfter(program.getEffectiveUntil())))continue;
    try{ executionService.getOrCreate(user.getEmail(),date); }catch(RuntimeException ignored){ continue; }
    var occurrence=occurrenceRepository.findByUserAndOccurrenceDate(user,date).orElse(null);
    if(occurrence!=null) delivered+=dispatchDue(occurrence,now);
   }
  }
  return delivered;
 }
 int dispatchDue(FastingProgramOccurrenceEntity occurrence,LocalDateTime now){
  if(occurrence.getStatus()==FastingOccurrenceStatus.SKIPPED)return 0;
  List<ReminderEvent> events=events(occurrence);
  int sent=0; for(ReminderEvent event:events){ if(isDue(event,now)&&deliver(occurrence,event,now))sent++; } return sent;
 }
 private List<ReminderEvent> events(FastingProgramOccurrenceEntity occurrence){
  List<ReminderEvent> result=new ArrayList<>();
  if(occurrence.getRuleType()==FastingDayRuleType.FAST&&occurrence.getPlannedStartAt()!=null){
   result.add(new ReminderEvent(AdvancedFastingReminderType.PRE_START,occurrence.getPlannedStartAt().minusMinutes(PRE_START_MINUTES)));
   result.add(new ReminderEvent(AdvancedFastingReminderType.START,occurrence.getPlannedStartAt()));
   FastingSessionEntity session=occurrence.getFastingSession();
   if(session==null) result.add(new ReminderEvent(AdvancedFastingReminderType.MISSED_PLAN,occurrence.getPlannedStartAt().plusMinutes(60)));
   else if(session.getStatus()==FastingSessionStatus.ACTIVE&&session.getTargetEndAt()!=null) result.add(new ReminderEvent(AdvancedFastingReminderType.NEARING_COMPLETION,session.getTargetEndAt().minusMinutes(PRE_START_MINUTES)));
   else if(session.getStatus()==FastingSessionStatus.COMPLETED&&session.getEndedAt()!=null) result.add(new ReminderEvent(AdvancedFastingReminderType.COMPLETION,session.getEndedAt()));
  } else if(occurrence.getRuleType()==FastingDayRuleType.REDUCED_CALORIE){
   result.add(new ReminderEvent(AdvancedFastingReminderType.START,occurrence.getOccurrenceDate().atTime(8,0)));
   if(occurrence.getStatus()==FastingOccurrenceStatus.COMPLETED) result.add(new ReminderEvent(AdvancedFastingReminderType.COMPLETION,occurrence.getOccurrenceDate().plusDays(1).atTime(0,5)));
  }
  return result;
 }
 private boolean isDue(ReminderEvent event,LocalDateTime now){ return !event.at().isAfter(now)&&!event.at().isBefore(now.minusHours(36)); }
 private boolean deliver(FastingProgramOccurrenceEntity occurrence,ReminderEvent event,LocalDateTime now){
  String key=occurrence.getProgram().getId()+":"+occurrence.getProgramVersion().getVersionNumber()+":"+occurrence.getOccurrenceDate()+":"+event.type();
  FastingReminderDeliveryEntity delivery=deliveryRepository.findByOccurrenceKey(key).orElseGet(()->newDelivery(occurrence,event,key));
  if(delivery.getStatus()==FastingReminderDeliveryStatus.SENT||delivery.getStatus()==FastingReminderDeliveryStatus.SUPPRESSED)return false;
  if(delivery.getStatus()==FastingReminderDeliveryStatus.FAILED&&delivery.getAttemptCount()>=3)return false;
  if(delivery.getNextAttemptAt()!=null&&delivery.getNextAttemptAt().isAfter(now))return false;
  UserEntity user=occurrence.getUser();
  if(!Boolean.TRUE.equals(user.getFastingRemindersEnabled())){
   delivery.setStatus(FastingReminderDeliveryStatus.SUPPRESSED); delivery.setLastError("Notification preference disabled"); deliveryRepository.save(delivery); return false;
  }
  if(inQuietHours(user,now.toLocalTime())){
   delivery.setStatus(FastingReminderDeliveryStatus.DEFERRED); delivery.setNextAttemptAt(nextQuietEnd(user,now)); deliveryRepository.save(delivery); return false;
  }
  try{
   NotificationEntity notification=delivery.getNotification();
   if(notification==null){ notification=notificationRepository.save(notification(occurrence,event,user,now)); delivery.setNotification(notification); }
   PushDeliveryResultDto result=pushDeliveryService.deliver(notification); delivery.setAttemptCount(delivery.getAttemptCount()+1);
   if(result.getFailed()>0){ delivery.setStatus(FastingReminderDeliveryStatus.FAILED); delivery.setLastError("Push provider delivery failed"); delivery.setNextAttemptAt(now.plusMinutes(retryMinutes(delivery.getAttemptCount()))); }
   else { delivery.setStatus(FastingReminderDeliveryStatus.SENT); delivery.setDeliveredAt(now); delivery.setNextAttemptAt(null); delivery.setLastError(null); }
   deliveryRepository.save(delivery); return delivery.getStatus()==FastingReminderDeliveryStatus.SENT;
  }catch(RuntimeException ex){ delivery.setAttemptCount(delivery.getAttemptCount()+1); delivery.setStatus(FastingReminderDeliveryStatus.FAILED); delivery.setLastError(limit(ex.getMessage())); delivery.setNextAttemptAt(now.plusMinutes(retryMinutes(delivery.getAttemptCount()))); deliveryRepository.save(delivery); return false; }
 }
 private FastingReminderDeliveryEntity newDelivery(FastingProgramOccurrenceEntity occurrence,ReminderEvent event,String key){ FastingReminderDeliveryEntity d=new FastingReminderDeliveryEntity(); d.setOccurrence(occurrence); d.setOccurrenceKey(key); d.setReminderType(event.type()); d.setScheduledFor(event.at()); d.setStatus(FastingReminderDeliveryStatus.PENDING); d.setAttemptCount(0); return d; }
 private NotificationEntity notification(FastingProgramOccurrenceEntity occurrence,ReminderEvent event,UserEntity user,LocalDateTime now){ ReminderCopy copy=copy(event.type(),occurrence.getRuleType()); NotificationEntity n=new NotificationEntity(); n.setUser(user); n.setTitle(copy.title()); n.setMessage(copy.message()); n.setType("fasting_reminder"); n.setSeverity("INFO"); n.setSource("ADVANCED_FASTING_REMINDER"); n.setTargetType("FASTING_OCCURRENCE"); n.setTargetId(String.valueOf(occurrence.getId())); n.setTargetRoute("fasting"); n.setPrimaryAction("VIEW_FASTING"); n.setVisibleInApp(true); n.setIsRead(false); n.setCreatedAt(now); return n; }
 private ReminderCopy copy(AdvancedFastingReminderType type,FastingDayRuleType rule){ return switch(type){
  case PRE_START->new ReminderCopy("Fasting window coming up","Your planned fasting window starts in about 30 minutes. You can adjust or skip it if today is not a good fit.");
  case START->rule==FastingDayRuleType.REDUCED_CALORIE?new ReminderCopy("Reduced-calorie day","Today is a planned reduced-calorie day. Follow the target only if it still feels appropriate for you."):new ReminderCopy("Planned fasting window","Your planned fasting window is ready when you are.");
  case NEARING_COMPLETION->new ReminderCopy("Fasting window nearly complete","Your planned fasting window is close to completion.");
  case COMPLETION->new ReminderCopy("Plan update","Your planned fasting activity has been recorded. Review it when convenient.");
  case MISSED_PLAN->new ReminderCopy("Plan check-in","This fasting window was not started. You can leave it as missed or update your plan without penalty."); }; }
 private boolean inQuietHours(UserEntity user,LocalTime time){ LocalTime start=user.getNotificationQuietHoursStart(),end=user.getNotificationQuietHoursEnd(); if(start==null||end==null||start.equals(end))return false; return start.isBefore(end)?(!time.isBefore(start)&&time.isBefore(end)):(!time.isBefore(start)||time.isBefore(end)); }
 private LocalDateTime nextQuietEnd(UserEntity user,LocalDateTime now){ LocalTime end=user.getNotificationQuietHoursEnd(); LocalDate date=now.toLocalTime().isBefore(end)?now.toLocalDate():now.toLocalDate().plusDays(1); return date.atTime(end); }
 private int retryMinutes(int attempt){ return attempt<=1?5:attempt==2?15:30; }
 private String limit(String value){ if(value==null)return "Reminder delivery failed"; return value.length()>500?value.substring(0,500):value; }
 private record ReminderEvent(AdvancedFastingReminderType type,LocalDateTime at){}
 private record ReminderCopy(String title,String message){}
}
