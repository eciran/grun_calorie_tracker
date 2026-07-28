package com.grun.calorietracker.service;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdvancedFastingProgramServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdvancedFastingProgramServiceImplTest {
 private UserRepository users=mock(UserRepository.class); private FastingProgramRepository programs=mock(FastingProgramRepository.class);
 private FastingProgramVersionRepository versions=mock(FastingProgramVersionRepository.class); private FastingProgramDayRuleRepository rules=mock(FastingProgramDayRuleRepository.class);
 private AdvancedFastingProgramServiceImpl service=new AdvancedFastingProgramServiceImpl(users,programs,versions,rules,new UserTimeZoneSupport());
 private UserEntity user;
 @BeforeEach void setup(){ reset(users,programs,versions,rules); user=new UserEntity(); user.setId(7L); user.setEmail("u@g.app"); user.setTimeZone("Europe/Dublin"); when(users.findByEmail("u@g.app")).thenReturn(Optional.of(user)); }
 @Test void createRejectsConsecutiveFiveTwoDays(){ var r=request(); reduced(r,DayOfWeek.MONDAY,DayOfWeek.TUESDAY); assertThrows(IllegalArgumentException.class,()->service.create("u@g.app",r)); verifyNoInteractions(programs); }
 @Test void createRejectsDuplicateWeekday(){ var r=request(); r.getRules().get(6).setDayOfWeek(DayOfWeek.MONDAY); assertThrows(IllegalArgumentException.class,()->service.create("u@g.app",r)); }
 @Test void getIsOwnerScoped(){ when(programs.findByIdAndUserId(9L,7L)).thenReturn(Optional.empty()); assertThrows(ResourceNotFoundException.class,()->service.get("u@g.app",9L)); }
 @Test void activateIsIdempotent(){ var p=program(3L,FastingProgramStatus.ACTIVE); when(programs.findAllByUserIdForUpdate(7L)).thenReturn(List.of(p)); stubVersion(p); var result=service.activate("u@g.app",3L); assertEquals(FastingProgramStatus.ACTIVE,result.status()); verify(programs,never()).saveAll(any()); }
 @Test void previewUsesUserTimezoneAcrossDstBoundary(){ var p=program(3L,FastingProgramStatus.DRAFT); when(programs.findByIdAndUserId(3L,7L)).thenReturn(Optional.of(p)); var v=stubVersion(p); var day=new FastingProgramDayRuleEntity(); day.setId(1L); day.setProgramVersion(v); day.setDayOfWeek(DayOfWeek.SUNDAY); day.setRuleType(FastingDayRuleType.FAST); day.setFastingMinutes(120); day.setPreferredStartTime(LocalTime.of(1,30)); List<FastingProgramDayRuleEntity> all=new ArrayList<>(); for(DayOfWeek d:DayOfWeek.values()){ if(d==DayOfWeek.SUNDAY) all.add(day); else {var x=new FastingProgramDayRuleEntity(); x.setDayOfWeek(d); x.setRuleType(FastingDayRuleType.NORMAL); all.add(x);} } when(rules.findAllByProgramVersionIdOrderByDayOfWeek(11L)).thenReturn(all); var result=service.preview("u@g.app",3L,LocalDate.of(2026,3,23)); assertEquals("Europe/Dublin",result.timeZone()); assertEquals(7,result.days().size()); assertNotNull(result.days().get(6).startAt()); }
 private FastingProgramVersionEntity stubVersion(FastingProgramEntity p){ var v=new FastingProgramVersionEntity(); v.setId(11L); v.setProgram(p); v.setVersionNumber(1); v.setSafetyPolicyVersion("FASTING_SAFETY_V1"); when(versions.findByProgramIdAndVersionNumber(p.getId(),1)).thenReturn(Optional.of(v)); when(rules.findAllByProgramVersionIdOrderByDayOfWeek(11L)).thenReturn(List.of()); return v; }
 private FastingProgramEntity program(Long id,FastingProgramStatus status){ var p=new FastingProgramEntity(); p.setId(id); p.setUser(user); p.setName("Plan"); p.setStatus(status); p.setCurrentVersionNumber(1); p.setVersion(0L); return p; }
 private FastingProgramRequestDto request(){ var r=new FastingProgramRequestDto(); r.setName("Plan"); List<FastingDayRuleRequestDto> list=new ArrayList<>(); for(DayOfWeek d:DayOfWeek.values()){ var x=new FastingDayRuleRequestDto(); x.setDayOfWeek(d); x.setRuleType(FastingDayRuleType.NORMAL); list.add(x);} r.setRules(list); return r; }
 private void reduced(FastingProgramRequestDto r,DayOfWeek a,DayOfWeek b){ for(var x:r.getRules()) if(x.getDayOfWeek()==a||x.getDayOfWeek()==b){x.setRuleType(FastingDayRuleType.REDUCED_CALORIE); x.setReducedCalorieTarget(600);} }
}
