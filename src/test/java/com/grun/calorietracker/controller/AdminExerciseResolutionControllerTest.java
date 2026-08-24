package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ResolveUnmatchedAiExerciseRequestDto;
import com.grun.calorietracker.entity.ExerciseItemAliasEntity;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.UnmatchedAiExerciseEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import com.grun.calorietracker.repository.ExerciseItemAliasRepository;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.UnmatchedAiExerciseRepository;
import com.grun.calorietracker.service.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminExerciseResolutionControllerTest {
    private final UnmatchedAiExerciseRepository unmatched = mock(UnmatchedAiExerciseRepository.class);
    private final ExerciseItemRepository items = mock(ExerciseItemRepository.class);
    private final ExerciseItemAliasRepository aliases = mock(ExerciseItemAliasRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminExerciseResolutionController controller =
            new AdminExerciseResolutionController(unmatched, items, aliases, audit);

    @Test
    void resolvesOpenItemCreatesAliasAndWritesAudit() {
        UnmatchedAiExerciseEntity row = row("OPEN");
        ExerciseItemEntity item = eligibleItem();
        UserDetails admin = mock(UserDetails.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(admin.getUsername()).thenReturn("admin@grun.test");
        when(request.getAttribute("correlationId")).thenReturn("cid-1");
        when(unmatched.findById(4L)).thenReturn(Optional.of(row));
        when(items.findById(9L)).thenReturn(Optional.of(item));
        when(aliases.findFirstByNormalizedAliasAndActiveTrue("sinav")).thenReturn(Optional.empty());
        when(unmatched.save(row)).thenReturn(row);

        var result = controller.resolve(4L, new ResolveUnmatchedAiExerciseRequestDto(9L, true), admin, request);

        assertEquals("RESOLVED", result.status());
        verify(aliases).save(any(ExerciseItemAliasEntity.class));
        verify(audit).record(eq("admin@grun.test"), eq(AdminAuditActionType.AI_EXERCISE_RESOLVE),
                eq(AdminAuditTargetType.AI_EXERCISE_RESOLUTION), eq("4"), any(), any(), eq("cid-1"));
    }

    @Test
    void rejectsReviewingSameQueueItemTwice() {
        when(unmatched.findById(4L)).thenReturn(Optional.of(row("RESOLVED")));
        assertThrows(IllegalStateException.class,
                () -> controller.dismiss(4L, mock(UserDetails.class), mock(HttpServletRequest.class)));
        verify(unmatched, never()).save(any());
    }

    @Test
    void rejectsAliasCollisionWithDifferentCatalogItem() {
        UnmatchedAiExerciseEntity row = row("OPEN");
        ExerciseItemEntity requested = eligibleItem();
        ExerciseItemEntity other = eligibleItem(); other.setId(10L);
        ExerciseItemAliasEntity existing = new ExerciseItemAliasEntity(); existing.setExerciseItem(other);
        when(unmatched.findById(4L)).thenReturn(Optional.of(row));
        when(items.findById(9L)).thenReturn(Optional.of(requested));
        when(aliases.findFirstByNormalizedAliasAndActiveTrue("sinav")).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> controller.resolve(4L, new ResolveUnmatchedAiExerciseRequestDto(9L, true),
                        mock(UserDetails.class), mock(HttpServletRequest.class)));
        verify(unmatched, never()).save(any());
    }

    @Test
    void calculatesResolutionSummary() {
        when(unmatched.countByStatus("OPEN")).thenReturn(2L);
        when(unmatched.countByStatus("RESOLVED")).thenReturn(6L);
        when(unmatched.countByStatus("DISMISSED")).thenReturn(2L);
        when(unmatched.sumOccurrencesByStatus("OPEN")).thenReturn(11L);

        var result = controller.summary();

        assertEquals(8L, result.reviewedNames());
        assertEquals(60.0, result.resolutionRatePercent());
    }

    private UnmatchedAiExerciseEntity row(String status) {
        UnmatchedAiExerciseEntity row = new UnmatchedAiExerciseEntity();
        row.setId(4L); row.setDisplayName("Şınav"); row.setNormalizedName("sinav");
        row.setStatus(status); row.setOccurrenceCount(3L);
        return row;
    }

    private ExerciseItemEntity eligibleItem() {
        ExerciseItemEntity item = new ExerciseItemEntity(); item.setId(9L); item.setName("Push-Up");
        item.setActive(true); item.setAiEligible(true);
        item.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.APPROVED);
        return item;
    }
}
