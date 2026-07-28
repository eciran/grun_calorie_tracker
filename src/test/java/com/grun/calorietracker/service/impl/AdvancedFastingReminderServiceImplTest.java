package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdvancedFastingReminderSettingsDto;
import com.grun.calorietracker.dto.PushDeliveryResultDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdvancedFastingReminderServiceImplTest {
 @Mock FastingProgramRepository programs; @Mock FastingProgramOccurrenceRepository occurrences; @Mock FastingReminderDeliveryRepository deliveries;
 @Mock AdvancedFastingReminderSettingsRepository settings; @Mock UserRepository users;
 @Mock AdvancedFastingExecutionService execution; @Mock NotificationRepository notifications; @Mock PushDeliveryService push;
 AdvancedFastingReminderServiceImpl service; UserEntity user; FastingProgramOccurrenceEntity occurrence; LocalDateTime now;
 @BeforeEach void setup(){ MockitoAnnotations.openMocks(this); service=new AdvancedFastingReminderServiceImpl(programs,occurrences,deliveries,settings,users,execution,notifications,push,new UserTimeZoneSupport());
  user=new UserEntity(); user.setId(1L); user.setEmail("user@grun.app"); user.setTimeZone("Europe/Dublin"); user.setPushNotificationsEnabled(true); user.setFastingRemindersEnabled(true);
  FastingProgramEntity program=new FastingProgramEntity(); program.setId(2L); program.setUser(user); FastingProgramVersionEntity version=new FastingProgramVersionEntity(); version.setId(3L); version.setVersionNumber(4); version.setProgram(program);
  occurrence=new FastingProgramOccurrenceEntity(); occurrence.setId(5L); occurrence.setUser(user); occurrence.setProgram(program); occurrence.setProgramVersion(version); occurrence.setOccurrenceDate(LocalDate.of(2026,7,28)); occurrence.setRuleType(FastingDayRuleType.FAST); occurrence.setStatus(FastingOccurrenceStatus.PLANNED);
  now=LocalDateTime.of(2026,7,28,19,30); occurrence.setPlannedStartAt(now.plusMinutes(30)); occurrence.setPlannedEndAt(now.plusHours(16).plusMinutes(30)); }
 @Test void duplicateOccurrenceKeyIsNotDeliveredTwice(){ FastingReminderDeliveryEntity sent=delivery(FastingReminderDeliveryStatus.SENT); when(deliveries.findByOccurrenceKey(key())).thenReturn(Optional.of(sent)); assertEquals(0,service.dispatchDue(occurrence,now)); verifyNoInteractions(notifications,push); }
 @Test void disabledModulePreferenceSuppressesReminder(){ user.setFastingRemindersEnabled(false); when(deliveries.findByOccurrenceKey(key())).thenReturn(Optional.empty()); when(deliveries.save(any())).thenAnswer(i->i.getArgument(0)); assertEquals(0,service.dispatchDue(occurrence,now)); ArgumentCaptor<FastingReminderDeliveryEntity> captor=ArgumentCaptor.forClass(FastingReminderDeliveryEntity.class); verify(deliveries).save(captor.capture()); assertEquals(FastingReminderDeliveryStatus.SUPPRESSED,captor.getValue().getStatus()); verifyNoInteractions(notifications,push); }
 @Test void quietHoursDefersUntilLocalQuietEnd(){ user.setNotificationQuietHoursStart(LocalTime.of(22,0)); user.setNotificationQuietHoursEnd(LocalTime.of(7,0)); now=LocalDateTime.of(2026,7,28,23,0); occurrence.setPlannedStartAt(now.plusMinutes(30)); when(deliveries.findByOccurrenceKey(key())).thenReturn(Optional.empty()); when(deliveries.save(any())).thenAnswer(i->i.getArgument(0)); assertEquals(0,service.dispatchDue(occurrence,now)); ArgumentCaptor<FastingReminderDeliveryEntity> captor=ArgumentCaptor.forClass(FastingReminderDeliveryEntity.class); verify(deliveries).save(captor.capture()); assertEquals(FastingReminderDeliveryStatus.DEFERRED,captor.getValue().getStatus()); assertEquals(LocalDateTime.of(2026,7,29,7,0),captor.getValue().getNextAttemptAt()); }
 @Test void providerFailureRetriesSameNotificationWithoutDuplicate(){ AtomicReference<FastingReminderDeliveryEntity> stored=new AtomicReference<>(); when(deliveries.findByOccurrenceKey(key())).thenAnswer(i->Optional.ofNullable(stored.get())); when(deliveries.save(any())).thenAnswer(i->{FastingReminderDeliveryEntity d=i.getArgument(0);stored.set(d);return d;}); when(notifications.save(any())).thenAnswer(i->{NotificationEntity n=i.getArgument(0);n.setId(9L);return n;}); when(push.deliver(any())).thenReturn(new PushDeliveryResultDto(1,0,0,1),new PushDeliveryResultDto(1,1,0,0));
  assertEquals(0,service.dispatchDue(occurrence,now)); assertEquals(FastingReminderDeliveryStatus.FAILED,stored.get().getStatus()); assertEquals(1,service.dispatchDue(occurrence,now.plusMinutes(6))); assertEquals(FastingReminderDeliveryStatus.SENT,stored.get().getStatus()); verify(notifications,times(1)).save(any()); verify(push,times(2)).deliver(any()); }
 @Test void disablingAdvancedRemindersSuppressesUndeliveredJobs(){
  when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
  when(settings.findByUser(user)).thenReturn(Optional.empty());
  when(settings.save(any())).thenAnswer(i->i.getArgument(0));
  AdvancedFastingReminderSettingsDto request=settingsDto(); request.setEnabled(false);
  AdvancedFastingReminderSettingsDto result=service.updateSettings(user.getEmail(),request);
  assertFalse(result.getEnabled()); verify(deliveries).suppressUndeliveredForUser(user.getId(),"Advanced fasting reminders disabled");
 }
 @Test void disabledPreStartEventDoesNotCreateDelivery(){
  AdvancedFastingReminderSettingsEntity preference=new AdvancedFastingReminderSettingsEntity(); preference.setUser(user); preference.setPreStartEnabled(false); preference.setStartEnabled(false); preference.setMissedPlanEnabled(false);
  when(settings.findByUser(user)).thenReturn(Optional.of(preference));
  assertEquals(0,service.dispatchDue(occurrence,now)); verifyNoInteractions(deliveries,notifications,push);
 }
 private AdvancedFastingReminderSettingsDto settingsDto(){ AdvancedFastingReminderSettingsDto d=new AdvancedFastingReminderSettingsDto(); d.setEnabled(true); d.setPreStartEnabled(true); d.setStartEnabled(true); d.setNearingCompletionEnabled(true); d.setCompletionEnabled(true); d.setMissedPlanEnabled(false); d.setPreStartMinutes(30); d.setNearingCompletionMinutes(15); return d; } private FastingReminderDeliveryEntity delivery(FastingReminderDeliveryStatus status){ FastingReminderDeliveryEntity d=new FastingReminderDeliveryEntity(); d.setOccurrence(occurrence); d.setOccurrenceKey(key()); d.setReminderType(AdvancedFastingReminderType.PRE_START); d.setStatus(status); d.setAttemptCount(1); d.setScheduledFor(now); return d; }
 private String key(){ return "2:4:2026-07-28:PRE_START"; }
}
