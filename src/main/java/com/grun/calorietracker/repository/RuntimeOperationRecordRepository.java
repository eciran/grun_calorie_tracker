package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RuntimeOperationRecordEntity;
import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeOperationRecordRepository extends JpaRepository<RuntimeOperationRecordEntity, Long> {
    Page<RuntimeOperationRecordEntity> findByRecordTypeAndStatus(
            RuntimeOperationRecordType recordType, RuntimeOperationStatus status, Pageable pageable);
    Page<RuntimeOperationRecordEntity> findByRecordType(
            RuntimeOperationRecordType recordType, Pageable pageable);
    Page<RuntimeOperationRecordEntity> findByStatus(
            RuntimeOperationStatus status, Pageable pageable);
}
