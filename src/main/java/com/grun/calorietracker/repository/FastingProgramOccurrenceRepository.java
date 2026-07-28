package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FastingProgramOccurrenceRepository extends JpaRepository<FastingProgramOccurrenceEntity, Long> {
    Optional<FastingProgramOccurrenceEntity> findByUserAndOccurrenceDate(UserEntity user, LocalDate date);
    Optional<FastingProgramOccurrenceEntity> findByIdAndUser(Long id, UserEntity user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select occurrence
            from FastingProgramOccurrenceEntity occurrence
            left join fetch occurrence.fastingSession
            where occurrence.id = :id and occurrence.user = :user
            """)
    Optional<FastingProgramOccurrenceEntity> findByIdAndUserForUpdate(
            @Param("id") Long id,
            @Param("user") UserEntity user);

    @EntityGraph(attributePaths = {"fastingSession", "dayRule"})
    List<FastingProgramOccurrenceEntity> findByUserAndOccurrenceDateBetweenOrderByOccurrenceDateAsc(
            UserEntity user, LocalDate startDate, LocalDate endDate);

    long deleteByUser(UserEntity user);
}