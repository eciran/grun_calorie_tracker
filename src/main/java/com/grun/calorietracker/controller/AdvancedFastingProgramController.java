package com.grun.calorietracker.controller;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.service.AdvancedFastingProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/v1/fasting/advanced/programs") @RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth") @Tag(name="Advanced Fasting Programs")
public class AdvancedFastingProgramController {
 private final AdvancedFastingProgramService service;
 @PostMapping @Operation(summary="Create weekly fasting program") public ResponseEntity<FastingProgramDto> create(@AuthenticationPrincipal UserDetails user,@Valid @RequestBody FastingProgramRequestDto request){return ResponseEntity.ok(service.create(user.getUsername(),request));}
 @GetMapping @Operation(summary="List weekly fasting programs") public ResponseEntity<List<FastingProgramDto>> list(@AuthenticationPrincipal UserDetails user){return ResponseEntity.ok(service.list(user.getUsername()));}
 @GetMapping("/{id}") @Operation(summary="Get weekly fasting program") public ResponseEntity<FastingProgramDto> get(@AuthenticationPrincipal UserDetails user,@PathVariable Long id){return ResponseEntity.ok(service.get(user.getUsername(),id));}
 @PutMapping("/{id}") @Operation(summary="Create a new immutable program version") public ResponseEntity<FastingProgramDto> update(@AuthenticationPrincipal UserDetails user,@PathVariable Long id,@Valid @RequestBody FastingProgramRequestDto request){return ResponseEntity.ok(service.update(user.getUsername(),id,request));}
 @GetMapping("/{id}/preview") @Operation(summary="Preview seven timezone-aware days") public ResponseEntity<FastingProgramPreviewDto> preview(@AuthenticationPrincipal UserDetails user,@PathVariable Long id,@RequestParam(required=false) LocalDate startDate){return ResponseEntity.ok(service.preview(user.getUsername(),id,startDate));}
 @PostMapping("/{id}/activate") @Operation(summary="Activate program idempotently") public ResponseEntity<FastingProgramDto> activate(@AuthenticationPrincipal UserDetails user,@PathVariable Long id){return ResponseEntity.ok(service.activate(user.getUsername(),id));}
 @PostMapping("/{id}/pause") @Operation(summary="Pause program") public ResponseEntity<FastingProgramDto> pause(@AuthenticationPrincipal UserDetails user,@PathVariable Long id){return ResponseEntity.ok(service.pause(user.getUsername(),id));}
 @PostMapping("/{id}/archive") @Operation(summary="Archive program") public ResponseEntity<FastingProgramDto> archive(@AuthenticationPrincipal UserDetails user,@PathVariable Long id){return ResponseEntity.ok(service.archive(user.getUsername(),id));}
}
