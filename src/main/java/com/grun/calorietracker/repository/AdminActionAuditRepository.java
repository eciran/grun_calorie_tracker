package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdminActionAuditEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionAuditRepository extends JpaRepository<AdminActionAuditEntity, Long> {
    Page<AdminActionAuditEntity> findByActionType(AdminAuditActionType actionType, Pageable pageable);
    Page<AdminActionAuditEntity> findByTargetType(AdminAuditTargetType targetType, Pageable pageable);
    Page<AdminActionAuditEntity> findByActionTypeAndTargetType(AdminAuditActionType actionType,
                                                               AdminAuditTargetType targetType,
                                                               Pageable pageable);
}
