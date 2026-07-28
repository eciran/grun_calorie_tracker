package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.AdvancedFastingProgramService;
import com.grun.calorietracker.service.support.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class AdvancedFastingProgramServiceImpl implements AdvancedFastingProgramService {
 private final UserRepository userRepository;
 private final FastingProgramRepository programRepository;
 private final FastingProgramVersionRepository versionRepository;
 private final FastingProgramDayRuleRepository ruleRepository;
 private final UserTimeZoneSupport timeZoneSupport;

 @Override @Transactional public FastingProgramDto create(String email,FastingProgramRequestDto request){
  UserEntity user=user(email); validate(request);
  FastingProgramEntity p=new FastingProgramEntity(); p.setUser(user); applyMetadata(p,request); p.setStatus(FastingProgramStatus.DRAFT); p.setCurrentVersionNumber(1);
  p=programRepository.save(p); createVersion(p,1,request.getRules()); return dto(p);
 }
 @Override @Transactional(readOnly=true) public List<FastingProgramDto> list(String email){ UserEntity u=user(email); return programRepository.findAllByUserIdOrderByCreatedAtDesc(u.getId()).stream().map(this::dto).toList(); }
 @Override @Transactional(readOnly=true) public FastingProgramDto get(String email,Long id){ return dto(owned(user(email),id)); }
 @Override @Transactional public FastingProgramDto update(String email,Long id,FastingProgramRequestDto request){
  UserEntity u=user(email); validate(request); FastingProgramEntity p=owned(u,id);
  if(p.getStatus()==FastingProgramStatus.ARCHIVED) throw new IllegalArgumentException("Archived fasting programs cannot be updated.");
  applyMetadata(p,request); int next=p.getCurrentVersionNumber()+1; createVersion(p,next,request.getRules()); p.setCurrentVersionNumber(next); return dto(programRepository.save(p));
 }
 @Override @Transactional(readOnly=true) public FastingProgramPreviewDto preview(String email,Long id,LocalDate startDate){
  UserEntity u=user(email); FastingProgramEntity p=owned(u,id); LocalDate start=startDate==null?timeZoneSupport.today(u):startDate;
  var version=currentVersion(p); Map<DayOfWeek,FastingProgramDayRuleEntity> rules=ruleRepository.findAllByProgramVersionIdOrderByDayOfWeek(version.getId()).stream().collect(Collectors.toMap(FastingProgramDayRuleEntity::getDayOfWeek,Function.identity()));
  ZoneId zone=timeZoneSupport.zoneId(u); List<FastingProgramPreviewDto.PreviewDay> days=new ArrayList<>();
  for(int i=0;i<7;i++){ LocalDate date=start.plusDays(i); var r=rules.get(date.getDayOfWeek()); ZonedDateTime at=null,end=null;
   if(r.getRuleType()==FastingDayRuleType.FAST){ at=date.atTime(r.getPreferredStartTime()).atZone(zone); end=at.plusMinutes(r.getFastingMinutes()); }
   days.add(new FastingProgramPreviewDto.PreviewDay(date,r.getRuleType(),at,end,r.getReducedCalorieTarget())); }
  return new FastingProgramPreviewDto(p.getId(),version.getVersionNumber(),zone.getId(),start,List.copyOf(days));
 }
 @Override @Transactional public FastingProgramDto activate(String email,Long id){
  UserEntity u=user(email); List<FastingProgramEntity> all=programRepository.findAllByUserIdForUpdate(u.getId()); FastingProgramEntity target=all.stream().filter(p->p.getId().equals(id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Fasting program not found."));
  if(target.getStatus()==FastingProgramStatus.ACTIVE) return dto(target);
  if(target.getStatus()==FastingProgramStatus.ARCHIVED) throw new IllegalArgumentException("Archived fasting programs cannot be activated.");
  List<FastingProgramEntity> previous=all.stream().filter(p->p.getStatus()==FastingProgramStatus.ACTIVE).toList(); previous.forEach(p->p.setStatus(FastingProgramStatus.PAUSED)); programRepository.saveAll(previous); programRepository.flush(); target.setStatus(FastingProgramStatus.ACTIVE); if(target.getEffectiveFrom()==null) target.setEffectiveFrom(timeZoneSupport.today(u)); programRepository.save(target); return dto(target);
 }
 @Override @Transactional public FastingProgramDto pause(String email,Long id){ FastingProgramEntity p=owned(user(email),id); if(p.getStatus()==FastingProgramStatus.ARCHIVED) throw new IllegalArgumentException("Archived fasting programs cannot be paused."); p.setStatus(FastingProgramStatus.PAUSED); return dto(programRepository.save(p)); }
 @Override @Transactional public FastingProgramDto archive(String email,Long id){ FastingProgramEntity p=owned(user(email),id); p.setStatus(FastingProgramStatus.ARCHIVED); return dto(programRepository.save(p)); }

 private void applyMetadata(FastingProgramEntity p,FastingProgramRequestDto r){ if(r.getEffectiveFrom()!=null&&r.getEffectiveUntil()!=null&&r.getEffectiveUntil().isBefore(r.getEffectiveFrom())) throw new IllegalArgumentException("effectiveUntil cannot be before effectiveFrom."); p.setName(r.getName().trim()); p.setEffectiveFrom(r.getEffectiveFrom()); p.setEffectiveUntil(r.getEffectiveUntil()); }
 private void validate(FastingProgramRequestDto request){
  if(request.getRules()==null||request.getRules().size()!=7) throw new IllegalArgumentException("Exactly seven weekday rules are required.");
  Set<DayOfWeek> days=request.getRules().stream().map(FastingDayRuleRequestDto::getDayOfWeek).collect(Collectors.toSet()); if(days.size()!=7) throw new IllegalArgumentException("Each weekday must be configured exactly once.");
  List<DayOfWeek> reduced=new ArrayList<>();
  for(var r:request.getRules()){
   if(r.getRuleType()==FastingDayRuleType.FAST){ if(r.getFastingMinutes()==null||r.getPreferredStartTime()==null||r.getReducedCalorieTarget()!=null) throw new IllegalArgumentException("FAST rules require time and duration only."); if(r.getFastingMinutes()>FastingSafetyPolicy.MAX_CONTINUOUS_FASTING_HOURS*60) throw new FastingSafetyException(FastingSafetyErrorCode.FASTING_UNSAFE_DURATION,"Continuous fasting cannot exceed 24 hours."); }
   else if(r.getRuleType()==FastingDayRuleType.REDUCED_CALORIE){ if(r.getReducedCalorieTarget()==null||r.getFastingMinutes()!=null||r.getPreferredStartTime()!=null) throw new IllegalArgumentException("REDUCED_CALORIE rules require a calorie target only."); reduced.add(r.getDayOfWeek()); }
   else if(r.getFastingMinutes()!=null||r.getPreferredStartTime()!=null||r.getReducedCalorieTarget()!=null) throw new IllegalArgumentException("NORMAL and REST rules cannot contain fasting targets.");
  }
  if(!reduced.isEmpty()){ if(reduced.size()!=2) throw new IllegalArgumentException("A 5:2 program requires exactly two reduced-calorie days."); int a=reduced.get(0).getValue(),b=reduced.get(1).getValue(); if(Math.abs(a-b)==1||Math.abs(a-b)==6) throw new IllegalArgumentException("Reduced-calorie days must not be consecutive."); }
 }
 private void createVersion(FastingProgramEntity p,int number,List<FastingDayRuleRequestDto> requests){ FastingProgramVersionEntity v=new FastingProgramVersionEntity(); v.setProgram(p); v.setVersionNumber(number); v.setSafetyPolicyVersion(FastingSafetyPolicy.VERSION); v=versionRepository.save(v); for(var r:requests){ FastingProgramDayRuleEntity e=new FastingProgramDayRuleEntity(); e.setProgramVersion(v); e.setDayOfWeek(r.getDayOfWeek()); e.setRuleType(r.getRuleType()); e.setFastingMinutes(r.getFastingMinutes()); e.setPreferredStartTime(r.getPreferredStartTime()); e.setReducedCalorieTarget(r.getReducedCalorieTarget()); ruleRepository.save(e); } }
 private FastingProgramDto dto(FastingProgramEntity p){ var v=currentVersion(p); var rules=ruleRepository.findAllByProgramVersionIdOrderByDayOfWeek(v.getId()).stream().map(r->new FastingProgramDto.FastingDayRuleDto(r.getId(),r.getDayOfWeek(),r.getRuleType(),r.getFastingMinutes(),r.getPreferredStartTime(),r.getReducedCalorieTarget())).toList(); return new FastingProgramDto(p.getId(),p.getName(),p.getStatus(),p.getEffectiveFrom(),p.getEffectiveUntil(),v.getVersionNumber(),p.getVersion(),v.getSafetyPolicyVersion(),rules); }
 private FastingProgramVersionEntity currentVersion(FastingProgramEntity p){ return versionRepository.findByProgramIdAndVersionNumber(p.getId(),p.getCurrentVersionNumber()).orElseThrow(()->new IllegalStateException("Fasting program version is missing.")); }
 private FastingProgramEntity owned(UserEntity u,Long id){ return programRepository.findByIdAndUserId(id,u.getId()).orElseThrow(()->new ResourceNotFoundException("Fasting program not found.")); }
 private UserEntity user(String email){ return userRepository.findByEmail(email).orElseThrow(()->new InvalidCredentialsException("Invalid credential")); }
}
