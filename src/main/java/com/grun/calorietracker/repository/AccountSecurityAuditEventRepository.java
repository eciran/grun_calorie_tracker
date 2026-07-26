package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AccountSecurityAuditEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountSecurityAuditEventRepository extends JpaRepository<AccountSecurityAuditEventEntity, Long> {
    List<AccountSecurityAuditEventEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
