package com.grun.calorietracker.controller;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.service.AdvancedFastingExecutionService;
import com.grun.calorietracker.service.AdvancedFastingScheduleExceptionService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/v1/fasting/advanced/occurrences") @RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
public class AdvancedFastingExecutionController {
 private final AdvancedFastingExecutionService service;
 private final AdvancedFastingScheduleExceptionService exceptionService;
 @GetMapping("/{date}") @Operation(summary="Get planned fasting occurrence")
 public ResponseEntity<FastingOccurrenceDto> get(@AuthenticationPrincipal UserDetails user,@PathVariable LocalDate date){ return ResponseEntity.ok(service.getOrCreate(user.getUsername(),date)); }
 @GetMapping("/{date}/nutrition-summary") @Operation(summary="Get canonical reduced-day nutrition summary")
 public ResponseEntity<ReducedDayNutritionSummaryDto> nutritionSummary(@AuthenticationPrincipal UserDetails user,@PathVariable LocalDate date){ return ResponseEntity.ok(service.reducedDayNutritionSummary(user.getUsername(),date)); }
 @PostMapping("/{date}/recalculate") @Operation(summary="Recalculate occurrence adherence")
 public ResponseEntity<FastingOccurrenceDto> recalculate(@AuthenticationPrincipal UserDetails user,@PathVariable LocalDate date){ return ResponseEntity.ok(service.recalculate(user.getUsername(),date)); }
 @PostMapping("/{date}/skip") @Operation(summary="Skip planned fasting occurrence")
 public ResponseEntity<FastingOccurrenceDto> skip(@AuthenticationPrincipal UserDetails user,@PathVariable LocalDate date,@RequestBody @Valid FastingOccurrenceSkipRequestDto request){ return ResponseEntity.ok(service.skip(user.getUsername(),date,request)); }
 @PutMapping("/{date}/exception") @Operation(summary="Create or replace a future schedule exception")
 public ResponseEntity<FastingScheduleExceptionDto> putException(@AuthenticationPrincipal UserDetails user,@PathVariable LocalDate date,@RequestBody @Valid FastingScheduleExceptionRequestDto request){ return ResponseEntity.ok(exceptionService.put(user.getUsername(),date,request)); }
 @DeleteMapping("/{date}/exception") @Operation(summary="Remove a future schedule exception")
 public ResponseEntity<Void> deleteException(@AuthenticationPrincipal UserDetails user,@PathVariable LocalDate date){ exceptionService.delete(user.getUsername(),date); return ResponseEntity.noContent().build(); }
 @PostMapping("/{date}/start") @Operation(summary="Start planned FAST occurrence")
 public ResponseEntity<FastingSessionDto> start(@AuthenticationPrincipal UserDetails user,@PathVariable LocalDate date,@RequestBody @Valid FastingSessionStartRequestDto request){ return ResponseEntity.ok(service.start(user.getUsername(),date,request)); }
}
