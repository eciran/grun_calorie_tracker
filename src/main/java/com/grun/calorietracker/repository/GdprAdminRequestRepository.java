package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.GdprAdminRequestEntity;
import com.grun.calorietracker.enums.GdprRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GdprAdminRequestRepository extends JpaRepository<GdprAdminRequestEntity, Long> {
    Page<GdprAdminRequestEntity> findByStatus(GdprRequestStatus status, Pageable pageable);
}
