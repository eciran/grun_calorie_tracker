package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SubscriptionVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface SubscriptionVerificationRepository extends JpaRepository<SubscriptionVerificationEntity, Long> {
    long countByStatus(String status);

    @Query("select v.userId from SubscriptionVerificationEntity v where v.nextAttemptAt <= :now "
            + "and (v.leaseUntil is null or v.leaseUntil <= :now) order by v.nextAttemptAt")
    List<Long> findDue(@Param("now") Instant now, Pageable pageable);
}
