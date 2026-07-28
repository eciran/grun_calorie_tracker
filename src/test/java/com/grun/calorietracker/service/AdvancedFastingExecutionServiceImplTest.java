package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdvancedFastingExecutionServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdvancedFastingExecutionServiceImplTest {
 @Mock UserRepository users; @Mock FastingProgramRepository programs; @Mock FastingProgramVersionRepository versions;
 @Mock FastingProgramDayRuleRepository rules; @Mock FastingProgramOccurrenceRepository occurrences; @Mock FastingSessionRepository sessions;
 @Mock FoodLogsService foodLogs; @Mock UserAnalyticsCacheRevisionService revisions;
 AdvancedFastingExecutionServiceImpl service; UserEntity user; FastingProgramEntity program; FastingProgramVersionEntity version;
 @BeforeEach void setup(){ MockitoAnnotations.openMocks(this); service=new AdvancedFastingExecutionServiceImpl(users,programs,versions,rules,occurrences,sessions,foodLogs,new UserTimeZoneSupport(),revisions);
  user=new UserEntity(); user.setId(7L); user.setEmail("owner@grun.app"); user.setTimeZone("Europe/Dublin");
  program=new FastingProgramEntity(); program.setId(11L); program.setUser(user); program.setStatus(FastingProgramStatus.ACTIVE); program.setCurrentVersionNumber(2);
  version=new FastingProgramVersionEntity(); version.setId(12L); version.setProgram(program); version.setVersionNumber(2); version.setSafetyPolicyVersion("2026-07-v1");
  when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user)); when(programs.findFirstByUserIdAndStatus(7L,FastingProgramStatus.ACTIVE)).thenReturn(Optional.of(program));
  when(versions.findByProgramIdAndVersionNumber(11L,2)).thenReturn(Optional.of(version)); when(occurrences.save(any())).thenAnswer(i->{ var o=(FastingProgramOccurrenceEntity)i.getArgument(0); if(o.getId()==null)o.setId(30L); return o; }); }
 @Test void reducedCalorieOccurrenceUsesCanonicalDailyTotalsWithoutCreatingSession(){ LocalDate date=LocalDate.of(2026,7,20); FastingProgramDayRuleEntity rule=rule(FastingDayRuleType.REDUCED_CALORIE,date.getDayOfWeek()); rule.setReducedCalorieTarget(600); when(rules.findByProgramVersionIdAndDayOfWeek(12L,date.getDayOfWeek())).thenReturn(Optional.of(rule));
  FoodLogDailyStatsDto stats=new FoodLogDailyStatsDto(); stats.setDate(date.toString()); stats.setTotalCalories(575D); when(foodLogs.getDailyStats(user.getEmail(),date.atStartOfDay(),date.plusDays(1).atStartOfDay())).thenReturn(List.of(stats));
  FastingOccurrenceDto result=service.getOrCreate(user.getEmail(),date); assertEquals(FastingDayRuleType.REDUCED_CALORIE,result.ruleType()); assertEquals(575D,result.actualCalories()); assertNull(result.sessionId()); verifyNoInteractions(sessions); }
 @Test void reducedDaySummaryReturnsCanonicalConsumedAndRemainingCalories(){ LocalDate date=LocalDate.of(2026,7,20); FastingProgramDayRuleEntity rule=rule(FastingDayRuleType.REDUCED_CALORIE,date.getDayOfWeek()); rule.setReducedCalorieTarget(600); when(rules.findByProgramVersionIdAndDayOfWeek(12L,date.getDayOfWeek())).thenReturn(Optional.of(rule));
  FoodLogDailyStatsDto stats=new FoodLogDailyStatsDto(); stats.setDate(date.toString()); stats.setTotalCalories(575D); when(foodLogs.getDailyStats(user.getEmail(),date.atStartOfDay(),date.plusDays(1).atStartOfDay())).thenReturn(List.of(stats));
  ReducedDayNutritionSummaryDto result=service.reducedDayNutritionSummary(user.getEmail(),date); assertEquals(600,result.plannedCalories()); assertEquals(575D,result.consumedCalories()); assertEquals(25D,result.remainingCalories()); assertEquals(FastingAdherenceStatus.MET,result.adherenceStatus()); assertNotNull(result.lastEvaluatedAt()); }
 @Test void fastOccurrenceStartsSessionWithImmutablePlanSnapshot(){ LocalDate date=LocalDate.of(2026,7,28); FastingProgramDayRuleEntity rule=rule(FastingDayRuleType.FAST,date.getDayOfWeek()); rule.setFastingMinutes(960); rule.setPreferredStartTime(LocalTime.of(20,0)); when(rules.findByProgramVersionIdAndDayOfWeek(12L,date.getDayOfWeek())).thenReturn(Optional.of(rule)); when(sessions.findTopByUserAndStatusOrderByStartedAtDesc(user,FastingSessionStatus.ACTIVE)).thenReturn(Optional.empty()); when(sessions.save(any())).thenAnswer(i->{ var s=(FastingSessionEntity)i.getArgument(0); s.setId(40L); return s; });
  FastingSessionStartRequestDto request=new FastingSessionStartRequestDto(); request.setStartedAt(LocalDateTime.of(2026,7,28,20,5)); FastingSessionDto result=service.start(user.getEmail(),date,request);
  assertEquals(40L,result.getId()); assertEquals(2,result.getPlannedProgramVersion()); assertEquals(960,result.getPlannedFastingMinutes()); assertEquals(LocalDateTime.of(2026,7,28,20,0),result.getPlannedStartAt()); }
 @Test void reducedCalorieOccurrenceCannotStartFakeFastingSession(){ LocalDate date=LocalDate.of(2026,7,28); FastingProgramDayRuleEntity rule=rule(FastingDayRuleType.REDUCED_CALORIE,date.getDayOfWeek()); rule.setReducedCalorieTarget(600); when(rules.findByProgramVersionIdAndDayOfWeek(12L,date.getDayOfWeek())).thenReturn(Optional.of(rule)); assertThrows(IllegalArgumentException.class,()->service.start(user.getEmail(),date,new FastingSessionStartRequestDto())); verify(sessions,never()).save(any()); }
 private FastingProgramDayRuleEntity rule(FastingDayRuleType type,DayOfWeek day){ FastingProgramDayRuleEntity r=new FastingProgramDayRuleEntity(); r.setId(13L); r.setProgramVersion(version); r.setRuleType(type); r.setDayOfWeek(day); return r; }
}
