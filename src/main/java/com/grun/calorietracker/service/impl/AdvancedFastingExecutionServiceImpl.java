package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor
public class AdvancedFastingExecutionServiceImpl implements AdvancedFastingExecutionService {
 private final UserRepository userRepository;
 private final FastingProgramRepository programRepository;
 private final FastingProgramVersionRepository versionRepository;
 private final FastingProgramDayRuleRepository ruleRepository;
 private final FastingProgramOccurrenceRepository occurrenceRepository;
 private final FastingSessionRepository sessionRepository;
 private final FoodLogsService foodLogsService;
 private final UserTimeZoneSupport timeZoneSupport;
 private final UserAnalyticsCacheRevisionService cacheRevisionService;

 @Override @Transactional public FastingOccurrenceDto getOrCreate(String email,LocalDate date){
  UserEntity user=user(email); LocalDate resolved=date==null?timeZoneSupport.today(user):date;
  FastingProgramOccurrenceEntity occurrence=occurrenceRepository.findByUserAndOccurrenceDate(user,resolved)
   .orElseGet(()->createOccurrence(user,resolved));
  return dto(refresh(occurrence,email,user));
 }
 @Override @Transactional public FastingOccurrenceDto recalculate(String email,LocalDate date){ return getOrCreate(email,date); }
 @Override @Transactional public FastingOccurrenceDto skip(String email,LocalDate date,FastingOccurrenceSkipRequestDto request){
  UserEntity user=user(email); LocalDate resolved=date==null?timeZoneSupport.today(user):date;
  FastingProgramOccurrenceEntity occurrence=occurrenceRepository.findByUserAndOccurrenceDate(user,resolved).orElseGet(()->createOccurrence(user,resolved));
  if(occurrence.getFastingSession()!=null&&occurrence.getFastingSession().getStatus()==FastingSessionStatus.ACTIVE) throw new AdvancedFastingException(AdvancedFastingErrorCode.ACTIVE_OCCURRENCE_CANNOT_BE_SKIPPED,"An active occurrence cannot be skipped.");
  occurrence.setStatus(FastingOccurrenceStatus.SKIPPED); occurrence.setAdherenceStatus(FastingAdherenceStatus.NOT_APPLICABLE);
  occurrence.setSkipReason(request.getReason()); occurrence.setReasonNote(normalize(request.getNote())); occurrence.setEvaluatedAt(timeZoneSupport.now(user));
  return dto(occurrenceRepository.save(occurrence));
 }
 @Override @Transactional public FastingSessionDto start(String email,LocalDate date,FastingSessionStartRequestDto request){
  UserEntity user=user(email); LocalDate resolved=date==null?timeZoneSupport.today(user):date;
  FastingProgramOccurrenceEntity occurrence=occurrenceRepository.findByUserAndOccurrenceDate(user,resolved).orElseGet(()->createOccurrence(user,resolved));
  if(occurrence.getRuleType()!=FastingDayRuleType.FAST) throw new AdvancedFastingException(AdvancedFastingErrorCode.FAST_OCCURRENCE_REQUIRED,"Only FAST occurrences create fasting sessions.");
  if(occurrence.getStatus()==FastingOccurrenceStatus.SKIPPED) throw new AdvancedFastingException(AdvancedFastingErrorCode.SKIPPED_OCCURRENCE_CANNOT_BE_STARTED,"Skipped occurrences cannot be started.");
  if(occurrence.getFastingSession()!=null) return sessionDto(occurrence.getFastingSession());
  sessionRepository.findTopByUserAndStatusOrderByStartedAtDesc(user,FastingSessionStatus.ACTIVE).ifPresent(active->{ throw new AdvancedFastingException(AdvancedFastingErrorCode.ACTIVE_FASTING_SESSION_EXISTS,"An active fasting session already exists."); });
  LocalDateTime started=request.getStartedAt()==null?timeZoneSupport.now(user):request.getStartedAt();
  int target=request.getTargetMinutes()==null?occurrence.getPlannedFastingMinutes():request.getTargetMinutes();
  FastingSessionEntity session=new FastingSessionEntity(); session.setUser(user); session.setStatus(FastingSessionStatus.ACTIVE);
  session.setFastingDate(resolved); session.setStartedAt(started); session.setTargetMinutes(target); session.setTargetEndAt(started.plusMinutes(target));
  session.setTargetReached(false); session.setNote(normalize(request.getNote())); session.setPlannedRule(occurrence.getDayRule());
  session.setPlannedProgramVersion(occurrence.getProgramVersion().getVersionNumber()); session.setPlannedRuleType(occurrence.getRuleType());
  session.setPlannedFastingMinutes(occurrence.getPlannedFastingMinutes()); session.setPlannedReducedCalorieTarget(occurrence.getPlannedCalorieTarget());
  session.setPlannedStartAt(occurrence.getPlannedStartAt()); session.setPlannedEndAt(occurrence.getPlannedEndAt());
  session.setPlannedSafetyPolicyVersion(occurrence.getProgramVersion().getSafetyPolicyVersion()); session=sessionRepository.save(session);
  occurrence.setFastingSession(session); occurrence.setStatus(FastingOccurrenceStatus.IN_PROGRESS); occurrence.setAdherenceStatus(FastingAdherenceStatus.PENDING); occurrenceRepository.save(occurrence);
  cacheRevisionService.bump(user.getId(),AnalyticsMutationSource.FASTING); return sessionDto(session);
 }
 private FastingProgramOccurrenceEntity createOccurrence(UserEntity user,LocalDate date){
  FastingProgramEntity program=programRepository.findFirstByUserIdAndStatus(user.getId(),FastingProgramStatus.ACTIVE)
   .filter(p->(p.getEffectiveFrom()==null||!date.isBefore(p.getEffectiveFrom()))&&(p.getEffectiveUntil()==null||!date.isAfter(p.getEffectiveUntil())))
   .orElseThrow(()->new ResourceNotFoundException("No active advanced fasting program applies to this date."));
  FastingProgramVersionEntity version=versionRepository.findByProgramIdAndVersionNumber(program.getId(),program.getCurrentVersionNumber()).orElseThrow(()->new IllegalStateException("Fasting program version is missing."));
  FastingProgramDayRuleEntity rule=ruleRepository.findByProgramVersionIdAndDayOfWeek(version.getId(),date.getDayOfWeek()).orElseThrow(()->new IllegalStateException("Fasting program day rule is missing."));
  FastingProgramOccurrenceEntity occurrence=new FastingProgramOccurrenceEntity(); occurrence.setUser(user); occurrence.setProgram(program); occurrence.setProgramVersion(version); occurrence.setDayRule(rule);
  occurrence.setOccurrenceDate(date); occurrence.setRuleType(rule.getRuleType()); occurrence.setStatus(FastingOccurrenceStatus.PLANNED);
  occurrence.setPlannedFastingMinutes(rule.getFastingMinutes()); occurrence.setPlannedCalorieTarget(rule.getReducedCalorieTarget());
  occurrence.setAdherenceStatus(rule.getRuleType()==FastingDayRuleType.NORMAL||rule.getRuleType()==FastingDayRuleType.REST?FastingAdherenceStatus.NOT_APPLICABLE:FastingAdherenceStatus.PENDING);
  if(rule.getRuleType()==FastingDayRuleType.FAST){ occurrence.setPlannedStartAt(date.atTime(rule.getPreferredStartTime())); occurrence.setPlannedEndAt(occurrence.getPlannedStartAt().plusMinutes(rule.getFastingMinutes())); }
  return occurrenceRepository.save(occurrence);
 }
 private FastingProgramOccurrenceEntity refresh(FastingProgramOccurrenceEntity occurrence,String email,UserEntity user){
  if(occurrence.getStatus()==FastingOccurrenceStatus.SKIPPED) return occurrence;
  if(occurrence.getRuleType()==FastingDayRuleType.REDUCED_CALORIE){
   var stats=foodLogsService.getDailyStats(email,occurrence.getOccurrenceDate().atStartOfDay(),occurrence.getOccurrenceDate().plusDays(1).atStartOfDay());
   Double calories=stats.isEmpty()?null:stats.get(0).getTotalCalories(); occurrence.setActualCalories(calories); occurrence.setEvaluatedAt(timeZoneSupport.now(user));
   if(occurrence.getOccurrenceDate().isBefore(timeZoneSupport.today(user))){ occurrence.setStatus(FastingOccurrenceStatus.COMPLETED); occurrence.setAdherenceStatus(calories==null?FastingAdherenceStatus.UNKNOWN:(calories<=occurrence.getPlannedCalorieTarget()?FastingAdherenceStatus.MET:FastingAdherenceStatus.NOT_MET)); }
  } else if(occurrence.getFastingSession()!=null){
   FastingSessionEntity session=occurrence.getFastingSession();
   if(session.getStatus()==FastingSessionStatus.ACTIVE){ occurrence.setStatus(FastingOccurrenceStatus.IN_PROGRESS); occurrence.setAdherenceStatus(FastingAdherenceStatus.PENDING); }
   else { occurrence.setStatus(FastingOccurrenceStatus.COMPLETED); occurrence.setAdherenceStatus(Boolean.TRUE.equals(session.getTargetReached())?FastingAdherenceStatus.MET:FastingAdherenceStatus.NOT_MET); occurrence.setEvaluatedAt(timeZoneSupport.now(user)); }
  }
  return occurrenceRepository.save(occurrence);
 }
 private FastingOccurrenceDto dto(FastingProgramOccurrenceEntity o){ return new FastingOccurrenceDto(o.getId(),o.getProgram().getId(),o.getProgramVersion().getVersionNumber(),o.getDayRule().getId(),o.getFastingSession()==null?null:o.getFastingSession().getId(),o.getOccurrenceDate(),o.getRuleType(),o.getStatus(),o.getAdherenceStatus(),o.getPlannedStartAt(),o.getPlannedEndAt(),o.getPlannedFastingMinutes(),o.getPlannedCalorieTarget(),o.getActualCalories(),o.getEvaluatedAt(),o.getSkipReason(),o.getReasonNote()); }
 private FastingSessionDto sessionDto(FastingSessionEntity e){ FastingSessionDto d=new FastingSessionDto(); d.setId(e.getId()); d.setStatus(e.getStatus()); d.setFastingDate(e.getFastingDate()); d.setStartedAt(e.getStartedAt()); d.setTargetEndAt(e.getTargetEndAt()); d.setTargetMinutes(e.getTargetMinutes()); d.setTargetReached(Boolean.TRUE.equals(e.getTargetReached())); d.setPlannedRuleId(e.getPlannedRule().getId()); d.setPlannedProgramVersion(e.getPlannedProgramVersion()); d.setPlannedRuleType(e.getPlannedRuleType()); d.setPlannedFastingMinutes(e.getPlannedFastingMinutes()); d.setPlannedReducedCalorieTarget(e.getPlannedReducedCalorieTarget()); d.setPlannedStartAt(e.getPlannedStartAt()); d.setPlannedEndAt(e.getPlannedEndAt()); d.setNote(e.getNote()); return d; }
 private UserEntity user(String email){ return userRepository.findByEmail(email).orElseThrow(()->new InvalidCredentialsException("Invalid credential")); }
 private String normalize(String value){ return value==null||value.isBlank()?null:value.trim(); }
}
