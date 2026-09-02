package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationOccurrenceEntity;
import com.grun.calorietracker.enums.NotificationClassification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface NotificationOccurrenceRepository extends JpaRepository<NotificationOccurrenceEntity, Long> {
    Optional<NotificationOccurrenceEntity> findBySourceAndSourceEventIdAndDefinitionKeyAndUserId(
            String source, String sourceEventId, String definitionKey, Long userId);

    Page<NotificationOccurrenceEntity> findByClassification(NotificationClassification classification, Pageable pageable);

    long countByClassification(NotificationClassification classification);

    @Query("select occurrence.status, count(occurrence) from NotificationOccurrenceEntity occurrence "
            + "where occurrence.classification = :classification group by occurrence.status")
    java.util.List<Object[]> countStatuses(NotificationClassification classification);
}
