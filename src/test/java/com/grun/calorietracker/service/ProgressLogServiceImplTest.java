package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.ProgressLogNotFoundException;
import com.grun.calorietracker.mapper.ProgressLogMapper;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.service.impl.ProgressLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressLogServiceImplTest {

    @Mock
    private ProgressLogRepository progressLogRepository;

    @Mock
    private UserService userService;

    private ProgressLogServiceImpl progressLogService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        progressLogService = new ProgressLogServiceImpl(progressLogRepository, userService, new ProgressLogMapper());
        user = new UserEntity();
        user.setId(9L);
        user.setEmail("progress@grun.app");
        when(userService.findByEmail("progress@grun.app")).thenReturn(Optional.of(user));
    }

    @Test
    void updateLog_UpdatesOwnedProgressValuesWithoutChangingTimestamp() {
        ProgressLogEntity entity = ownedEntity();
        ProgressLogDto request = new ProgressLogDto();
        request.setWeight(79.5);
        request.setCalorieIntake(2050);
        request.setProteinIntake(150.0);
        request.setFatIntake(62.0);
        request.setCarbIntake(220.0);
        request.setNote("Morning measurement");

        when(progressLogRepository.findByIdAndUser(4L, user)).thenReturn(Optional.of(entity));
        when(progressLogRepository.save(entity)).thenReturn(entity);

        ProgressLogDto result = progressLogService.updateLog(4L, request, "progress@grun.app");

        assertEquals(79.5, result.getWeight());
        assertEquals(LocalDate.of(2026, 5, 20).atTime(7, 30), result.getLogDate());
        assertEquals("Morning measurement", entity.getNote());
    }

    @Test
    void getUserLogs_WithRange_ReturnsMappedOwnedHistory() {
        ProgressLogEntity entity = ownedEntity();
        when(progressLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                user,
                LocalDate.of(2026, 5, 1).atStartOfDay(),
                LocalDate.of(2026, 6, 1).atStartOfDay()
        )).thenReturn(List.of(entity));

        List<ProgressLogDto> result = progressLogService.getUserLogs(
                "progress@grun.app",
                LocalDate.of(2026, 5, 1).atStartOfDay(),
                LocalDate.of(2026, 6, 1).atStartOfDay()
        );

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).getId());
    }

    @Test
    void deleteLog_WhenLogIsNotOwned_ThrowsNotFound() {
        when(progressLogRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThrows(ProgressLogNotFoundException.class,
                () -> progressLogService.deleteLog(99L, "progress@grun.app"));
    }

    @Test
    void deleteLog_DeletesOwnedEntity() {
        ProgressLogEntity entity = ownedEntity();
        when(progressLogRepository.findByIdAndUser(4L, user)).thenReturn(Optional.of(entity));

        progressLogService.deleteLog(4L, "progress@grun.app");

        verify(progressLogRepository).delete(entity);
    }

    private ProgressLogEntity ownedEntity() {
        ProgressLogEntity entity = new ProgressLogEntity();
        entity.setId(4L);
        entity.setUser(user);
        entity.setLogDate(LocalDate.of(2026, 5, 20).atTime(7, 30));
        entity.setWeight(80.0);
        return entity;
    }
}
