package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminUnmatchedAiExerciseDto;
import com.grun.calorietracker.dto.AdminExerciseResolutionSummaryDto;
import com.grun.calorietracker.dto.ResolveUnmatchedAiExerciseRequestDto;
import com.grun.calorietracker.entity.ExerciseItemAliasEntity;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.UnmatchedAiExerciseEntity;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.repository.ExerciseItemAliasRepository;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.UnmatchedAiExerciseRepository;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.grun.calorietracker.service.AdminAuditService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/exercise-resolution")
@RequiredArgsConstructor
public class AdminExerciseResolutionController {
    private final UnmatchedAiExerciseRepository unmatchedRepository;
    private final ExerciseItemRepository itemRepository;
    private final ExerciseItemAliasRepository aliasRepository;
    private final AdminAuditService auditService;

    @GetMapping("/unmatched")
    @Transactional(readOnly = true)
    public Page<AdminUnmatchedAiExerciseDto> unmatched(@RequestParam(defaultValue = "OPEN") String status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "25") int size) {
        return unmatchedRepository.findByStatusOrderByOccurrenceCountDescLastSeenAtDesc(
                status.toUpperCase(), PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(this::toDto);
    }

    @PostMapping("/unmatched/{id}/resolve")
    @Transactional
    public AdminUnmatchedAiExerciseDto resolve(@PathVariable Long id,
                                                @RequestBody @Valid ResolveUnmatchedAiExerciseRequestDto request,
                                                @AuthenticationPrincipal UserDetails admin,
                                                HttpServletRequest servletRequest) {
        UnmatchedAiExerciseEntity row = unmatchedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unmatched AI exercise not found"));
        requireOpen(row);
        ExerciseItemEntity item = itemRepository.findById(request.exerciseItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Exercise item not found"));
        if (!Boolean.TRUE.equals(item.getActive()) || !Boolean.TRUE.equals(item.getAiEligible())
                || item.getTechniqueReviewStatus() != com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus.APPROVED) {
            throw new IllegalArgumentException("Only active, technique-approved, AI-eligible exercises can resolve AI names.");
        }
        row.setResolvedExerciseItem(item); row.setStatus("RESOLVED");
        if (Boolean.TRUE.equals(request.createAlias())) {
            var existingAlias = aliasRepository.findFirstByNormalizedAliasAndActiveTrue(row.getNormalizedName());
            if (existingAlias.isPresent() && !existingAlias.get().getExerciseItem().getId().equals(item.getId())) {
                throw new IllegalArgumentException("This exercise name is already mapped to another catalog item.");
            }
            if (existingAlias.isEmpty()) {
                ExerciseItemAliasEntity alias = new ExerciseItemAliasEntity();
                alias.setExerciseItem(item); alias.setAlias(row.getDisplayName()); alias.setNormalizedAlias(row.getNormalizedName());
                alias.setLanguage(row.getLanguage() == null ? "und" : row.getLanguage()); alias.setAliasType("AI_REVIEW");
                aliasRepository.save(alias);
            }
        }
        AdminUnmatchedAiExerciseDto result = toDto(unmatchedRepository.save(row));
        audit(admin, servletRequest, AdminAuditActionType.AI_EXERCISE_RESOLVE, row,
                Map.of("status", "RESOLVED", "exerciseItemId", item.getId(),
                        "aliasCreated", Boolean.TRUE.equals(request.createAlias())));
        return result;
    }

    @PostMapping("/unmatched/{id}/dismiss")
    @Transactional
    public AdminUnmatchedAiExerciseDto dismiss(@PathVariable Long id,
                                                @AuthenticationPrincipal UserDetails admin,
                                                HttpServletRequest servletRequest) {
        UnmatchedAiExerciseEntity row = unmatchedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unmatched AI exercise not found"));
        requireOpen(row);
        row.setStatus("DISMISSED"); row.setResolvedExerciseItem(null);
        AdminUnmatchedAiExerciseDto result = toDto(unmatchedRepository.save(row));
        audit(admin, servletRequest, AdminAuditActionType.AI_EXERCISE_DISMISS, row,
                Map.of("status", "DISMISSED"));
        return result;
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public AdminExerciseResolutionSummaryDto summary() {
        long open = unmatchedRepository.countByStatus("OPEN");
        long resolved = unmatchedRepository.countByStatus("RESOLVED");
        long dismissed = unmatchedRepository.countByStatus("DISMISSED");
        long reviewed = resolved + dismissed;
        long total = open + reviewed;
        double resolutionRate = total == 0 ? 100.0 : Math.round((resolved * 10000.0) / total) / 100.0;
        return new AdminExerciseResolutionSummaryDto(open, unmatchedRepository.sumOccurrencesByStatus("OPEN"),
                resolved, dismissed, reviewed, resolutionRate);
    }

    private void requireOpen(UnmatchedAiExerciseEntity row) {
        if (!"OPEN".equals(row.getStatus())) {
            throw new IllegalStateException("Only OPEN unmatched exercise records can be reviewed.");
        }
    }

    private void audit(UserDetails admin, HttpServletRequest request, AdminAuditActionType action,
                       UnmatchedAiExerciseEntity row, Object newValue) {
        auditService.record(admin == null ? "unknown-admin" : admin.getUsername(), action,
                AdminAuditTargetType.AI_EXERCISE_RESOLUTION, String.valueOf(row.getId()),
                Map.of("status", "OPEN", "name", row.getDisplayName()), newValue,
                request == null ? null : (String) request.getAttribute("correlationId"));
    }

    private AdminUnmatchedAiExerciseDto toDto(UnmatchedAiExerciseEntity row) {
        return new AdminUnmatchedAiExerciseDto(row.getId(), row.getDisplayName(), row.getNormalizedName(),
                row.getLanguage(), row.getEquipment(), row.getTargetMuscleGroup(), row.getOccurrenceCount(),
                row.getFirstSeenAt(), row.getLastSeenAt(), row.getStatus(),
                row.getResolvedExerciseItem() == null ? null : row.getResolvedExerciseItem().getId());
    }
}
