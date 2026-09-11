package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.*;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.reminder.*;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.*;
import java.time.*; import java.util.*;
import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;

class AdminMealReminderAutomationServiceImplTest {
 MealReminderPolicyRepository policies=mock(MealReminderPolicyRepository.class); MealReminderDryRunDecisionRepository decisions=mock(MealReminderDryRunDecisionRepository.class);
 MealReminderOccurrenceRepository occurrences=mock(MealReminderOccurrenceRepository.class); MealReminderOutboxRepository outboxes=mock(MealReminderOutboxRepository.class);
 MealReminderInteractionRepository interactions=mock(MealReminderInteractionRepository.class); MealReminderDeliveryAttemptRepository attempts=mock(MealReminderDeliveryAttemptRepository.class);
 MealReminderAdminTestSendRepository sends=mock(MealReminderAdminTestSendRepository.class); UserRepository users=mock(UserRepository.class); NotificationDefinitionRepository definitions=mock(NotificationDefinitionRepository.class);
 NotificationRepository notifications=mock(NotificationRepository.class); NotificationDefinitionPolicy definitionPolicy=mock(NotificationDefinitionPolicy.class); PushDeliveryService pushDeliveryService=mock(PushDeliveryService.class); MealReminderDeliveryProperties deployment=new MealReminderDeliveryProperties(); PushProperties push=new PushProperties(); AdminAuditService audit=mock(AdminAuditService.class);
 Instant now=Instant.parse("2026-08-29T12:00:00Z"); AdminMealReminderAutomationServiceImpl service;
 @BeforeEach void setUp(){service=new AdminMealReminderAutomationServiceImpl(policies,decisions,occurrences,interactions,attempts,outboxes,sends,users,definitions,notifications,definitionPolicy,pushDeliveryService,deployment,push,audit,Clock.fixed(now,ZoneOffset.UTC));}

 @Test void rejectsPolicyAboveHardLimitsBeforeWriting(){assertThrows(IllegalArgumentException.class,()->service.createDraft(request(null,4),"maker","cid"));verifyNoInteractions(audit);}
 @Test void detectsOptimisticConflict(){MealReminderPolicyEntity e=policy(MealReminderPolicyStatus.DRAFT);e.setVersion(3L);when(policies.findByIdForUpdate(1L)).thenReturn(Optional.of(e));assertThrows(OptimisticLockException.class,()->service.updateDraft(1L,request(2L,3),"maker","cid"));verify(policies,never()).save(any());}
 @Test void emergencyStopIsImmediateAuditedAndDoesNotEnableDeployment(){MealReminderPolicyEntity e=policy(MealReminderPolicyStatus.ACTIVE);when(policies.findFirstByStatus(MealReminderPolicyStatus.ACTIVE)).thenReturn(Optional.of(e));when(policies.findByIdForUpdate(1L)).thenReturn(Optional.of(e));var result=service.emergencyStop("incident","growth","cid");assertTrue(result.emergencyStopped());assertEquals(MealReminderContract.Mode.OFF,result.mode());assertFalse(result.deploymentGateEnabled());verify(audit).record(eq("growth"),eq(AdminAuditActionType.MEAL_REMINDER_EMERGENCY_STOP),any(),eq("1"),isNull(),any(),eq("cid"));}
 @Test void definitionPublishRejectsUnknownPlaceholder(){NotificationDefinitionEntity e=new NotificationDefinitionEntity();e.setId(9L);e.setKey("meal_reminder_lunch");e.setProtectedDefinition(true);when(definitions.findById(9L)).thenReturn(Optional.of(e));AdminNotificationDefinitionRequestDto r=definition("meal_reminder_lunch","Try {calories}");assertThrows(IllegalArgumentException.class,()->service.publishDefinitionApproved(9L,r,"checker","cid"));verify(definitions,never()).save(any());}
 @Test void approvedTestSendImmediatelyUsesManagedTurkishCopy(){MealReminderPolicyEntity e=policy(MealReminderPolicyStatus.ACTIVE);e.setMode(MealReminderContract.Mode.LIVE);e.setPilotUserIds("6");when(policies.findFirstByStatus(MealReminderPolicyStatus.ACTIVE)).thenReturn(Optional.of(e));UserEntity user=new UserEntity();user.setId(6L);user.setPushNotificationsEnabled(true);user.setMealRemindersEnabled(true);user.setPreferredLanguage(PreferredLanguage.TR);user.setTimeZone("Europe/Istanbul");when(users.findById(6L)).thenReturn(Optional.of(user));deployment.setDeliveryEnabled(true);push.setEnabled(true);NotificationDefinitionEntity definition=new NotificationDefinitionEntity();definition.setKey("meal_reminder_lunch");when(definitionPolicy.find("meal_reminder_lunch")).thenReturn(definition);when(definitionPolicy.presentation(any(),eq(definition))).thenReturn(new NotificationDefinitionPolicy.NotificationPresentation("Küçük bir öğle hatırlatması","Hazır olduğunda öğle öğününü ekleyerek gününü güncel tutabilirsin.","INFO","diary"));when(pushDeliveryService.deliver(any())).thenReturn(new PushDeliveryResultDto(1,1,0,0));service.testSendApproved(6L,"checker","cid");verify(notifications).saveAndFlush(argThat(n->"Küçük bir öğle hatırlatması".equals(n.getTitle())&&n.getMessage().contains("öğününü")));verify(pushDeliveryService).deliver(any());}

 private AdminMealReminderPolicyRequestDto request(Long version,int maxDaily){return new AdminMealReminderPolicyRequestDto(version,MealReminderContract.Mode.PILOT,true,LocalTime.of(8,30),LocalTime.of(13,0),LocalTime.of(19,0),60,maxDaily,3,1,180,60,LocalTime.of(22,0),LocalTime.of(8,0),Set.of(11L));}
 private MealReminderPolicyEntity policy(MealReminderPolicyStatus status){MealReminderPolicyEntity e=new MealReminderPolicyEntity();e.setId(1L);e.setVersion(0L);e.setPolicyVersion("p1");e.setStatus(status);e.setMode(MealReminderContract.Mode.PILOT);e.setKcalEnabled(true);e.setBreakfastTime(LocalTime.of(8,30));e.setLunchTime(LocalTime.of(13,0));e.setDinnerTime(LocalTime.of(19,0));e.setSlotAgeMinutes(60);e.setMaxDaily(3);e.setMaxRolling(3);e.setMaxCatchups(1);e.setMinimumGapMinutes(180);e.setRoutineGapMinutes(60);e.setQuietStart(LocalTime.of(22,0));e.setQuietEnd(LocalTime.of(8,0));e.setPilotUserIds("11");e.setUpdatedBy("system");e.setUpdatedAt(now);return e;}
 private AdminNotificationDefinitionRequestDto definition(String key,String message){AdminNotificationDefinitionRequestDto r=new AdminNotificationDefinitionRequestDto();r.setKey(key);r.setDisplayName("Lunch");r.setEnabled(true);r.setChannel(NotificationCampaignChannel.IN_APP_AND_PUSH);r.setSeverity("INFO");r.setTargetRoute("diary");r.setTitleEn("Title");r.setMessageEn(message);r.setTitleTr("Başlık");r.setMessageTr("Mesaj");return r;}
}
