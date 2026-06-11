package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealTemplateEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MealTemplateRepository extends JpaRepository<MealTemplateEntity, Long> {
    long countByUser(UserEntity user);
    List<MealTemplateEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    List<MealTemplateEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);
    Optional<MealTemplateEntity> findByIdAndUser(Long id, UserEntity user);
    long deleteByUser(UserEntity user);
}
