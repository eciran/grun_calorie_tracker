package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.NotificationDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationDefinitionRepository extends JpaRepository<NotificationDefinitionEntity, Long> {
    Optional<NotificationDefinitionEntity> findByKey(String key);
    boolean existsByKey(String key);
    List<NotificationDefinitionEntity> findByKeyIn(Collection<String> keys);
    List<NotificationDefinitionEntity> findAllByOrderByDisplayNameAsc();
}
