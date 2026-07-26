package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AccountSecurityAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSecurityAuditEventRepository extends JpaRepository<AccountSecurityAuditEventEntity, Long> {
}
