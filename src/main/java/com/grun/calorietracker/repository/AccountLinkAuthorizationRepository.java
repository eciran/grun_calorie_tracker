package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AccountLinkAuthorizationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AccountLinkAuthorizationRepository extends JpaRepository<AccountLinkAuthorizationEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select authorization from AccountLinkAuthorizationEntity authorization "
            + "where authorization.tokenHash = :tokenHash")
    Optional<AccountLinkAuthorizationEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
