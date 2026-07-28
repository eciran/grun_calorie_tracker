package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FastingDiaryContextDto;
import com.grun.calorietracker.entity.FastingProgramOccurrenceEntity;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FastingDayRuleType;
import com.grun.calorietracker.enums.FastingOccurrenceStatus;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.repository.FastingProgramOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FastingDiaryContextResolver {

    private final FastingProgramOccurrenceRepository occurrenceRepository;

    public FastingDiaryContextDto resolve(UserEntity user, LocalDateTime loggedAt) {
        if (user == null || loggedAt == null) {
            return FastingDiaryContextDto.outsideWindow();
        }
        return resolve(loggedAt, loadOccurrences(user, List.of(loggedAt)));
    }

    public Map<LocalDateTime, FastingDiaryContextDto> resolveAll(UserEntity user, Collection<LocalDateTime> timestamps) {
        List<LocalDateTime> validTimestamps = timestamps.stream().filter(Objects::nonNull).distinct().toList();
        if (user == null || validTimestamps.isEmpty()) {
            return Map.of();
        }
        List<FastingProgramOccurrenceEntity> occurrences = loadOccurrences(user, validTimestamps);
        return validTimestamps.stream().collect(Collectors.toMap(
                Function.identity(),
                timestamp -> resolve(timestamp, occurrences)
        ));
    }

    private List<FastingProgramOccurrenceEntity> loadOccurrences(UserEntity user, Collection<LocalDateTime> timestamps) {
        LocalDateTime earliest = timestamps.stream().min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime latest = timestamps.stream().max(LocalDateTime::compareTo).orElseThrow();
        return occurrenceRepository.findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
                user, earliest.toLocalDate().minusDays(1), latest.toLocalDate().plusDays(1));
    }

    private FastingDiaryContextDto resolve(LocalDateTime loggedAt, List<FastingProgramOccurrenceEntity> occurrences) {
        for (FastingProgramOccurrenceEntity occurrence : occurrences) {
            boolean planned = isInsidePlannedWindow(loggedAt, occurrence);
            boolean actual = isInsideActualWindow(loggedAt, occurrence.getFastingSession());
            if (planned || actual) {
                FastingSessionEntity session = occurrence.getFastingSession();
                return new FastingDiaryContextDto(planned, actual, occurrence.getId(), session == null ? null : session.getId());
            }
        }
        return FastingDiaryContextDto.outsideWindow();
    }

    private boolean isInsidePlannedWindow(LocalDateTime loggedAt, FastingProgramOccurrenceEntity occurrence) {
        return occurrence.getRuleType() == FastingDayRuleType.FAST
                && occurrence.getStatus() != FastingOccurrenceStatus.SKIPPED
                && contains(loggedAt, occurrence.getPlannedStartAt(), occurrence.getPlannedEndAt());
    }

    private boolean isInsideActualWindow(LocalDateTime loggedAt, FastingSessionEntity session) {
        return session != null
                && (session.getStatus() == FastingSessionStatus.ACTIVE
                || session.getStatus() == FastingSessionStatus.COMPLETED)
                && contains(loggedAt, session.getStartedAt(), session.getEndedAt());
    }

    private boolean contains(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return start != null && !value.isBefore(start) && (end == null || value.isBefore(end));
    }
}