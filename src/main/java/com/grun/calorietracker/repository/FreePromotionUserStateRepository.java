package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FreePromotionUserStateRepository extends JpaRepository<FreePromotionUserStateEntity, Long> {
    Optional<FreePromotionUserStateEntity> findByUser(UserEntity user);
}
