package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.OwnerOperationalAlertEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OwnerOperationalAlertRepository extends JpaRepository<OwnerOperationalAlertEntity, Long>, JpaSpecificationExecutor<OwnerOperationalAlertEntity> {
    Optional<OwnerOperationalAlertEntity> findByDedupeKey(String dedupeKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OwnerOperationalAlertEntity a where a.id=:id")
    Optional<OwnerOperationalAlertEntity> findByIdForUpdate(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OwnerOperationalAlertEntity> findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
            Collection<String> statuses, Instant dueAt, Pageable pageable);

    @Query("select coalesce(sum(a.occurrenceCount),0) from OwnerOperationalAlertEntity a where a.category=:category and a.lastOccurredAt>=:from and a.lastOccurredAt<:to")
    long sumOccurrencesByCategoryBetween(@Param("category") String category, @Param("from") Instant from, @Param("to") Instant to);
    long countByCategoryAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan(String category, Instant from, Instant to);
    long countByStatusAndLastOccurredAtGreaterThanEqualAndLastOccurredAtLessThan(String status, Instant from, Instant to);
}
